package ax.xz.max.pvpgames.arena;

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
 * <p>Editing operations (add / list / visit / remove spawn) and player
 * movement (join / leave) live HERE rather than on the manager because they
 * belong to a specific session: the session knows the arena name, the world,
 * the origin, and the WorldGuard region. The manager's responsibility is
 * limited to the pool itself: creating sessions, looking them up, and
 * destroying them.
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
     * The arena snapshot used to build this session. Edits made through this
     * session update both the repository and the cached snapshot, so this
     * always reflects the latest state.
     */
    Arena arena();

    /**
     * Translates an arena-relative spawn point into a world-absolute
     * {@link Location} by adding {@link #origin()}.
     */
    Location toWorldLocation(SpawnPoint spawn);

    /**
     * Adds {@code player} to the session: snapshots their pre-session state
     * if not already cached, applies the join baseline (empty inventory, full
     * health, etc.), and teleports them to the first spawn point (or the
     * session origin if the spawn list is empty).
     */
    ArenaResult.JoinResult joinPlayer(Player player);

    /**
     * Removes {@code player} from the session and restores their cached
     * pre-session state. Idempotent; returns {@code NoActiveSession} when
     * the player has no cached state.
     */
    ArenaResult.LeaveResult leavePlayer(Player player);

    /**
     * Appends {@code player}'s current location as a new spawn point
     * (translated to arena-relative coordinates) and persists the change.
     */
    ArenaResult.AddSpawnResult addSpawnAtPlayer(Player player);

    /**
     * Appends a new spawn point at the given world-absolute coordinates,
     * with rotation taken from {@code player} if {@code yaw} or
     * {@code pitch} is {@code null}.
     */
    ArenaResult.AddSpawnResult addSpawnExplicit(Player player, double x, double y, double z, Float yaw, Float pitch);

    /** Returns the current spawn list. */
    ArenaResult.ListSpawnResult listSpawns();

    /**
     * Teleports {@code player} to the spawn at {@code oneBasedIndex}.
     * Returns {@code IndexOutOfRange} when the index is outside
     * {@code [1, spawns.size()]}.
     */
    ArenaResult.VisitSpawnResult visitSpawn(Player player, int oneBasedIndex);

    /**
     * Removes the spawn at {@code oneBasedIndex} and persists the change.
     * Returns {@code IndexOutOfRange} when the index is outside
     * {@code [1, spawns.size()]}.
     */
    ArenaResult.RemoveSpawnResult removeSpawn(int oneBasedIndex);

    /**
     * Returns {@code true} when {@code player}'s current location is inside
     * the session's region. Used by the manager to derive a player's session
     * from their location.
     */
    boolean contains(Player player);
}
