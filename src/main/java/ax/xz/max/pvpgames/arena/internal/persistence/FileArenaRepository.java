package ax.xz.max.pvpgames.arena.internal.persistence;

import ax.xz.max.pvpgames.arena.Arena;
import ax.xz.max.pvpgames.arena.ArenaFlags;
import ax.xz.max.pvpgames.arena.ArenaName;
import ax.xz.max.pvpgames.arena.ArenaPersistenceException;
import ax.xz.max.pvpgames.arena.ArenaRepository;
import ax.xz.max.pvpgames.arena.SpawnPoint;
import ax.xz.max.pvpgames.schematic.SchematicName;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * {@link ArenaRepository} that stores one YAML file per arena under a
 * configured directory.
 *
 * <p>YAML metadata is plaintext for ease of inspection. Mirrors
 * {@code FileKitRepository} in its eager-load + atomic-write strategy: all
 * arenas are loaded into a {@link ConcurrentHashMap} on construction; writes
 * update the cache and flush to disk via write-temp + {@code ATOMIC_MOVE}.
 *
 * <p>Schema:
 * <pre>
 * name: stadium
 * schematic: stadium_v3
 * created-at: 2026-04-21T13:42:11.523Z
 * created-by: 0a8e1f6a-...
 * spawns:
 *   - { x: 0.5, y: 65.0, z: 0.5, yaw: 0.0, pitch: 0.0 }
 * flags:
 *   pvp: true
 *   block-break: false
 *   block-place: false
 *   fall-damage: true
 *   natural-health-regen: true
 *   natural-hunger-drain: true
 * </pre>
 *
 * <p>The {@code flags} section is optional; any flag not listed falls back
 * to {@link ArenaFlags#defaults()}. Unknown flag names in the file are
 * logged and skipped so adding or removing fields on {@link ArenaFlags}
 * does not corrupt existing arenas.
 */
public final class FileArenaRepository implements ArenaRepository {

    private static final String FILE_EXTENSION = ".yml";
    private static final String TEMP_EXTENSION = ".yml.tmp";

    private static final String KEY_NAME       = "name";
    private static final String KEY_SCHEMATIC  = "schematic";
    private static final String KEY_CREATED_AT = "created-at";
    private static final String KEY_CREATED_BY = "created-by";
    private static final String KEY_SPAWNS     = "spawns";
    private static final String KEY_FLAGS      = "flags";

    private static final String SPAWN_KEY_X     = "x";
    private static final String SPAWN_KEY_Y     = "y";
    private static final String SPAWN_KEY_Z     = "z";
    private static final String SPAWN_KEY_YAW   = "yaw";
    private static final String SPAWN_KEY_PITCH = "pitch";

    private final Path arenasDir;
    private final Logger logger;
    private final ConcurrentMap<ArenaName, Arena> cache = new ConcurrentHashMap<>();

    public FileArenaRepository(Path arenasDir, Logger logger) {
        this.arenasDir = Objects.requireNonNull(arenasDir, "arenasDir");
        this.logger = Objects.requireNonNull(logger, "logger");

        loadAllFromFileSystem();
    }

    /**
     * Scans {@link #arenasDir} and loads every {@code *.yml} file into the
     * cache. Files that fail to parse are logged and skipped; one corrupt
     * arena must not prevent the plugin from starting.
     */
    public void loadAllFromFileSystem() {
        cache.clear();
        if (!Files.isDirectory(arenasDir)) {
            logger.info("Arenas directory {} does not exist yet; starting with no arenas.", arenasDir);
            return;
        }
        int loaded = 0;
        int failed = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(arenasDir, "*" + FILE_EXTENSION)) {
            for (Path file : stream) {
                try {
                    Arena arena = readArenaFile(file);
                    cache.put(arena.name(), arena);
                    loaded++;
                } catch (Exception ex) {
                    failed++;
                    logger.warn("Failed to load arena file {}: {}", file.getFileName(), ex.getMessage(), ex);
                }
            }
        } catch (IOException ex) {
            logger.error("Failed to enumerate arenas directory {}: {}", arenasDir, ex.getMessage(), ex);
        }
        logger.info("Loaded {} arena(s) from {} ({} failed).", loaded, arenasDir, failed);
    }

    @Override
    public Collection<Arena> all() {
        return Collections.unmodifiableCollection(cache.values());
    }

    @Override
    public Optional<Arena> find(ArenaName name) {
        Objects.requireNonNull(name, "name");
        return Optional.ofNullable(cache.get(name));
    }

    @Override
    public boolean save(Arena arena) throws ArenaPersistenceException {
        Objects.requireNonNull(arena, "arena");
        boolean replaced = cache.containsKey(arena.name());
        try {
            ensureArenasDirExists();
            writeArenaFileAtomically(arena);
        } catch (IOException ex) {
            throw new ArenaPersistenceException(
                    "Could not save arena '" + arena.name().value() + "': " + ex.getMessage(), ex);
        }
        cache.put(arena.name(), arena);
        return replaced;
    }

    @Override
    public boolean delete(ArenaName name) throws ArenaPersistenceException {
        Objects.requireNonNull(name, "name");
        if (!cache.containsKey(name)) {
            return false;
        }
        Path file = name.resolveFile(arenasDir, FILE_EXTENSION);
        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            throw new ArenaPersistenceException(
                    "Could not delete arena '" + name.value() + "': " + ex.getMessage(), ex);
        }
        cache.remove(name);
        return true;
    }

    private void ensureArenasDirExists() throws IOException {
        if (!Files.isDirectory(arenasDir)) {
            Files.createDirectories(arenasDir);
        }
    }

    private void writeArenaFileAtomically(Arena arena) throws IOException {
        Path target = arena.name().resolveFile(arenasDir, FILE_EXTENSION);
        Path temp = arena.name().resolveFile(arenasDir, TEMP_EXTENSION);

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set(KEY_NAME, arena.name().value());
        yaml.set(KEY_SCHEMATIC, arena.schematicName().value());
        yaml.set(KEY_CREATED_AT, arena.createdAt().toString());
        if (arena.createdBy() != null) {
            yaml.set(KEY_CREATED_BY, arena.createdBy().toString());
        }
        yaml.set(KEY_SPAWNS, encodeSpawns(arena.spawns()));
        yaml.set(KEY_FLAGS, encodeFlags(arena.flags()));

        yaml.save(temp.toFile());
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static List<Map<String, Object>> encodeSpawns(List<SpawnPoint> spawns) {
        List<Map<String, Object>> out = new ArrayList<>(spawns.size());
        for (SpawnPoint spawn : spawns) {
            out.add(Map.of(
                    SPAWN_KEY_X, spawn.x(),
                    SPAWN_KEY_Y, spawn.y(),
                    SPAWN_KEY_Z, spawn.z(),
                    SPAWN_KEY_YAW, (double) spawn.yaw(),
                    SPAWN_KEY_PITCH, (double) spawn.pitch()));
        }
        return out;
    }

    /**
     * Encodes the flag set as a {@link LinkedHashMap} keyed by the canonical
     * flag names; values are booleans so YAML emits {@code true}/{@code false}
     * and admins can hand-edit without quoting.
     */
    private static Map<String, Object> encodeFlags(ArenaFlags flags) {
        Map<String, Object> out = new LinkedHashMap<>(ArenaFlags.FLAG_NAMES.size());
        for (String name : ArenaFlags.FLAG_NAMES) {
            out.put(name, flags.readFlag(name));
        }
        return out;
    }

    private Arena readArenaFile(Path file) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file.toFile());

        String rawName = yaml.getString(KEY_NAME);
        if (rawName == null || rawName.isEmpty()) {
            throw new InvalidConfigurationException("missing 'name'");
        }
        ArenaName name = new ArenaName(rawName);

        String rawSchematic = yaml.getString(KEY_SCHEMATIC);
        if (rawSchematic == null || rawSchematic.isEmpty()) {
            throw new InvalidConfigurationException("missing 'schematic'");
        }
        SchematicName schematic;
        try {
            schematic = new SchematicName(rawSchematic);
        } catch (IllegalArgumentException ex) {
            throw new InvalidConfigurationException("invalid 'schematic': " + ex.getMessage());
        }

        Instant createdAt = readInstant(yaml.getString(KEY_CREATED_AT));
        UUID createdBy = readUuid(yaml.getString(KEY_CREATED_BY));
        List<SpawnPoint> spawns = readSpawns(yaml);
        ArenaFlags flags = readFlags(yaml);

        return new Arena(name, schematic, spawns, flags, createdAt, createdBy);
    }

    private static List<SpawnPoint> readSpawns(YamlConfiguration yaml) throws InvalidConfigurationException {
        List<?> raw = yaml.getList(KEY_SPAWNS);
        if (raw == null) {
            return List.of();
        }
        List<SpawnPoint> out = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            Object entry = raw.get(i);
            Map<?, ?> map;
            if (entry instanceof Map<?, ?> m) {
                map = m;
            } else if (entry instanceof ConfigurationSection section) {
                map = section.getValues(false);
            } else {
                throw new InvalidConfigurationException(
                        "spawn[" + i + "] is not a map (got " + (entry == null ? "null" : entry.getClass().getSimpleName()) + ")");
            }
            out.add(new SpawnPoint(
                    requireDouble(map, SPAWN_KEY_X, i),
                    requireDouble(map, SPAWN_KEY_Y, i),
                    requireDouble(map, SPAWN_KEY_Z, i),
                    (float) requireDouble(map, SPAWN_KEY_YAW, i),
                    (float) requireDouble(map, SPAWN_KEY_PITCH, i)));
        }
        return out;
    }

    /**
     * Reads the optional {@code flags} section into an {@link ArenaFlags}.
     * Missing flags fall back to {@link ArenaFlags#defaults()}; unknown flag
     * names in the file are logged and ignored so removing a field from
     * {@code ArenaFlags} does not corrupt existing arenas.
     */
    private ArenaFlags readFlags(YamlConfiguration yaml) {
        ConfigurationSection section = yaml.getConfigurationSection(KEY_FLAGS);
        ArenaFlags result = ArenaFlags.defaults();
        if (section == null) {
            return result;
        }
        for (String key : section.getKeys(false)) {
            try {
                result = result.withFlag(key, section.getBoolean(key));
            } catch (IllegalArgumentException ex) {
                logger.warn("Ignoring unknown arena flag '{}' in YAML.", key);
            }
        }
        return result;
    }

    private static double requireDouble(Map<?, ?> map, String key, int index) throws InvalidConfigurationException {
        Object raw = map.get(key);
        if (raw instanceof Number n) {
            return n.doubleValue();
        }
        throw new InvalidConfigurationException(
                "spawn[" + index + "] missing or non-numeric '" + key + "'");
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
}
