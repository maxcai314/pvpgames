package ax.xz.max.pvpgames.arena;

import org.bukkit.GameMode;
import org.bukkit.Location;

import java.util.Objects;
import java.util.UUID;

/**
 * Per-admin record of an active {@code /arena preview} session.
 *
 * <p>The session captures the state we need to restore when the admin runs
 * {@code /arena leave}: where they were before the preview and what gamemode
 * they had. The arena identity is stored alongside as a convenience; it can
 * also be derived from the player's current world at any time.
 */
public record ArenaSession(
        UUID admin,
        ArenaName arena,
        Location previousLocation,
        GameMode previousGameMode
) {

    public ArenaSession {
        Objects.requireNonNull(admin, "admin");
        Objects.requireNonNull(arena, "arena");
        Objects.requireNonNull(previousLocation, "previousLocation");
        Objects.requireNonNull(previousGameMode, "previousGameMode");
    }
}
