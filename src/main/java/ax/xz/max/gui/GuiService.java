package ax.xz.max.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Single global Bukkit listener that routes inventory events to the
 * matching {@link GuiSession}. One instance is constructed during plugin
 * enable; every {@link GuiSession} receives it via constructor injection.
 *
 * <p>The service owns the {@code Map<Inventory, GuiSession>} that backs
 * routing. Sessions register themselves on {@link GuiSession#open()} and
 * unregister on close.
 *
 * <p>This is also where the "no item glitches" invariant is enforced. The
 * listener cancels every click, drag, and hand-swap that happens while a
 * session is open, regardless of whether the click landed in the GUI's top
 * inventory or in the player's bottom inventory; otherwise shift-clicking
 * from the player inventory could leak items into the GUI.
 */
public final class GuiService implements Listener {

    // todo: make sure this does not memory leak
    private final Map<Inventory, GuiSession> openSessions = new HashMap<>();

    public GuiService(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    void register(Inventory inventory, GuiSession session) {
        openSessions.put(inventory, session);
    }

    void unregister(Inventory inventory) {
        openSessions.remove(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        GuiSession session = openSessions.get(top);
        if (session == null) return;

        // Cancel every click while a session is open. This blocks
        // shift-clicks, hotbar swaps, double-click collect, etc. from
        // moving items between the player inventory and the GUI inventory.
        event.setCancelled(true);

        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < top.getSize()) {
            // Click landed in the GUI inventory; dispatch to the tile.
            session.handleClick(rawSlot, event.getClick(), (Player) event.getWhoClicked());
        }
        // Clicks in the player's bottom inventory are still cancelled, but
        // there is no tile dispatch for them.
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (openSessions.containsKey(top)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        // F-key swap fires independently of InventoryClickEvent. Cancel it
        // while a GUI is open so the player cannot rearrange items they
        // can see in the bottom rows of the open GUI.
        Inventory top = event.getPlayer().getOpenInventory().getTopInventory();
        if (openSessions.containsKey(top)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        GuiSession session = openSessions.get(event.getInventory());
        if (session != null) {
            session.handleClose();
        }
    }
}
