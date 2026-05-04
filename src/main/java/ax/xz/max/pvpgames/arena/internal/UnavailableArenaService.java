package ax.xz.max.pvpgames.arena.internal;

import ax.xz.max.pvpgames.arena.Arena;
import ax.xz.max.pvpgames.arena.ArenaName;
import ax.xz.max.pvpgames.arena.ArenaPersistenceException;
import ax.xz.max.pvpgames.arena.ArenaRepository;
import ax.xz.max.pvpgames.arena.ArenaResult;
import ax.xz.max.pvpgames.arena.ArenaService;
import ax.xz.max.pvpgames.arena.ArenaSession;
import ax.xz.max.pvpgames.command.NameParseResult;
import ax.xz.max.pvpgames.schematic.SchematicName;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Clock;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link ArenaService} used when the world-management or schematic-loading
 * dependencies (Multiverse-Core, WorldEdit) are not installed.
 *
 * <p>Pure data operations (create / delete / list / info) still work because
 * they only touch the {@link ArenaRepository} on disk. Anything that needs to
 * load worlds or paste schematics returns a {@code DependencyMissing} (or, for
 * the spawn-list ops, {@code NotInArenaWorld}, since no session can exist when
 * sessions can't be opened) so the player gets a clean message instead of a
 * confusing world-load failure.
 *
 * <p>Wiring this implementation in place of {@link DefaultArenaService} is the
 * mechanism that lets {@code DefaultArenaService} stay free of
 * {@code instanceof UnavailableXService} branches: by the time
 * {@code DefaultArenaService} is constructed, both dependencies are known to
 * be present.
 */
public final class UnavailableArenaService implements ArenaService {

    private final ArenaRepository repository;
    private final Clock clock;
    private final String message;

    /**
     * @param message human-readable description of which dependencies are
     *                missing, e.g. {@code "Multiverse-Core is not installed."}
     */
    public UnavailableArenaService(ArenaRepository repository, Clock clock, String message) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.message = Objects.requireNonNull(message, "message");
    }

    // --- CRUD: works without world/schematic deps --------------------------

    @Override
    public ArenaResult.CreateResult create(CommandSender creator, String rawArenaName, String rawSchematicName) {
        Objects.requireNonNull(creator, "creator");

        NameParseResult<ArenaName> parsedName = ArenaName.tryParse(rawArenaName);
        if (parsedName instanceof NameParseResult.Invalid<ArenaName>(String reason)) {
            return new ArenaResult.CreateResult.InvalidName(reason);
        }
        ArenaName name = ((NameParseResult.Valid<ArenaName>) parsedName).name();

        NameParseResult<SchematicName> parsedSchematic = SchematicName.tryParse(rawSchematicName);
        if (parsedSchematic instanceof NameParseResult.Invalid<SchematicName>(String reason)) {
            return new ArenaResult.CreateResult.InvalidSchematic(reason);
        }
        SchematicName schematic = ((NameParseResult.Valid<SchematicName>) parsedSchematic).name();

        Arena arena = new Arena(
                name,
                schematic,
                List.of(),
                clock.instant(),
                creator instanceof Player p ? p.getUniqueId() : null);
        try {
            boolean replaced = repository.save(arena);
            return new ArenaResult.CreateResult.Created(arena, replaced);
        } catch (ArenaPersistenceException ex) {
            return new ArenaResult.CreateResult.IoError(ex.getMessage());
        }
    }

    @Override
    public ArenaResult.DeleteResult delete(String rawArenaName) {
        NameParseResult<ArenaName> parsed = ArenaName.tryParse(rawArenaName);
        if (parsed instanceof NameParseResult.Invalid<ArenaName>(String reason)) {
            return new ArenaResult.DeleteResult.InvalidName(reason);
        }
        ArenaName name = ((NameParseResult.Valid<ArenaName>) parsed).name();

        try {
            boolean existed = repository.delete(name);
            return existed
                    ? new ArenaResult.DeleteResult.Deleted(name)
                    : new ArenaResult.DeleteResult.NotFound(rawArenaName);
        } catch (ArenaPersistenceException ex) {
            return new ArenaResult.DeleteResult.IoError(ex.getMessage());
        }
    }

    @Override
    public List<ArenaName> listNames() {
        return repository.all().stream()
                .map(Arena::name)
                .sorted(Comparator.comparing(ArenaName::value))
                .toList();
    }

    @Override
    public Optional<Arena> find(String rawArenaName) {
        NameParseResult<ArenaName> parsed = ArenaName.tryParse(rawArenaName);
        if (parsed instanceof NameParseResult.Valid<ArenaName>(ArenaName name)) {
            return repository.find(name);
        }
        return Optional.empty();
    }

    // --- Sessions: every entry point fails cleanly -------------------------

    @Override
    public ArenaResult.PreviewResult preview(Player player, String rawArenaName) {
        return new ArenaResult.PreviewResult.DependencyMissing(message);
    }

    @Override
    public ArenaResult.JoinResult join(Player player, long sessionId) {
        return new ArenaResult.JoinResult.DependencyMissing(message);
    }

    @Override
    public ArenaResult.LeaveResult leave(Player player) {
        // No session can exist; nothing to leave.
        return new ArenaResult.LeaveResult.NoActiveSession();
    }

    @Override
    public Collection<ArenaSession> activeSessions() {
        return List.of();
    }

    @Override
    public ArenaResult.AddSpawnResult addSpawnAtPlayer(Player player) {
        return new ArenaResult.AddSpawnResult.NotInArenaWorld();
    }

    @Override
    public ArenaResult.AddSpawnResult addSpawnExplicit(
            Player player, double x, double y, double z, Float yaw, Float pitch) {
        return new ArenaResult.AddSpawnResult.NotInArenaWorld();
    }

    @Override
    public ArenaResult.ListSpawnResult listSpawns(Player player) {
        return new ArenaResult.ListSpawnResult.NotInArenaWorld();
    }

    @Override
    public ArenaResult.VisitSpawnResult visitSpawn(Player player, int oneBasedIndex) {
        return new ArenaResult.VisitSpawnResult.NotInArenaWorld();
    }

    @Override
    public ArenaResult.RemoveSpawnResult removeSpawn(Player player, int oneBasedIndex) {
        return new ArenaResult.RemoveSpawnResult.NotInArenaWorld();
    }

    @Override
    public void shutdown() {
        // No worlds were ever created.
    }
}
