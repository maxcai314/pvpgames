package ax.xz.max.pvpgames.kit;

/**
 * Thrown by {@link KitRepository} implementations when an I/O or serialization
 * failure prevents a kit operation from completing durably.
 *
 * <p>Checked because the repository is a system boundary where I/O failures are
 * an expected outcome and the calling layer must decide how to surface them
 * (typically by mapping to a {@link KitResult.IoFailure} variant). Programming
 * bugs and fully unrecoverable conditions should still surface as unchecked
 * exceptions.
 */
public class KitPersistenceException extends Exception {

    public KitPersistenceException(String message) {
        super(message);
    }

    public KitPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
