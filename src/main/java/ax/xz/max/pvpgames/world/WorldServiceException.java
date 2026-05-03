package ax.xz.max.pvpgames.world;

/**
 * Thrown by {@link WorldService} implementations when a world cannot be created
 * or deleted, or when the underlying world-management plugin is missing.
 *
 * <p>Checked because callers (the arena service) need to map every failure mode
 * to a player-facing message; unchecked propagation would skip that mapping.
 */
public class WorldServiceException extends Exception {

    public WorldServiceException(String message) {
        super(message);
    }

    public WorldServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
