package ax.xz.max.pvpgames.worldguard;

/**
 * Thrown by {@link WorldGuardService} implementations when a region cannot be
 * created, removed, or have its flags applied; also thrown by the unavailable
 * fallback when WorldGuard is not installed.
 *
 * <p>Checked because callers (the arena manager) need to map every failure
 * mode to a player-facing message; unchecked propagation would skip that
 * mapping. The message is intended to be shown to admins.
 */
public class WorldGuardException extends Exception {

    public WorldGuardException(String message) {
        super(message);
    }

    public WorldGuardException(String message, Throwable cause) {
        super(message, cause);
    }
}
