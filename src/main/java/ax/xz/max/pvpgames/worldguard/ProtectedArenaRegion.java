package ax.xz.max.pvpgames.worldguard;

import ax.xz.max.pvpgames.schematic.BlockVec3;
import org.bukkit.World;

import java.util.Objects;

/**
 * Opaque handle to a WorldGuard region created via
 * {@link WorldGuardService#createRegion}.
 *
 * <p>Only the fields needed by the arena manager are exposed; the underlying
 * {@code ProtectedRegion} is left inside the service implementation so the
 * rest of the plugin does not import {@code com.sk89q.worldguard} types.
 *
 * @param world    the world the region was registered in
 * @param regionId the WorldGuard region id (unique per world)
 * @param min      inclusive minimum corner of the cuboid
 * @param max      inclusive maximum corner of the cuboid
 */
public record ProtectedArenaRegion(World world, String regionId, BlockVec3 min, BlockVec3 max) {

    public ProtectedArenaRegion {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(regionId, "regionId");
        Objects.requireNonNull(min, "min");
        Objects.requireNonNull(max, "max");
    }

    /** True when {@code (x, y, z)} falls inside the cuboid (inclusive). */
    public boolean contains(int x, int y, int z) {
        return x >= min.x() && x <= max.x()
                && y >= min.y() && y <= max.y()
                && z >= min.z() && z <= max.z();
    }
}
