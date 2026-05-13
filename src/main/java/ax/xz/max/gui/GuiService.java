package ax.xz.max.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Single global Bukkit service that routes inventory events to the
 * {@link GuiSession} they affect. Pure routing: this class does NOT cancel
 * events, enforce invariants, or otherwise interpret what events mean.
 * Each session's hook ({@link GuiSession#onClick}, {@link GuiSession#onDrag},
 * {@link GuiSession#onSwap}) decides for itself whether to cancel or allow.
 *
 * <p>Internally registers a listener that is owned by this object.
 *
 * <p>Sessions register themselves on {@link GuiSession#open()} via the
 * package-private {@link #register} / {@link #unregister} pair. Routing is
 * keyed on the top inventory of the open view.
 */
public final class GuiService {

    final Logger logger;

    // todo: make sure this does not memory leak
    private final Map<Inventory, GuiSession> openSessions = new HashMap<>();

    public GuiService(Plugin plugin) {
        this.logger = plugin.getSLF4JLogger();

        GuiServiceListener listener = new GuiServiceListener();
        this.logger.info("Registering event listener for GuiServiceListener");
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    void register(Inventory inventory, GuiSession session) {
        logger.debug("Registering GuiSession {}", System.identityHashCode(session));
        openSessions.put(inventory, session);
    }

    void unregister(Inventory inventory, GuiSession session) {
        openSessions.remove(inventory);
        logger.debug("Unregistered GuiSession {}", System.identityHashCode(session));
    }

    /** listener used by this GuiService */
    private class GuiServiceListener implements Listener {

        @EventHandler
        public void onClick(InventoryClickEvent event) {
            GuiSession session = openSessions.get(event.getView().getTopInventory());
            if (session != null) session.dispatchClick(event);
        }

        @EventHandler
        public void onDrag(InventoryDragEvent event) {
            GuiSession session = openSessions.get(event.getView().getTopInventory());
            if (session != null) session.dispatchDrag(event);
        }

        @EventHandler
        public void onSwap(PlayerSwapHandItemsEvent event) {
            Inventory top = event.getPlayer().getOpenInventory().getTopInventory();
            GuiSession session = openSessions.get(top);
            if (session != null) session.dispatchSwap(event);
        }

        @EventHandler
        public void onClose(InventoryCloseEvent event) {
            logger.debug("Received inventory close event for player {} (reason={})",
                    event.getPlayer().getName(), event.getReason());
            GuiSession session = openSessions.get(event.getInventory());
            if (session != null) {
                logger.debug("Handling inventory close event for GuiSession {}", System.identityHashCode(session));
                session.handleClose(event.getReason());
            }
        }
    }
}
