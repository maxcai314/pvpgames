package ax.xz.max.pvpgames.arena.internal.manager;

import ax.xz.max.async.Promise;
import ax.xz.max.async.Result;
import ax.xz.max.pvpgames.arena.Arena;
import ax.xz.max.pvpgames.arena.ArenaManager;
import ax.xz.max.pvpgames.arena.ArenaName;
import ax.xz.max.pvpgames.arena.ArenaPersistenceException;
import ax.xz.max.pvpgames.arena.ArenaRepository;
import ax.xz.max.pvpgames.arena.ArenaResult;
import ax.xz.max.pvpgames.arena.ArenaSession;
import ax.xz.max.pvpgames.schematic.SchematicName;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Clock;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * {@link ArenaManager} used when one or more of the world / schematic /
 * region dependencies (Multiverse-Core, WorldEdit, WorldGuard) is missing.
 *
 * <p>Persistent CRUD (create / delete / list / find) still works because it
 * only touches the {@link ArenaRepository} on disk. Anything that would
 * require pasting a schematic or registering a region returns
 * {@code DependencyMissing}; the player gets a clear message instead of a
 * confusing internal failure.
 *
 * <p>Wiring this implementation in place of {@link DefaultArenaManager} is
 * the mechanism that lets {@code DefaultArenaManager} stay free of
 * {@code instanceof Unavailable*Service} branches: by the time
 * {@code DefaultArenaManager} is constructed, all dependencies are known to
 * be present.
 */
public final class UnavailableArenaManager implements ArenaManager {

    private final ArenaRepository repository;
    private final Clock clock;
    private final String message;

    public UnavailableArenaManager(ArenaRepository repository, Clock clock, String message) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.message = Objects.requireNonNull(message, "message");
    }

    // ---- persistent CRUD ----------------------------------------------

    @Override
    public ArenaResult.CreateResult create(CommandSender creator, String rawArenaName, String rawSchematicName) {
        Objects.requireNonNull(creator, "creator");

        Result<ArenaName, String> parsedName = ArenaName.tryParse(rawArenaName);
        if (parsedName instanceof Result.Err<ArenaName, String>(String reason)) {
            return new ArenaResult.CreateResult.InvalidName(reason);
        }
        ArenaName name = ((Result.Ok<ArenaName, String>) parsedName).val();

        Result<SchematicName, String> parsedSchematic = SchematicName.tryParse(rawSchematicName);
        if (parsedSchematic instanceof Result.Err<SchematicName, String>(String reason)) {
            return new ArenaResult.CreateResult.InvalidSchematic(reason);
        }
        SchematicName schematic = ((Result.Ok<SchematicName, String>) parsedSchematic).val();

        Arena arena = new Arena(
                name,
                schematic,
                List.of(),
                Map.of(),
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
        Result<ArenaName, String> parsed = ArenaName.tryParse(rawArenaName);
        if (parsed instanceof Result.Err<ArenaName, String>(String reason)) {
            return new ArenaResult.DeleteResult.InvalidName(reason);
        }
        ArenaName name = ((Result.Ok<ArenaName, String>) parsed).val();

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
        Result<ArenaName, String> parsed = ArenaName.tryParse(rawArenaName);
        if (parsed instanceof Result.Ok<ArenaName, String>(ArenaName name)) {
            return repository.find(name);
        }
        return Optional.empty();
    }

    // ---- session lifecycle (every entry point fails cleanly) ----------

    @Override
    public Promise<ArenaResult.OpenSessionResult> openSession(String rawArenaName) {
        return Promise.completedFuture(new ArenaResult.OpenSessionResult.DependencyMissing(message));
    }

    @Override
    public ArenaResult.CloseSessionResult closeSession(long sessionId) {
        return new ArenaResult.CloseSessionResult.NotFound(sessionId);
    }

    @Override
    public Optional<ArenaSession> findSession(long sessionId) {
        return Optional.empty();
    }

    @Override
    public Optional<ArenaSession> findSessionFor(Player player) {
        return Optional.empty();
    }

    @Override
    public Collection<ArenaSession> activeSessions() {
        return List.of();
    }

    @Override
    public void shutdown() {
        // No worlds, regions, or sessions were ever created.
    }
}
