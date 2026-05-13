package ax.xz.max.pvpgames.arena;

import ax.xz.max.async.Result;
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
     * Name of the shared Bukkit-managed void world that hosts every
     * active arena session. Created at plugin enable and deleted at
     * plugin disable; sessions are pasted into this world at
     * non-overlapping origins handed out by the arena allocator.
     */
    public static final String SHARED_WORLD_NAME = "pvpgames_arenas";

    public ArenaName {
        value = ResourceNames.validate(value, "Arena");
    }

    /**
     * Attempts to construct an {@link ArenaName} from raw user input.
     *
     * @return the parsed name result, or an error message that can be shown
     *         to players
     */
    public static Result<ArenaName, String> tryParse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return new Result.Err<>("Arena name cannot be empty.");
        }
        try {
            return new Result.Ok<>(new ArenaName(raw));
        } catch (IllegalArgumentException ex) {
            return new Result.Err<>(ex.getMessage());
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

}
