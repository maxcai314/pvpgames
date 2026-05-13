package ax.xz.max.pvpgames.arena;

import ax.xz.max.async.Promise;
import ax.xz.max.async.Result;
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
 * works whether or not the schematic / region soft dependencies are
 * available; {@link #openSession} requires FAWE and WorldGuard to both
 * be present and surfaces an {@code Err} otherwise.
 *
 * <p>All methods must run on the server main thread.
 * todo: maybe don't export behavior related to ArenaRepository?
 */
public interface ArenaManager {

    // ---- persistent CRUD ----------------------------------------------

    /**
     * Creates (or replaces) a persistent arena definition.
     *
     * @return on success the new arena and whether it replaced an existing
     *         one; on failure an error message that can be shown to players
     */
    Result<ArenaCreation, String> create(CommandSender creator, String rawArenaName, String rawSchematicName);

    /**
     * Deletes a persistent arena definition.
     *
     * @return {@code Ok(true)} if an arena was deleted, {@code Ok(false)} if
     *         no arena with that name existed, or {@code Err} error message
     */
    Result<Boolean, String> delete(String rawArenaName);

    /** Returns the names of every arena currently on disk. */
    List<ArenaName> listNames();

    /** Looks up an arena by raw user-supplied name; case-insensitive. */
    Optional<Arena> find(String rawArenaName);

    // ---- session lifecycle --------------------------------------------

    /**
     * Asynchronously opens a session for the named arena.
     *
     * <p>Pipeline: validate the name and allocate a grid slot on the main
     * thread; paste the schematic on a background thread; register the
     * WorldGuard region, apply flags, and add the session to the pool back
     * on the main thread. The returned promise completes once every step is
     * done, so by the time the caller observes {@code Ok} the schematic is
     * fully pasted and the session is safe to teleport players into.
     *
     * <p>The caller is responsible for joining players via
     * {@link ArenaSession#joinPlayer}; this method does NOT teleport anyone.
     *
     * @return a promise that resolves to the opened session, or to an error
     *         message that can be shown to players
     */
    Promise<Result<ArenaSession, String>> openSession(String rawArenaName);

    /**
     * Removes a session from the pool: the WorldGuard region is removed and
     * the grid slot is released back to the allocator. Any players still
     * inside the session are left where they are; the caller should restore
     * them via {@link ArenaSession#leavePlayer} first.
     *
     * @return {@code true} if a session with this id was closed,
     *         {@code false} if no such session existed
     */
    boolean closeSession(long sessionId);

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
