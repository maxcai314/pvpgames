package ax.xz.max.pvpgames.arena.internal;

import ax.xz.max.async.Promise;
import ax.xz.max.async.Result;
import ax.xz.max.pvpgames.arena.Arena;
import ax.xz.max.pvpgames.arena.ArenaCreation;
import ax.xz.max.pvpgames.arena.ArenaManager;
import ax.xz.max.pvpgames.arena.ArenaName;
import ax.xz.max.pvpgames.arena.ArenaPersistenceException;
import ax.xz.max.pvpgames.arena.ArenaRepository;
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
 * region dependencies is missing.
 *
 * <p>Persistent CRUD still works because it only touches the
 * {@link ArenaRepository} on disk. Anything that needs a schematic or region
 * returns the dependency-missing message; the player gets a clear hint
 * instead of a confusing internal failure.
 *
 * <p>Wiring this implementation in place of {@link DefaultArenaManager} is
 * the mechanism that lets {@code DefaultArenaManager} stay free of
 * {@code instanceof Unavailable*Service} branches.
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
    public Result<ArenaCreation, String> create(CommandSender creator, String rawArenaName, String rawSchematicName) {
        Objects.requireNonNull(creator, "creator");

        Result<ArenaName, String> parsedName = ArenaName.tryParse(rawArenaName);
        if (parsedName instanceof Result.Err<ArenaName, String>(String reason)) {
            return new Result.Err<>(reason);
        }
        ArenaName name = ((Result.Ok<ArenaName, String>) parsedName).val();

        Result<SchematicName, String> parsedSchematic = SchematicName.tryParse(rawSchematicName);
        if (parsedSchematic instanceof Result.Err<SchematicName, String>(String reason)) {
            return new Result.Err<>(reason);
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
            return new Result.Ok<>(new ArenaCreation(arena, replaced));
        } catch (ArenaPersistenceException ex) {
            return new Result.Err<>("Could not save arena: " + ex.getMessage());
        }
    }

    @Override
    public Result<Boolean, String> delete(String rawArenaName) {
        Result<ArenaName, String> parsed = ArenaName.tryParse(rawArenaName);
        if (parsed instanceof Result.Err<ArenaName, String>(String reason)) {
            return new Result.Err<>(reason);
        }
        ArenaName name = ((Result.Ok<ArenaName, String>) parsed).val();

        try {
            return new Result.Ok<>(repository.delete(name));
        } catch (ArenaPersistenceException ex) {
            return new Result.Err<>("Could not delete arena: " + ex.getMessage());
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
    public Promise<Result<ArenaSession, String>> openSession(String rawArenaName) {
        return Promise.completedFuture(new Result.Err<>(message));
    }

    @Override
    public boolean closeSession(long sessionId) {
        return false;
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
