package ax.xz.max.pvpgames.arena;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * High-level entry point for the arena subsystem. Owns the shared "arenas"
 * world and the pool of live {@link ArenaSession}s; per-session logic
 * (joining, leaving, editing spawn lists) is delegated to
 * {@link ArenaSession}.
 *
 * <p>Persistent CRUD ({@link #create}, {@link #delete}, {@link #find},
 * {@link #listNames}) operates against the on-disk arena repository and
 * works whether or not the world / schematic / region dependencies are
 * available; session lifecycle ({@link #openSession},
 * {@link #closeSession}) requires Multiverse-Core, WorldEdit, and WorldGuard
 * to all be present and surfaces a {@code DependencyMissing} result
 * otherwise.
 *
 * <p>All methods must run on the server main thread.
 */
public interface ArenaManager {

    // ---- persistent CRUD ----------------------------------------------

    ArenaResult.CreateResult create(CommandSender creator, String rawArenaName, String rawSchematicName);

    ArenaResult.DeleteResult delete(String rawArenaName);

    /** Returns the names of every arena currently on disk. */
    List<ArenaName> listNames();

    /** Looks up an arena by raw user-supplied name; case-insensitive. */
    Optional<Arena> find(String rawArenaName);

    // ---- session lifecycle --------------------------------------------

    /**
     * Allocates a grid slot in the shared world, pastes the named arena's
     * schematic at that origin, registers a WorldGuard region around it, and
     * applies the arena's flag map. The returned session is registered in the
     * pool until {@link #closeSession} or {@link #shutdown} is called.
     *
     * <p>The caller is responsible for joining players to the returned
     * session via {@link ArenaSession#joinPlayer}; this method does NOT
     * teleport anyone.
     */
    ArenaResult.OpenSessionResult openSession(String rawArenaName);

    /**
     * Removes a session from the pool: the WorldGuard region is removed, the
     * grid slot is released back to the allocator, and any players still
     * inside the session are left where they are (the caller should restore
     * them via {@link ArenaSession#leavePlayer} first).
     */
    ArenaResult.CloseSessionResult closeSession(long sessionId);

    /** Looks up an existing session by id. */
    Optional<ArenaSession> findSession(long sessionId);

    /**
     * Returns the session whose region currently contains {@code player}.
     * Used by command handlers to derive "which session am I in?" without
     * passing a session id around.
     */
    Optional<ArenaSession> findSessionFor(Player player);

    /** Read-only snapshot of the active session pool. */
    Collection<ArenaSession> activeSessions();

    /**
     * Restores every cached pre-session player state and closes every open
     * session. Called from the plugin's {@code onDisable} BEFORE the shared
     * world is deleted; after this returns, no sessions remain in the pool.
     */
    void shutdown();
}
