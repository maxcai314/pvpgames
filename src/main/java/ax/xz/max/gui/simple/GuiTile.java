package ax.xz.max.gui.simple;

import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * The contents of one slot in a {@link GuiSession}: an {@link ItemStack}
 * shown to the viewer and an optional click handler. Subclasses are free to
 * carry mutable state and return different items from {@link #icon()} over
 * time; the surrounding session repaints icons whenever
 * {@link GuiSession#refresh()} or {@link GuiSession#set} is invoked.
 *
 * <p>The default {@link #onClick(ClickContext)} is a no-op, so a tile that
 * is only meant as decoration (e.g. a glass-pane filler) does not need to
 * override anything but {@link #icon()}. Click events on every tile are
 * unconditionally canceled by the framework regardless of what the
 * subclass does, so tiles never leak items in or out of the inventory.
 */
public abstract class GuiTile {

    /**
     * The {@link ItemStack} this tile currently displays. Called every time
     * the slot is painted, so a tile with internal mutable state can return
     * different items as that state changes.
     */
    public abstract ItemStack icon();

    /** Default no-op. Subclasses override to react to clicks. */
    public void onClick(ClickContext ctx) {}

    /**
     * Convenience factory for simple tiles whose icon does not change over
     * time. The same {@link ItemStack} reference is returned on every paint;
     * if you need a per-paint copy, override {@link GuiTile} instead.
     */
    public static GuiTile of(ItemStack icon, Consumer<ClickContext> onClick) {
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(onClick, "onClick");
        return new GuiTile() {
            @Override public ItemStack icon() { return icon; }
            @Override public void onClick(ClickContext ctx) { onClick.accept(ctx); }
        };
    }

    /** Convenience factory for an inert decoration tile (no click handler). */
    public static GuiTile decoration(ItemStack icon) {
        Objects.requireNonNull(icon, "icon");
        return new GuiTile() {
            @Override public ItemStack icon() { return icon; }
        };
    }
}
