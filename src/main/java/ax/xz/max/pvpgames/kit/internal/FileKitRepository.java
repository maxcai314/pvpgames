package ax.xz.max.pvpgames.kit.internal;

import ax.xz.max.pvpgames.kit.InventorySnapshot;
import ax.xz.max.pvpgames.kit.Kit;
import ax.xz.max.pvpgames.kit.KitName;
import ax.xz.max.pvpgames.kit.KitPersistenceException;
import ax.xz.max.pvpgames.kit.KitRepository;
import ax.xz.max.pvpgames.persistence.ItemStackCodec;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * {@link KitRepository} that stores one YAML file per kit under a configured
 * directory.
 *
 * <p>YAML metadata is plaintext for ease of inspection; item slots are stored
 * as base64-encoded NBT bytes via {@link ItemStackCodec} so kits survive
 * Minecraft version updates.
 *
 * <p>All kits are eager-loaded into a {@link ConcurrentHashMap} when
 * {@link #loadAll()} is called; subsequent reads hit the cache. Writes update
 * the cache and flush to disk atomically (write-temp + {@code ATOMIC_MOVE}).
 */
public final class FileKitRepository implements KitRepository {

    private static final String FILE_EXTENSION = ".yml";
    private static final String TEMP_EXTENSION = ".yml.tmp";

    private static final String KEY_NAME       = "name";
    private static final String KEY_CREATED_AT = "created-at";
    private static final String KEY_CREATED_BY = "created-by";
    private static final String KEY_STORAGE    = "storage";
    private static final String KEY_ARMOR      = "armor";
    private static final String KEY_OFFHAND    = "offhand";

    private final Path kitsDir;
    private final Logger logger;
    private final ConcurrentMap<KitName, Kit> cache = new ConcurrentHashMap<>();

    public FileKitRepository(Path kitsDir, Logger logger) {
        this.kitsDir = Objects.requireNonNull(kitsDir, "kitsDir");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Scans {@link #kitsDir} and loads every {@code *.yml} file into the cache.
     * Files that fail to parse are logged and skipped; one corrupt kit must
     * not prevent the plugin from starting.
     *
     * <p>Intended to be called once during plugin enable.
     */
    public void loadAll() {
        cache.clear();
        if (!Files.isDirectory(kitsDir)) {
            logger.info("Kits directory {} does not exist yet; starting with no kits.", kitsDir);
            return;
        }
        int loaded = 0;
        int failed = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(kitsDir, "*" + FILE_EXTENSION)) {
            for (Path file : stream) {
                try {
                    Kit kit = readKitFile(file);
                    cache.put(kit.name(), kit);
                    loaded++;
                } catch (Exception ex) {
                    failed++;
                    logger.warn("Failed to load kit file {}: {}", file.getFileName(), ex.getMessage(), ex);
                }
            }
        } catch (IOException ex) {
            logger.error("Failed to enumerate kits directory {}: {}", kitsDir, ex.getMessage(), ex);
        }
        logger.info("Loaded {} kit(s) from {} ({} failed).", loaded, kitsDir, failed);
    }

    @Override
    public Collection<Kit> all() {
        return Collections.unmodifiableCollection(cache.values());
    }

    @Override
    public Optional<Kit> find(KitName name) {
        Objects.requireNonNull(name, "name");
        return Optional.ofNullable(cache.get(name));
    }

    @Override
    public boolean save(Kit kit) throws KitPersistenceException {
        Objects.requireNonNull(kit, "kit");
        boolean replaced = cache.containsKey(kit.name());
        try {
            ensureKitsDirExists();
            writeKitFileAtomically(kit);
        } catch (IOException ex) {
            throw new KitPersistenceException(
                    "Could not save kit '" + kit.name().value() + "': " + ex.getMessage(), ex);
        }
        cache.put(kit.name(), kit);
        return replaced;
    }

    @Override
    public boolean delete(KitName name) throws KitPersistenceException {
        Objects.requireNonNull(name, "name");
        if (!cache.containsKey(name)) {
            return false;
        }
        Path file = name.resolveFile(kitsDir, FILE_EXTENSION);
        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            throw new KitPersistenceException(
                    "Could not delete kit '" + name.value() + "': " + ex.getMessage(), ex);
        }
        cache.remove(name);
        return true;
    }

    private void ensureKitsDirExists() throws IOException {
        if (!Files.isDirectory(kitsDir)) {
            Files.createDirectories(kitsDir);
        }
    }

    private void writeKitFileAtomically(Kit kit) throws IOException {
        Path target = kit.name().resolveFile(kitsDir, FILE_EXTENSION);
        Path temp = kit.name().resolveFile(kitsDir, TEMP_EXTENSION);

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set(KEY_NAME, kit.name().value());
        yaml.set(KEY_CREATED_AT, kit.createdAt().toString());
        if (kit.createdBy() != null) {
            yaml.set(KEY_CREATED_BY, kit.createdBy().toString());
        }
        yaml.set(KEY_STORAGE, ItemStackCodec.encodeArray(kit.contents().storage()));
        yaml.set(KEY_ARMOR, ItemStackCodec.encodeArray(kit.contents().armor()));
        String offhand = ItemStackCodec.encode(kit.contents().offHand());
        yaml.set(KEY_OFFHAND, offhand == null ? "" : offhand);

        yaml.save(temp.toFile());
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Kit readKitFile(Path file) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file.toFile());

        String rawName = yaml.getString(KEY_NAME);
        if (rawName == null || rawName.isEmpty()) {
            throw new InvalidConfigurationException("missing 'name'");
        }
        KitName name = new KitName(rawName);

        Instant createdAt = readInstant(yaml.getString(KEY_CREATED_AT));
        UUID createdBy = readUuid(yaml.getString(KEY_CREATED_BY));

        List<String> storageEncoded = readStringList(yaml, KEY_STORAGE, InventorySnapshot.STORAGE_SIZE);
        List<String> armorEncoded = readStringList(yaml, KEY_ARMOR, InventorySnapshot.ARMOR_SIZE);
        String offhandEncoded = yaml.getString(KEY_OFFHAND, "");

        ItemStack[] storage = ItemStackCodec.decodeArray(storageEncoded, InventorySnapshot.STORAGE_SIZE);
        ItemStack[] armor = ItemStackCodec.decodeArray(armorEncoded, InventorySnapshot.ARMOR_SIZE);
        ItemStack offHand = ItemStackCodec.decode(offhandEncoded);

        return new Kit(name, new InventorySnapshot(storage, armor, offHand), createdAt, createdBy);
    }

    private static Instant readInstant(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Instant.EPOCH;
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException ex) {
            return Instant.EPOCH;
        }
    }

    private static UUID readUuid(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static List<String> readStringList(YamlConfiguration yaml, String key, int expectedLength)
            throws InvalidConfigurationException {
        List<?> raw = yaml.getList(key);
        if (raw == null) {
            throw new InvalidConfigurationException("missing '" + key + "'");
        }
        List<String> out = new ArrayList<>(raw.size());
        for (Object entry : raw) {
            out.add(entry == null ? "" : entry.toString());
        }
        if (out.size() != expectedLength) {
            throw new InvalidConfigurationException(
                    "expected " + expectedLength + " entries under '" + key + "', got " + out.size());
        }
        return out;
    }
}
