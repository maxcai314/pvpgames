package ax.xz.max.pvpgames.arena;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

/**
 * A position and rotation in an arena world where a player can be spawned at the
 * start of a game.
 *
 * <p>The world a spawn belongs to is implicit: spawns live inside an
 * {@link Arena}, and the arena's preview world is determined by
 * {@link ArenaName#worldName()}. We therefore store only the geometric
 * coordinates and matrix the world in at use-time via {@link #toLocation(World)}.
 */
public record SpawnPoint(double x, double y, double z, float yaw, float pitch) {

    /**
     * Captures position and rotation from a Bukkit {@link Location}. The
     * location's world is ignored.
     */
    public static SpawnPoint of(Location location) {
        Objects.requireNonNull(location, "location");
        return new SpawnPoint(
                location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
    }

    /**
     * Builds a {@link Location} for this spawn in the given {@link World}.
     */
    public Location toLocation(World world) {
        Objects.requireNonNull(world, "world");
        return new Location(world, x, y, z, yaw, pitch);
    }
}
