package ax.xz.max.gui.simple;

import ax.xz.max.async.GameScheduler;
import ax.xz.max.gui.GuiService;
import ax.xz.max.gui.GuiSession;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Opinionated {@link GuiSession} subclass for "button menu" style GUIs:
 * the inventory contents are decorative, clicks are dispatched to a
 * per-slot {@link GuiTile}, and the player can never move items in or out.
 *
 * <p>Enforces the "no item glitches" invariant by canceling every click,
 * drag, and hand-swap event, then dispatches clicks to whatever
 * {@link GuiTile} the subclass has placed at the clicked slot. Subclasses
 * interact through {@link #set(int, GuiTile)} / {@link #clear(int)} /
 * {@link #refresh()}; the event overrides are {@code final} so subclasses
 * cannot weaken the invariant.
 *
 * <p>Use cases that need item movement (sortable strips, paintbrush
 * inventories, player-editable slots, etc.) should subclass
 * {@link GuiSession} directly instead.
 */
public abstract class SimpleGuiSession extends GuiSession {

    private final Map<Integer, GuiTile> tiles = new HashMap<>();

    protected SimpleGuiSession(Player viewer, GameScheduler scheduler, GuiService guiService,
                               Component title, int rows) {
        super(viewer, scheduler, guiService, title, rows);
    }

    /**
     * Place a tile at {@code slot}. Updates the tile map and immediately
     * paints the tile's current icon into the live inventory.
     */
    public final void set(int slot, GuiTile tile) {
        Objects.requireNonNull(tile, "tile");
        checkSlot(slot);
        tiles.put(slot, tile);
        inventory().setItem(slot, tile.icon());
    }

    /** Remove the tile at {@code slot}, leaving the inventory slot empty. */
    public final void clear(int slot) {
        checkSlot(slot);
        tiles.remove(slot);
        inventory().setItem(slot, null);
    }

    /**
     * Re-paint every slot from its current {@link GuiTile#icon()}. Useful
     * when tile state has changed and the icon needs to update without
     * replacing the tile itself.
     */
    public final void refresh() {
        for (Map.Entry<Integer, GuiTile> e : tiles.entrySet()) {
            inventory().setItem(e.getKey(), e.getValue().icon());
        }
    }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= inventory().getSize()) {
            throw new IndexOutOfBoundsException(
                    "slot " + slot + " out of range [0, " + inventory().getSize() + ").");
        }
    }

    // ---- locked event hooks (button-menu invariant) ----

    /**
     * Cancels the click unconditionally and, if it landed in the GUI's top
     * inventory, dispatches to the {@link GuiTile} at that slot (if any).
     */
    @Override
    protected final void onClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < inventory().getSize()) {
            GuiTile tile = tiles.get(rawSlot);
            if (tile != null) {
                tile.onClick(new ClickContext(
                        (Player) event.getWhoClicked(), this, rawSlot, event.getClick()));
            }
        }
    }

    /** Cancels every drag in this inventory. */
    @Override
    protected final void onDrag(InventoryDragEvent event) {
        event.setCancelled(true);
    }

    /** Cancels F-key main/off-hand swap while this session is open. */
    @Override
    protected final void onSwap(PlayerSwapHandItemsEvent event) {
        event.setCancelled(true);
    }
}
