package ax.xz.max.pvpgames.worldguard;

import ax.xz.max.pvpgames.schematic.BlockVec3;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Map;

/**
 * Abstraction over the WorldGuard plugin. Lets the rest of the plugin create
 * arena cuboid regions and apply behavior flags without importing the
 * WorldGuard API directly.
 *
 * <p>When WorldGuard is absent, an {@link UnavailableWorldGuardService} is
 * wired in instead so the rest of the plugin keeps working; mutating
 * operations throw {@link WorldGuardException} with a clear "plugin missing"
 * message and queries return safe empty values.
 */
public interface WorldGuardService {

    /**
     * Creates a cuboid region with id {@code regionId} bounded by the given
     * inclusive corners in {@code world} and registers it with WorldGuard's
     * region manager.
     *
     * @param world    the world to register the region in
     * @param regionId a unique id within this world; conventionally
     *                 {@code "pvpgames_arena_<sessionId>"}
     * @param min      inclusive minimum corner
     * @param max      inclusive maximum corner
     * @return a handle that can be passed to {@link #applyFlags}
     * @throws WorldGuardException if WorldGuard is unavailable, the region
     *         manager is not loaded for {@code world}, or a region with the
     *         same id already exists
     */
    ProtectedArenaRegion createRegion(World world, String regionId, BlockVec3 min, BlockVec3 max)
            throws WorldGuardException;

    /**
     * Removes the named region from {@code world}'s region manager. Idempotent;
     * removing a region that does not exist is a no-op.
     */
    void removeRegion(World world, String regionId);

    /**
     * Applies textual flag values to {@code region}. Each entry's flag name is
     * looked up in WorldGuard's flag registry; the raw string value is parsed
     * by the flag's own {@code parseInput}, so the format is whatever WG
     * accepts (e.g. {@code "allow"}/{@code "deny"} for state flags,
     * {@code "true"}/{@code "false"} for boolean flags, integers for integer
     * flags).
     *
     * <p>Flags are applied to the default group only; group-targeted flag
     * overrides are out of scope for this service.
     *
     * @throws WorldGuardException if WorldGuard is unavailable, the flag name
     *         is unknown to the registry, or a value fails to parse
     */
    void applyFlags(ProtectedArenaRegion region, Map<String, String> flags) throws WorldGuardException;

    /**
     * Returns {@code true} if {@code loc} lies inside the named region in
     * {@code world}. Returns {@code false} when WorldGuard is unavailable, the
     * world has no region manager, or the region does not exist.
     */
    boolean contains(World world, String regionId, Location loc);

    /**
     * Removes any region this service still has open and forces a save of
     * every region manager it has touched, so a future plugin enable does
     * not see stale regions persisted by WorldGuard's per-world database.
     *
     * <p>{@link #removeRegion} only changes the in-memory state of the
     * region manager; WorldGuard's automatic save runs during world unload,
     * which is too late if the arena world is being deleted at plugin
     * disable. This method makes the save explicit and synchronous.
     *
     * <p>Call from the plugin's {@code onDisable} (or from
     * {@code ArenaManager#shutdown}) BEFORE the arena world is deleted.
     */
    void shutdown();
}
