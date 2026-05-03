package ax.xz.max.pvpgames.arena.internal;

import ax.xz.max.pvpgames.arena.ArenaSession;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Tracks active {@code /arena preview} sessions, keyed by the previewing
 * admin's {@link UUID}.
 *
 * <p>Implements {@link Listener} so it can clear stale sessions when a player
 * disconnects; the plugin registers it on enable.
 */
public final class ArenaSessionRegistry implements Listener {

    private final ConcurrentMap<UUID, ArenaSession> sessions = new ConcurrentHashMap<>();

    /**
     * Registers a session, replacing any existing entry for the same admin.
     */
    public void put(ArenaSession session) {
        Objects.requireNonNull(session, "session");
        sessions.put(session.admin(), session);
    }

    /**
     * Returns the session for {@code admin}, if any.
     */
    public Optional<ArenaSession> get(UUID admin) {
        Objects.requireNonNull(admin, "admin");
        return Optional.ofNullable(sessions.get(admin));
    }

    /**
     * Removes and returns the session for {@code admin}, if any.
     */
    public Optional<ArenaSession> remove(UUID admin) {
        Objects.requireNonNull(admin, "admin");
        return Optional.ofNullable(sessions.remove(admin));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }
}
