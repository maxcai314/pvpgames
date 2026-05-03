package ax.xz.max.pvpgames.kit;

import ax.xz.max.pvpgames.command.NameParseResult;
import ax.xz.max.pvpgames.naming.ResourceNames;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A validated, filesystem-safe kit identifier.
 *
 * <p>Names are case-insensitive: {@code "Diamond"} and {@code "diamond"} resolve
 * to the same kit. Validation is delegated to
 * {@link ResourceNames#validate(String, String)} so the regex is shared with
 * other named resources (arenas, etc.).
 */
public record KitName(String value) {

    public KitName {
        value = ResourceNames.validate(value, "Kit");
    }

    /**
     * Attempts to construct a {@link KitName} from raw user input. Returns the
     * reason for rejection in the result for use in player-facing messages.
     */
    public static NameParseResult<KitName> tryParse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return new NameParseResult.Invalid<>("Kit name cannot be empty.");
        }
        try {
            return new NameParseResult.Valid<>(new KitName(raw));
        } catch (IllegalArgumentException ex) {
            return new NameParseResult.Invalid<>(ex.getMessage());
        }
    }

    /**
     * The on-disk file path for this kit inside {@code kitsDir}. Because the
     * shared name pattern excludes path separators and dots, the resulting path
     * is always a direct child of {@code kitsDir}.
     */
    public Path resolveFile(Path kitsDir, String extension) {
        Objects.requireNonNull(kitsDir, "kitsDir");
        Objects.requireNonNull(extension, "extension");
        return kitsDir.resolve(value + extension);
    }
}
