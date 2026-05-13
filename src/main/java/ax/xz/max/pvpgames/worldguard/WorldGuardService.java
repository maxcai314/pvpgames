package ax.xz.max.pvpgames.worldguard;

import ax.xz.max.pvpgames.schematic.BlockVec3;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Map;

/**
 * Abstraction over the WorldGuard plugin scoped to a single {@link World}.
 * Each instance is bound to one world at construction and operates only on
 * that world; callers do not pass a world through every call. Lets the
 * arena subsystem create cuboid regions and apply behavior flags without
 * importing the WorldGuard API directly.
 *
 * <p>Lifetime: an instance lives as long as the world it is bound to and
 * must be {@link #shutdown() shut down} before that world is unloaded.
 */
public interface WorldGuardService {

    /** The world this service is bound to; fixed at construction. */
    World world();

    /**
     * Creates a cuboid region with id {@code regionId} bounded by the
     * given inclusive corners in {@link #world()} and registers it with
     * WorldGuard's region manager.
     *
     * @param regionId a unique id within this world; conventionally
     *                 {@code "pvpgames_arena_<sessionId>"}
     * @param min      inclusive minimum corner
     * @param max      inclusive maximum corner
     * @return a handle that can be passed to {@link #applyFlags}
     * @throws WorldGuardException if the region manager is not loaded
     *         for {@link #world()} or a region with the same id already
     *         exists
     */
    ProtectedArenaRegion createRegion(String regionId, BlockVec3 min, BlockVec3 max)
            throws WorldGuardException;

    /**
     * Removes the named region from {@link #world()}'s region manager.
     * Idempotent; removing a region that does not exist is a no-op.
     */
    void removeRegion(String regionId);

    /**
     * Applies textual flag values to {@code region}. Each entry's flag
     * name is looked up in WorldGuard's flag registry; the raw string
     * value is parsed by the flag's own {@code parseInput}, so the
     * format is whatever WG accepts (e.g. {@code "allow"}/{@code "deny"}
     * for state flags, {@code "true"}/{@code "false"} for boolean
     * flags, integers for integer flags).
     *
     * <p>Flags are applied to the default group only; group-targeted
     * flag overrides are out of scope for this service.
     *
     * @throws WorldGuardException if the flag name is unknown to the
     *         registry or a value fails to parse
     */
    void applyFlags(ProtectedArenaRegion region, Map<String, String> flags) throws WorldGuardException;

    /**
     * Returns {@code true} if {@code loc} lies inside the named region
     * in {@link #world()}. Returns {@code false} when the location is
     * in a different world, the world has no region manager, or the
     * region does not exist.
     */
    boolean contains(String regionId, Location loc);

    /**
     * Removes any region this service still has open and forces a save
     * of its region manager, so a future plugin enable does not see
     * stale regions persisted by WorldGuard's per-world database. Must
     * run before {@link #world()} is unloaded.
     *
     * <p>{@link #removeRegion} only changes in-memory state;
     * WorldGuard's automatic save runs during world unload, which is
     * too late if the world is about to be deleted. This method makes
     * the save explicit and synchronous.
     */
    void shutdown();
}
