package ax.xz.max.pvpgames.arena.internal;

import ax.xz.max.pvpgames.arena.ArenaService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;

/**
 * Bukkit listener that forces a session restore when a player disconnects
 * while inside an arena session.
 *
 * <p>Without this, the server's auto-save on quit would persist the player's
 * <i>in-session</i> state (cleared inventory, arena world, etc.) into their
 * {@code .dat} file. On reconnect they would respawn inside the arena world
 * with no session record to /arena leave from. By running
 * {@link ArenaService#leave(org.bukkit.entity.Player) ArenaService#leave}
 * during the quit event we restore them first, so what gets saved is their
 * original world / location / inventory / health.
 */
public final class ArenaSessionListener implements Listener {

    private final ArenaService service;

    public ArenaSessionListener(ArenaService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Returns NoActiveSession when the player wasn't in a session, which
        // is the harmless no-op case; we don't care about the result.
        service.leave(event.getPlayer());
    }
}
