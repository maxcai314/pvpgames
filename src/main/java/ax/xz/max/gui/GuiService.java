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
import java.util.Objects;

/**
 * Single global Bukkit listener that routes inventory events to the
 * {@link GuiSession} they affect. Pure routing: this class does NOT cancel
 * events, enforce invariants, or otherwise interpret what events mean.
 * Each session's hook ({@link GuiSession#onClick}, {@link GuiSession#onDrag},
 * {@link GuiSession#onSwap}) decides for itself whether to cancel or allow.
 *
 * <p>Sessions register themselves on {@link GuiSession#open()} via the
 * package-private {@link #register} / {@link #unregister} pair. Routing is
 * keyed on the top inventory of the open view.
 */
public final class GuiService implements Listener {

    final Logger logger;

    // todo: make sure this does not memory leak
    private final Map<Inventory, GuiSession> openSessions = new HashMap<>();

    public GuiService(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.logger = plugin.getSLF4JLogger();
    }

    void register(Inventory inventory, GuiSession session) {
        openSessions.put(inventory, session);
    }

    void unregister(Inventory inventory) {
        openSessions.remove(inventory);
    }

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
        GuiSession session = openSessions.get(event.getInventory());
        if (session != null) {
            session.handleClose();
        }
        // todo: what if the player disconnects?
        // player death?
    }
}
