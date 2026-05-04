package ax.xz.max.pvpgames.worldguard;

import ax.xz.max.pvpgames.schematic.BlockVec3;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Map;

/**
 * Fallback {@link WorldGuardService} used when WorldGuard is not installed.
 *
 * <p>Mutating operations throw {@link WorldGuardException}; query operations
 * return safe empty values. The plugin enables and the rest of the system
 * (kits, arena data, etc.) works fine; world-touching arena commands surface
 * the missing-dependency message via the unavailable arena manager.
 */
public final class UnavailableWorldGuardService implements WorldGuardService {

    public static final String MESSAGE = "WorldGuard is not installed.";

    @Override
    public ProtectedArenaRegion createRegion(World world, String regionId, BlockVec3 min, BlockVec3 max)
            throws WorldGuardException {
        throw new WorldGuardException(MESSAGE);
    }

    @Override
    public void removeRegion(World world, String regionId) {
        // no-op: nothing to remove without WorldGuard
    }

    @Override
    public void applyFlags(ProtectedArenaRegion region, Map<String, String> flags) throws WorldGuardException {
        throw new WorldGuardException(MESSAGE);
    }

    @Override
    public boolean contains(World world, String regionId, Location loc) {
        return false;
    }

    @Override
    public void shutdown() {
        // No regions were ever created.
    }
}
