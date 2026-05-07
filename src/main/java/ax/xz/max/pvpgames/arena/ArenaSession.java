package ax.xz.max.pvpgames.arena;

import ax.xz.max.async.Result;
import ax.xz.max.pvpgames.schematic.BlockVec3;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * A live arena instance: schematic pasted into the shared arenas world at an
 * allocated origin, WorldGuard region registered around it, players possibly
 * inside. Created by {@link ArenaManager#openSession} and destroyed by
 * {@link ArenaManager#closeSession}.
 *
 * <p>Editing operations (add / visit / remove spawn) and player movement
 * (join / leave) live HERE rather than on the manager because they belong to
 * a specific session. The manager's responsibility is limited to the pool
 * itself: creating sessions, looking them up, and destroying them.
 *
 * <p>All methods must run on the server main thread.
 */
public interface ArenaSession {

    /** Unique within this server run; assigned by the manager. */
    long id();

    /** The arena this session is hosting. */
    ArenaName arenaName();

    /** The shared world this session lives in. */
    World world();

    /**
     * The grid origin assigned by the allocator at session creation. Spawn
     * offsets are relative to this point; the WorldGuard region's bounds are
     * computed from this point too.
     */
    BlockVec3 origin();

    /**
     * The latest arena snapshot. Edits made through this session update both
     * the repository and the cached snapshot, so this always reflects the
     * current state. Use {@code arena().spawns()} to read the spawn list.
     */
    Arena arena();

    /**
     * Translates an arena-relative spawn point into a world-absolute
     * {@link Location} by adding {@link #origin()}.
     */
    Location toWorldLocation(SpawnPoint spawn);

    // ---- player movement ----------------------------------------------

    /**
     * Adds {@code player} to the session: snapshots their pre-session state
     * if not already cached, applies the join baseline (empty inventory,
     * full health, etc.), and teleports them to the first spawn point (or
     * the session origin if the spawn list is empty). Always succeeds.
     */
    void joinPlayer(Player player);

    /**
     * Removes {@code player} from the session and restores their cached
     * pre-session state. Idempotent.
     *
     * @return {@code true} if a snapshot was restored, {@code false} if the
     *         player had no cached state
     */
    boolean leavePlayer(Player player);

    // ---- spawn-list editing -------------------------------------------

    /**
     * Appends {@code player}'s current location as a new spawn point
     * (translated to arena-relative coordinates) and persists the change.
     *
     * @return {@code Ok} on success, or {@code Err} carrying an I/O failure
     *         message
     */
    Result<Void, String> addSpawnAtPlayer(Player player);

    /**
     * Appends a new spawn point at the given world-absolute coordinates,
     * with rotation taken from {@code player} if {@code yaw} or {@code pitch}
     * is {@code null}.
     */
    Result<Void, String> addSpawnExplicit(Player player, double x, double y, double z, Float yaw, Float pitch);

    /**
     * Teleports {@code player} to the spawn at {@code oneBasedIndex}.
     *
     * @return on success the spawn that was visited; on failure an error
     *         message that can be shown to players (out-of-range index)
     */
    Result<SpawnPoint, String> visitSpawn(Player player, int oneBasedIndex);

    /**
     * Removes the spawn at {@code oneBasedIndex} and persists the change.
     *
     * @return on success the spawn that was removed; on failure an error
     *         message that can be shown to players (out-of-range index or
     *         I/O failure)
     */
    Result<SpawnPoint, String> removeSpawn(int oneBasedIndex);

    /**
     * Updates one of the arena's configurable WorldGuard flags, persists
     * the new value, and re-applies it to the live region so it takes
     * effect without reopening the session.
     *
     * @param name  one of {@link ArenaFlags#FLAG_NAMES}; unknown names
     *              return {@code Err} with a message listing valid names
     * @param value {@code true} maps to WorldGuard's {@code allow} state,
     *              {@code false} maps to {@code deny}
     * @return Ok on success, Err with a player-facing message on failure
     */
    Result<Void, String> setFlag(String name, boolean value);

    /**
     * Returns {@code true} when {@code player}'s current location is inside
     * the session's region.
     */
    boolean contains(Player player);
}
