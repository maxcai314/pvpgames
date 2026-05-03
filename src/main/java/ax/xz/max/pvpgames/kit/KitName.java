package ax.xz.max.pvpgames.kit;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A validated, filesystem-safe kit identifier.
 *
 * <p>Names are case-insensitive: {@code "Diamond"} and {@code "diamond"} resolve
 * to the same kit. The canonical constructor lowercases the input and rejects
 * anything that does not match {@link #PATTERN}, which closes path-traversal at
 * the type boundary; a {@code KitName} cannot hold {@code ../foo} or any other
 * filesystem-significant character.
 */
public record KitName(String value) {

    /** Matches lowercase ASCII alphanumerics, underscores, and hyphens; 1–32 chars. */
    public static final Pattern PATTERN = Pattern.compile("[a-z0-9][a-z0-9_\\-]{0,31}");

    /** Maximum length, in characters, of a valid kit name. */
    public static final int MAX_LENGTH = 32;

    public KitName {
        Objects.requireNonNull(value, "value");
        value = value.toLowerCase(Locale.ROOT);
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(rejectionReason(value));
        }
    }

    /**
     * Attempts to construct a {@link KitName} from raw user input. Returns the
     * reason for rejection in the result for use in player-facing messages.
     */
    public static Result tryParse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return new Result.Invalid("Kit name cannot be empty.");
        }
        try {
            return new Result.Valid(new KitName(raw));
        } catch (IllegalArgumentException ex) {
            return new Result.Invalid(ex.getMessage());
        }
    }

    /**
     * The on-disk file path for this kit inside {@code kitsDir}. Because
     * {@link #PATTERN} excludes path separators and dots, the resulting path is
     * always a direct child of {@code kitsDir}.
     */
    public Path resolveFile(Path kitsDir, String extension) {
        Objects.requireNonNull(kitsDir, "kitsDir");
        Objects.requireNonNull(extension, "extension");
        return kitsDir.resolve(value + extension);
    }

    private static String rejectionReason(String value) {
        if (value.length() > MAX_LENGTH) {
            return "Kit name too long: max " + MAX_LENGTH + " characters.";
        }
        return "Kit name must use only lowercase letters, digits, '_' or '-', " +
                "starting with a letter or digit (got '" + value + "').";
    }

    /** Outcome of {@link KitName#tryParse(String)}. */
    public sealed interface Result {
        record Valid(KitName name) implements Result {}
        record Invalid(String reason) implements Result {}
    }
}
