package ax.xz.max.pvpgames.arena;

import ax.xz.max.pvpgames.command.NameParseResult;
import ax.xz.max.pvpgames.naming.ResourceNames;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A validated, filesystem-safe arena identifier.
 *
 * <p>Names are case-insensitive: {@code "Stadium"} and {@code "stadium"} resolve
 * to the same arena. Validation is delegated to
 * {@link ResourceNames#validate(String, String)} so the regex is shared with
 * other named resources (kits, etc.).
 */
public record ArenaName(String value) {

    /**
     * Common prefix for the temporary Multiverse worlds created by
     * {@code /arena preview}. Plugin enable / disable sweeps every loaded world
     * whose name starts with this prefix.
     */
    public static final String WORLD_PREFIX = "pvpgames_arena_";

    public ArenaName {
        value = ResourceNames.validate(value, "Arena");
    }

    /**
     * Attempts to construct an {@link ArenaName} from raw user input. Returns the
     * reason for rejection in the result for use in player-facing messages.
     */
    public static NameParseResult<ArenaName> tryParse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return new NameParseResult.Invalid<>("Arena name cannot be empty.");
        }
        try {
            return new NameParseResult.Valid<>(new ArenaName(raw));
        } catch (IllegalArgumentException ex) {
            return new NameParseResult.Invalid<>(ex.getMessage());
        }
    }

    /**
     * The on-disk file path for this arena inside {@code arenasDir}. Because the
     * shared name pattern excludes path separators and dots, the resulting path
     * is always a direct child of {@code arenasDir}.
     */
    public Path resolveFile(Path arenasDir, String extension) {
        Objects.requireNonNull(arenasDir, "arenasDir");
        Objects.requireNonNull(extension, "extension");
        return arenasDir.resolve(value + extension);
    }

    /**
     * Deterministic Multiverse world name for this arena's preview world.
     */
    public String worldName() {
        return WORLD_PREFIX + value;
    }
}
