package ax.xz.max.pvpgames.worldguard;

import ax.xz.max.pvpgames.schematic.BlockVec3;
import org.bukkit.World;

/**
 * Abstraction over the WorldGuard plugin scoped to a single {@link World}.
 * Each instance is bound to one world at construction and operates only
 * on that world; callers do not pass a world through every call.
 *
 * <p>The service is a region factory: callers ask for a
 * {@link ProtectedArenaRegion} by cuboid bounds, get back an opaque
 * handle, and operate on it through the handle's own API
 * ({@link ProtectedArenaRegion#contains},
 * {@link ProtectedArenaRegion#applyFlags}). The WorldGuard region ID
 * format, the number of underlying regions per handle, and any other
 * details of how protection is realized are owned by the
 * implementation.
 *
 * <p>Lifetime: an instance lives as long as the world it is bound to
 * and must be {@link #shutdown() shut down} before that world is
 * unloaded. Typically constructed and destroyed by the class that
 * owns the world (today, {@code DefaultArenaManager}).
 */
public interface WorldGuardService {

    /** The world this service is bound to; fixed at construction. */
    World world();

    /**
     * Creates a protected region bounded by the given inclusive corners
     * in {@link #world()}. The returned handle is opaque; callers
     * cannot tell how many underlying WorldGuard regions back it.
     *
     * @throws WorldGuardException if the region manager is not loaded
     *         for {@link #world()} or the underlying WG region could
     *         not be registered
     */
    ProtectedArenaRegion createRegion(BlockVec3 min, BlockVec3 max) throws WorldGuardException;

    /**
     * Removes {@code region} from {@link #world()}. Idempotent: removing
     * an already-removed handle is a no-op. Handles created by a
     * different service instance are ignored.
     */
    void removeRegion(ProtectedArenaRegion region);

    /**
     * Removes any region this service still has open and forces a save
     * of its region manager, so a future plugin enable does not see
     * stale regions persisted by WorldGuard's per-world database. Must
     * run before {@link #world()} is unloaded.
     */
    void shutdown();
}
