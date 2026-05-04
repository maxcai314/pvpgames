package ax.xz.max.pvpgames.schematic;

import ax.xz.max.pvpgames.command.NameParseResult;

import java.util.Objects;

/**
 * Validated short name of a schematic file. The canonical constructor rejects
 * names that would let the caller escape the schematics directory or otherwise
 * resolve to something other than a single file inside it: empty strings,
 * names with path separators ({@code /}, {@code \}), and names containing
 * {@code ..}.
 *
 * <p>Once you hold a {@link SchematicName}, you know it is filesystem-safe and
 * you do not need to re-validate the raw string elsewhere. Construct directly
 * from a known-good source, or use {@link #tryParse(String)} when the input
 * came from a user.
 *
 * <p>Names are case-sensitive: a schematic file's case must match the
 * filesystem (the name is just a filename hint).
 */
public record SchematicName(String value) {

    public SchematicName {
        Objects.requireNonNull(value, "value");
        // todo: use less suspicious validation logic
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Schematic name cannot be empty.");
        }
        if (value.indexOf('/') >= 0 || value.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Schematic name cannot contain path separators.");
        }
        if (value.contains("..")) {
            throw new IllegalArgumentException("Schematic name cannot contain '..'.");
        }
    }

    /**
     * Attempts to construct a {@link SchematicName} from raw user input.
     * Returns the rejection reason in the result for use in player-facing
     * messages.
     */
    public static NameParseResult<SchematicName> tryParse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return new NameParseResult.Invalid<>("Schematic name cannot be empty.");
        }
        try {
            return new NameParseResult.Valid<>(new SchematicName(raw));
        } catch (IllegalArgumentException ex) {
            return new NameParseResult.Invalid<>(ex.getMessage());
        }
    }
}
