package ax.xz.max.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/**
 * Per-click data passed to {@link GuiTile#onClick}. Pure data record; the
 * underlying Bukkit event has already been canceled by the time a tile's
 * click handler runs, so the handler never has to deal with it.
 *
 * @param player    the player who clicked; same as {@code gui.viewer()}
 * @param gui       the session that received the click
 * @param slot      the slot in the GUI's inventory that was clicked (raw
 *                  slot in the top inventory)
 * @param clickType Bukkit's classification of the click (LEFT, RIGHT,
 *                  SHIFT_LEFT, NUMBER_KEY_n, etc.); useful for handlers
 *                  that distinguish e.g. "right-click to remove, left-click
 *                  to add"
 */
public record ClickContext(
        Player player,
        GuiSession gui,
        int slot,
        ClickType clickType
) {}
