package ax.xz.max.pvpgames.schematic;

/**
 * Failure modes returned by {@link SchematicService#pasteAtOrigin} on the
 * error side of its {@link ax.xz.max.async.Result Result}. Sealed so callers
 * can pattern-match exhaustively.
 */
public sealed interface SchematicError {

    /** Human-readable description of the failure; safe to show admins. */
    String message();

    /** No file matched the requested schematic name. */
    record NotFound(String message) implements SchematicError {}

    /** A file was found but could not be parsed or pasted. */
    record LoadFailed(String message) implements SchematicError {}
}
