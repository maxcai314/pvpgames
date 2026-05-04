package ax.xz.max.pvpgames.arena;

import ax.xz.max.pvpgames.schematic.BlockVec3;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

/**
 * A position and rotation expressed as an OFFSET from the arena's allocated
 * origin in the shared arenas world. Translation to a world-absolute
 * {@link Location} is the session's job; the session knows the origin handed
 * out by the arena allocator and uses
 * {@link #toWorldLocation(World, BlockVec3)} to produce a teleport target.
 *
 * <p>Coordinates are stored as offsets so the same arena definition can be
 * pasted at any allocated grid slot without rewriting its spawn list. This is
 * compatible with the pre-rewrite YAML format because old per-session worlds
 * pasted their schematic at {@code (0, 0, 0)}; the previously absolute spawn
 * coordinates therefore happen to already be valid offsets under the new
 * scheme.
 */
public record SpawnPoint(double x, double y, double z, float yaw, float pitch) {

    /**
     * Captures position and rotation from {@code worldLocation}, expressed
     * relative to {@code origin}. The location's world is ignored.
     */
    public static SpawnPoint relativeTo(Location worldLocation, BlockVec3 origin) {
        Objects.requireNonNull(worldLocation, "worldLocation");
        Objects.requireNonNull(origin, "origin");
        return new SpawnPoint(
                worldLocation.getX() - origin.x(),
                worldLocation.getY() - origin.y(),
                worldLocation.getZ() - origin.z(),
                worldLocation.getYaw(),
                worldLocation.getPitch());
    }

    /**
     * Builds a world-absolute {@link Location} by adding {@code origin} to
     * this offset.
     */
    public Location toWorldLocation(World world, BlockVec3 origin) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(origin, "origin");
        return new Location(world,
                x + origin.x(),
                y + origin.y(),
                z + origin.z(),
                yaw, pitch);
    }
}
