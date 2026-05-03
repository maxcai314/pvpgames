package ax.xz.max.pvpgames.naming;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Shared validation helper functions for filesystem-safe resource identifiers (kit names,
 * arena names, etc.).
 *
 * <p>Names are case-insensitive and lowercased on validation. The pattern
 * forbids any character that has filesystem meaning, so a validated name is
 * always safe to use as a path component.
 */
public final class ResourceNames {

    /** Lowercase ASCII alphanumerics, underscores, and hyphens; 1–32 chars. */
    public static final Pattern PATTERN = Pattern.compile("[a-z0-9][a-z0-9_\\-]{0,31}");

    /** Maximum length, in characters, of a valid resource name. */
    public static final int MAX_LENGTH = 32;

    private ResourceNames() {}

    /**
     * Validates {@code raw} as a resource name and returns the canonical
     * (lowercased) form.
     *
     * @param raw       the raw user input
     * @param typeLabel a short human-readable label like {@code "Kit"} or
     *                  {@code "Arena"} used to prefix the rejection message
     * @return the lowercased, validated form of {@code raw}
     * @throws IllegalArgumentException if {@code raw} fails validation
     */
    public static String validate(String raw, String typeLabel) {
        Objects.requireNonNull(raw, "raw");
        Objects.requireNonNull(typeLabel, "typeLabel");
        String lower = raw.toLowerCase(Locale.ROOT);
        if (!PATTERN.matcher(lower).matches()) {
            throw new IllegalArgumentException(rejectionMessage(typeLabel, lower));
        }
        return lower;
    }

    private static String rejectionMessage(String typeLabel, String value) {
        if (value.length() > MAX_LENGTH) {
            return typeLabel + " name too long: max " + MAX_LENGTH + " characters.";
        }
        return typeLabel + " name must use only lowercase letters, digits, '_' or '-', " +
                "starting with a letter or digit (got '" + value + "').";
    }
}
