package ax.xz.max.pvpgames.arena;

/**
 * Thrown by {@link ArenaRepository} implementations when an I/O or serialization
 * failure prevents an arena operation from completing durably.
 *
 * <p>Checked because the repository is a system boundary where I/O failures are
 * an expected outcome and the calling layer must decide how to surface them
 * (typically by mapping to an {@link ArenaResult.IoFailure} variant). Programming
 * bugs and fully unrecoverable conditions should still surface as unchecked
 * exceptions.
 */
public class ArenaPersistenceException extends Exception {

    public ArenaPersistenceException(String message) {
        super(message);
    }

    public ArenaPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
