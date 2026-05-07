package ax.xz.max.pvpgames.arena.internal;

import ax.xz.max.pvpgames.arena.ArenaManager;
import ax.xz.max.pvpgames.arena.ArenaSession;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;
import java.util.Optional;

/**
 * Bukkit listener that forces a session restore when a player disconnects
 * while inside an arena session.
 *
 * <p>Without this, the server's auto-save on quit would persist the player's
 * in-session state (cleared inventory, arenas world, etc.) into their
 * {@code .dat} file. On reconnect they would respawn inside the arenas world
 * with no session to {@code /arena leave} from. By calling
 * {@link ArenaSession#leavePlayer(Player)} during the quit event we restore
 * them first, so what gets saved is their original world / location /
 * inventory / health.
 *
 * todo: should be created by DefaultArenaManager's constructor, should be an inner private class
 */
public final class ArenaSessionListener implements Listener {

    private final ArenaManager manager;

    public ArenaSessionListener(ArenaManager manager) {
        this.manager = Objects.requireNonNull(manager, "manager");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Optional<ArenaSession> session = manager.findSessionFor(player);
        // Returns NoActiveSession when the player has no cached state, which
        // is the harmless no-op case; we don't care about the result.
        session.ifPresent(s -> s.leavePlayer(player));
    }
}
