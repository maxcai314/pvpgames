package ax.xz.max.gui;

import ax.xz.max.async.GameExecutor;
import ax.xz.max.async.GameScheduler;
import ax.xz.max.async.Promise;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Abstract base for a chest-inventory GUI bound to one player for one open
 * cycle.
 *
 * <p>The base class is intentionally policy-free. It manages the session
 * lifecycle (open --> events --> close), owns the inventory, and exposes the
 * inventory events Bukkit fires during the session via three protected
 * hooks. Subclasses decide what those events mean: whether to cancel them,
 * whether to allow item movement, whether to translate clicks into
 * higher-level concepts. {@link ax.xz.max.gui.simple.SimpleGuiSession} is
 * one such opinionated subclass (default-deny clicks, tile-based dispatch);
 * other subclasses can implement sortable layouts, paintbrush behaviors,
 * editable inputs, or whatever else.
 *
 * <p>The base constructor never calls a subclass-overridable method, so
 * subclasses can initialize their own fields and touch the inventory via
 * {@link #inventory()} immediately after {@code super(...)} without
 * uninitialised-this hazards.
 */
public abstract class GuiSession {

    protected final Player viewer;
    private final GameScheduler scheduler;
    private final GuiService guiService;

    private final Inventory inventory;
    private final List<Runnable> closeCallbacks = new ArrayList<>();

    private boolean opened = false;
    private boolean closed = false;
    private boolean forceClose = false;

    protected GuiSession(Player viewer, GameScheduler scheduler, GuiService guiService,
                         Component title, int rows) {
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.guiService = Objects.requireNonNull(guiService, "guiService");
        Objects.requireNonNull(title, "title");
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be in [1, 6], got " + rows);
        }
        this.inventory = Bukkit.createInventory(null, rows * 9, title);
    }

    // ---- inventory access (subclasses mutate items here) ----

    /**
     * The underlying inventory. Subclasses use this to set / read items
     * directly; the framework imposes no structure on slot contents.
     */
    protected final Inventory inventory() { return inventory; }

    // ---- event hooks (default no-op; subclasses override) ----

    /**
     * Called for every click in the GUI inventory or in the player's
     * inventory while this session is the top inventory. The Bukkit event
     * is NOT canceled by the framework; subclasses must call
     * {@code event.setCanceled(true)} if they want to block the click.
     */
    protected void onClick(InventoryClickEvent event) {}

    /**
     * Called for every drag that touches the GUI inventory. Not canceled
     * by the framework; subclasses are responsible.
     */
    protected void onDrag(InventoryDragEvent event) {}

    /**
     * Called when the viewer presses the swap-hands key (F by default)
     * while this session is open. Default no-op so the swap proceeds
     * normally; override and cancel to block.
     */
    protected void onSwap(PlayerSwapHandItemsEvent event) {}

    // ---- lifecycle hooks (subclasses override) ----

    /** Runs once after the inventory is shown to the viewer. */
    protected void onOpen() {}

    /** Runs once on close, before close callbacks fire. */
    protected void onClose() {}

    /**
     * Returns false to prevent the player from closing the GUI themselves
     * (E key, mouse outside, etc.). The framework re-opens the inventory
     * on the next tick when this returns false. Has no effect on explicit
     * {@link #forceClose()} calls; those always go through. Default true.
     */
    protected boolean playerCanClose() { return true; }

    // ---- session lifecycle ----

    /** The player this session is bound to. */
    public final Player viewer() { return viewer; }

    /** True once the session has closed. */
    public final boolean isClosed() { return closed; }

    /**
     * Show the inventory to the bound viewer.
     *
     * @throws IllegalStateException if already opened or closed
     */
    public final void open() {
        if (closed) throw new IllegalStateException("GuiSession is already closed.");
        if (opened) throw new IllegalStateException("GuiSession is already open.");
        opened = true;
        guiService.register(inventory, this);
        viewer.openInventory(inventory);
        onOpen();
    }

    /**
     * Force-close the inventory; bypasses {@link #playerCanClose()}.
     * Idempotent: a no-op once closed.
     */
    public final void forceClose() {
        if (closed || !opened) return;
        forceClose = true;
        viewer.closeInventory();   // fires InventoryCloseEvent --> handleClose()
    }

    // ---- close subscription ----

    /**
     * GameExecutor that schedules each submitted task to run on the main
     * thread the moment this session closes. If already closed, the task
     * is scheduled for the next main-thread tick.
     */
    public final GameExecutor whenClosedExecutor() {
        return this::registerCloseCallback;
    }

    /**
     * A {@code Promise<Void>} that completes when this session closes.
     * Named to avoid confusion with the {@link #isClosed()} state predicate.
     */
    public final Promise<Void> whenClosedPromise() {
        return Promise.runAsync(() -> {}, whenClosedExecutor());
    }

    private void registerCloseCallback(Runnable callback) {
        Objects.requireNonNull(callback, "callback");
        if (closed) {
            scheduler.mainExecutor().execute(callback);
        } else {
            closeCallbacks.add(callback);
        }
    }

    // ---- package-private hooks driven by GuiService ----

    /** Routes a click event to the subclass hook. Called only by {@link GuiService}. */
    void dispatchClick(InventoryClickEvent event) {
        if (closed) return;
        onClick(event);
    }

    /** Routes a drag event to the subclass hook. */
    void dispatchDrag(InventoryDragEvent event) {
        if (closed) return;
        onDrag(event);
    }

    /** Routes a hand-swap event to the subclass hook. */
    void dispatchSwap(PlayerSwapHandItemsEvent event) {
        if (closed) return;
        onSwap(event);
    }

    /**
     * Internal method called by the listener on {@code InventoryCloseEvent}.
     * Honors the {@link #playerCanClose()} veto by re-opening on the next tick.
     */
    void handleClose() {
        if (closed) return;
        if (forceClose || playerCanClose()) {
            // allow the close, clean up
            finishClose();
            return;
        }

        // otherwise, reopen on next tick
        scheduler.mainExecutor().execute(() -> {
            if (!closed && viewer.isOnline()) {
                viewer.openInventory(inventory);
            }
        });
    }

    private void finishClose() {
        closed = true;
        try {
            onClose();
        } catch (RuntimeException ex) {
            guiService.logger.error("Exception during GuiSession onClose", ex);
        }
        for (Runnable cb : closeCallbacks) {
            try {
                cb.run();
            } catch (RuntimeException ex) {
                guiService.logger.error("Exception during GuiSession close callback", ex);
            }
        }
        closeCallbacks.clear();
        guiService.unregister(inventory);
    }
}
