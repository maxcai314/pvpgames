package ax.xz.max.pvpgames.schematic;

/**
 * Thrown by {@link SchematicService} when a schematic cannot be loaded or
 * pasted, or when the underlying schematic plugin is missing.
 *
 * <p>Subtypes encode "kind of failure" so the calling layer can map each to a
 * distinct player-facing message without parsing exception strings.
 */
public class SchematicException extends Exception {

    public SchematicException(String message) {
        super(message);
    }

    public SchematicException(String message, Throwable cause) {
        super(message, cause);
    }

    /** No file matches the requested schematic name. */
    public static final class NotFound extends SchematicException {
        public NotFound(String message) { super(message); }
    }

    /** A file was found but could not be parsed or pasted. */
    public static final class LoadFailed extends SchematicException {
        public LoadFailed(String message, Throwable cause) { super(message, cause); }
    }
}
