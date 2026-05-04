package ax.xz.max.pvpgames.arena;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * High-level arena management API used by command handlers and (later) game
 * lifecycle code.
 *
 * <p>The service owns two pieces of in-memory state:
 * <ul>
 *   <li>A <b>session pool</b> of {@link ArenaSession}s, one per temporary
 *       preview world we created. Sessions are added by {@link #preview} and
 *       removed only on {@link #shutdown}.</li>
 *   <li>A <b>saved-state map</b> of {@code playerId -> PlayerStateSnapshot},
 *       holding each player's pre-session state so {@link #leave} can
 *       restore it.</li>
 * </ul>
 *
 * <p>All methods that take raw user input return sealed {@link ArenaResult}
 * variants so the caller can pattern-match on each failure mode without
 * try/catch chains.
 *
 * <p>Unless documented otherwise, methods must be invoked from the server
 * main thread because they read or mutate live {@link Player} state and load
 * / delete worlds.
 */
public interface ArenaService {

    /**
     * Creates a new arena named {@code rawArenaName} that references the
     * schematic {@code schematicName}. The schematic file does not have to
     * exist yet; existence is checked lazily at {@code preview} time.
     */
    ArenaResult.CreateResult create(CommandSender creator, String rawArenaName, String schematicName);

    /**
     * Permanently deletes the arena named {@code rawArenaName}. Active
     * sessions referencing the arena are not terminated; their spawn-list
     * commands will start returning {@code ArenaMissing}.
     */
    ArenaResult.DeleteResult delete(String rawArenaName);

    /** All known arena names, sorted alphabetically. */
    List<ArenaName> listNames();

    /** Looks up an arena by raw name. */
    Optional<Arena> find(String rawArenaName);

    /**
     * Creates a new session for the named arena and joins {@code player} to
     * it: a fresh world is built, the schematic is pasted, the player's
     * pre-session state is captured (if not already cached), the
     * {@link ax.xz.max.pvpgames.player.PlayerStateSnapshot#DEFAULT default
     * baseline} is applied to wipe their inventory, and they are teleported
     * to the arena's first spawn point (or the world's spawn if there are
     * none yet).
     */
    ArenaResult.PreviewResult preview(Player player, String rawArenaName);

    /**
     * Joins {@code player} to an existing session by ID. Same join semantics
     * as {@link #preview} (capture-if-absent, apply DEFAULT, teleport to
     * spawn).
     */
    ArenaResult.JoinResult join(Player player, long sessionId);

    /**
     * Pops the player's saved pre-session state and applies it, returning
     * them to the world / location / inventory / health / etc. they had when
     * they first joined any session. Returns
     * {@link ArenaResult.LeaveResult.NoActiveSession} when no saved state is
     * cached (e.g. they were never in a session, or their state was already
     * popped).
     */
    ArenaResult.LeaveResult leave(Player player);

    /** Snapshot of all currently-active sessions in the pool. */
    Collection<ArenaSession> activeSessions();

    // Spawn-list operations: derive the arena from the player's current session.

    ArenaResult.AddSpawnResult addSpawnAtPlayer(Player player);
    ArenaResult.AddSpawnResult addSpawnExplicit(Player player, double x, double y, double z, Float yaw, Float pitch);
    ArenaResult.ListSpawnResult listSpawns(Player player);
    ArenaResult.VisitSpawnResult visitSpawn(Player player, int oneBasedIndex);
    ArenaResult.RemoveSpawnResult removeSpawn(Player player, int oneBasedIndex);

    /**
     * Tears down everything the service owns: restores every cached player
     * state to its owner (if online), then deletes every session world.
     * Intended to be called from the plugin's {@code onDisable}.
     */
    void shutdown();
}
