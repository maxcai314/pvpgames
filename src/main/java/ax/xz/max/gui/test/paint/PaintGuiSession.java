package ax.xz.max.gui.test.paint;

import ax.xz.max.async.GameScheduler;
import ax.xz.max.gui.GuiService;
import ax.xz.max.gui.GuiSession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Free-form paint canvas built directly on {@link GuiSession} to exercise
 * the layer-1 abstraction (cursor-carrying clicks, item flow in and out
 * of slots, custom drag and swap policy).
 *
 * <p>Layout (6 rows / 54 slots):
 * <pre>
 * rows 0..4 -- black-pane frame around a 3x7 canvas (slots 10..16, 19..25, 28..34)
 * row 5     -- palette: 8 stained-glass colors (45..52), barrier eraser (53)
 * </pre>
 *
 * <p>Click semantics:
 * <ul>
 *   <li>Palette click: copies the picked item onto the player's cursor.</li>
 *   <li>Canvas click with a paint color on cursor: paints the slot and
 *       leaves the cursor untouched, so the brush is sticky.</li>
 *   <li>Canvas click with the eraser on cursor: clears the slot.</li>
 *   <li>Everything else (drags, hand-swap, drops, shift / number-key /
 *       double-click variants, player-inventory clicks): canceled.</li>
 * </ul>
 *
 * <p>On close the cursor is wiped before NMS can restore it to the
 * viewer's inventory, and the final color histogram is snapshotted into
 * {@link #histogram} for the close-promise consumer.
 */
final class PaintGuiSession extends GuiSession {

    private static final int ROWS = 6;
    private static final int SIZE = ROWS * 9;
    private static final int PALETTE_START = 45;
    private static final int ERASER_SLOT = 53;

    private static final Material[] PALETTE_COLORS = {
            Material.RED_STAINED_GLASS_PANE,
            Material.ORANGE_STAINED_GLASS_PANE,
            Material.YELLOW_STAINED_GLASS_PANE,
            Material.GREEN_STAINED_GLASS_PANE,
            Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            Material.BLUE_STAINED_GLASS_PANE,
            Material.PURPLE_STAINED_GLASS_PANE,
            Material.WHITE_STAINED_GLASS_PANE,
    };

    private static final Set<Integer> CANVAS_SLOTS = Set.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    );

    private static final Set<Material> PAINT_MATERIALS = Set.of(PALETTE_COLORS);

    private Map<Material, Integer> histogram = Map.of();

    PaintGuiSession(Player viewer, GameScheduler scheduler, GuiService guiService) {
        super(viewer, scheduler, guiService, Component.text("Paint"), ROWS);

        // Frame: every non-canvas, non-palette slot.
        for (int slot = 0; slot < PALETTE_START; slot++) {
            if (CANVAS_SLOTS.contains(slot)) continue;
            inventory().setItem(slot, new ItemStack(Material.BLACK_STAINED_GLASS_PANE));
        }

        // Palette: 8 colors then the eraser.
        for (int i = 0; i < PALETTE_COLORS.length; i++) {
            Material color = PALETTE_COLORS[i];
            inventory().setItem(PALETTE_START + i,
                    named(new ItemStack(color), prettyName(color), "Click to pick this color"));
        }
        inventory().setItem(ERASER_SLOT,
                named(new ItemStack(Material.BARRIER), "Eraser",
                        "Click then tap a canvas tile to clear it"));
    }

    /**
     * Color histogram over the canvas at the moment the session closed.
     * Empty until {@link #onClose()} runs.
     */
    public Map<Material, Integer> histogram() { return histogram; }

    @Override
    protected void onClick(InventoryClickEvent event) {
        event.setCancelled(true);
        ClickType type = event.getClick();
        if (type != ClickType.LEFT && type != ClickType.RIGHT) return;

        int raw = event.getRawSlot();
        if (raw < 0 || raw >= SIZE) return;     // player inventory or OUTSIDE
        if (isFrame(raw)) return;

        InventoryView view = event.getView();
        if (raw >= PALETTE_START) {
            ItemStack picked = inventory().getItem(raw);
            if (picked != null) {
                ItemStack onCursor = picked.clone();
                onCursor.setAmount(1);
                view.setCursor(onCursor);
            }
            return;
        }

        // canvas
        ItemStack cursor = view.getCursor();
        if (cursor == null || cursor.getType() == Material.AIR) return;
        if (cursor.getType() == Material.BARRIER) {
            inventory().setItem(raw, null);
        } else if (PAINT_MATERIALS.contains(cursor.getType())) {
            ItemStack copy = cursor.clone();
            copy.setAmount(1);
            inventory().setItem(raw, copy);
        }
        // cursor is left untouched: the brush is sticky.
    }

    @Override
    protected void onDrag(InventoryDragEvent event) {
        event.setCancelled(true);
    }

    @Override
    protected void onSwap(PlayerSwapHandItemsEvent event) {
        event.setCancelled(true);
    }

    @Override
    protected void onClose() {
        // InventoryCloseEvent fires before NMS restores the carried item,
        // so wiping the cursor here keeps the brush out of the viewer's
        // inventory and off the ground.
        viewer.getOpenInventory().setCursor(null);
        histogram = buildHistogram();
    }

    private Map<Material, Integer> buildHistogram() {
        EnumMap<Material, Integer> counts = new EnumMap<>(Material.class);
        for (int slot : CANVAS_SLOTS) {
            ItemStack item = inventory().getItem(slot);
            if (item == null || item.getType() == Material.AIR) continue;
            counts.merge(item.getType(), 1, Integer::sum);
        }
        return counts;
    }

    private static boolean isFrame(int slot) {
        return slot < PALETTE_START && !CANVAS_SLOTS.contains(slot);
    }

    /** "RED_STAINED_GLASS_PANE" -> "Red". */
    private static String prettyName(Material color) {
        String stripped = color.name().replace("_STAINED_GLASS_PANE", "");
        StringBuilder sb = new StringBuilder(stripped.length());
        boolean upper = true;
        for (char c : stripped.toCharArray()) {
            if (c == '_') {
                sb.append(' ');
                upper = true;
            } else if (upper) {
                sb.append(Character.toUpperCase(c));
                upper = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    private static ItemStack named(ItemStack item, String displayName, String... lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(displayName).color(NamedTextColor.AQUA));
            if (lore.length > 0) {
                List<Component> loreLines = new ArrayList<>(lore.length);
                for (String line : lore) {
                    loreLines.add(Component.text(line, NamedTextColor.GRAY));
                }
                meta.lore(loreLines);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
