package ax.xz.max.pvpgames.arena;

import java.util.Objects;

/**
 * Handle to one of the temporary preview worlds we created. Multiple players
 * can be inside the same session at once: {@code /arena preview} starts a new
 * session and joins, while {@code /arena join <id>} joins an existing one.
 *
 * <p>Sessions live in the {@link ArenaService} session pool from creation
 * until plugin disable. The pool starts empty on plugin enable and is wiped
 * on plugin disable; sessions never persist across server restarts.
 *
 * @param id        a unique identifier within this server run; assigned by
 *                  the service. Players use it as the argument to
 *                  {@code /arena join}
 * @param arena     the arena this session is hosting
 * @param worldName the Bukkit name of the temporary world this session owns
 */
public record ArenaSession(long id, ArenaName arena, String worldName) {

    public ArenaSession {
        Objects.requireNonNull(arena, "arena");
        Objects.requireNonNull(worldName, "worldName");
    }
}
