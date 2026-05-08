package ax.xz.max.gui;

import ax.xz.max.async.GameExecutor;
import ax.xz.max.async.GameScheduler;
import ax.xz.max.async.Promise;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Abstract base class for a chest-inventory GUI bound to one player for one
 * open cycle.
 *
 * <p>One instance represents one session: construct it, optionally configure
 * tiles, call {@link #open()}, the player interacts, the session ends when
 * either the player closes the inventory or {@link #close()} is called.
 * After close, the instance is terminal; a new session means a new instance.
 *
 * <p>The framework enforces "GUI invariants" by cancelling every click /
 * drag / hand-swap event in this inventory, so subclasses cannot accidentally
 * leak items in or out. Interaction happens entirely through
 * {@link GuiTile#onClick}.
 *
 * <p>The base constructor never calls a subclass-overridable method, so
 * subclasses can populate tiles via {@link #set} immediately after
 * {@code super(...)} returns without uninitialised-this hazards.
 */
public abstract class GuiSession {

    protected final Player viewer;
    private final GameScheduler scheduler;
    private final GuiService guiService;

    private final Inventory inventory;
    private final Map<Integer, GuiTile> tiles = new HashMap<>();
    private final List<Runnable> closeCallbacks = new ArrayList<>();

    private boolean opened = false;
    private boolean closed = false;
    private boolean closingExplicitly = false;

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

    // ---- subclass-overridable lifecycle hooks (never called from constructor) ----

    /** Runs once after the inventory is shown to the viewer. Default no-op. */
    protected void onOpen() {}

    /** Runs once on close, before close callbacks fire. Default no-op. */
    protected void onClose() {}

    /**
     * Returns false to prevent the player from closing the GUI themselves
     * (E key, mouse click outside the menu). The framework re-opens the
     * inventory on the next tick when this returns false. Has no effect on
     * explicit {@link #close()} calls from plugin code; those always go
     * through. Default true.
     */
    protected boolean playerCanClose() { return true; }

    // ---- tile mutation (safe to call from any main-thread context) ----

    /**
     * Place a tile at {@code slot}. Updates the tile map and immediately
     * paints the tile's current icon into the live inventory.
     */
    public final void set(int slot, GuiTile tile) {
        Objects.requireNonNull(tile, "tile");
        checkSlot(slot);
        tiles.put(slot, tile);
        inventory.setItem(slot, tile.icon());
    }

    /** Remove the tile at {@code slot}, leaving the inventory slot empty. */
    public final void clear(int slot) {
        checkSlot(slot);
        tiles.remove(slot);
        inventory.setItem(slot, null);
    }

    /**
     * Re-paint every slot from its current {@link GuiTile#icon()}. Useful
     * when tile state has changed and the icon needs to update without
     * replacing the tile itself.
     */
    public final void refresh() {
        for (Map.Entry<Integer, GuiTile> e : tiles.entrySet()) {
            inventory.setItem(e.getKey(), e.getValue().icon());
        }
    }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= inventory.getSize()) {
            throw new IndexOutOfBoundsException(
                    "slot " + slot + " out of range [0, " + inventory.getSize() + ").");
        }
    }

    // ---- session lifecycle ----

    /** The player this session is bound to. */
    public final Player viewer() { return viewer; }

    /** True once the session has closed (player-initiated or {@link #close()}). */
    public final boolean isClosed() { return closed; }

    /**
     * Show the inventory to the bound viewer. May only be called once per
     * instance.
     * todo: unsure whether or not to require that they be open during constructor
     * feels unnecessary to have to handle the potential state of not being open,
     * could lead to a bunch of avoidable logic errors
     *
     * @throws IllegalStateException if already opened or closed
     */
    public final void open() {
        if (closed) {
            throw new IllegalStateException("GuiSession is already closed.");
        }
        if (opened) {
            throw new IllegalStateException("GuiSession is already open.");
        }
        opened = true;
        guiService.register(inventory, this);
        viewer.openInventory(inventory);
        onOpen();
    }

    /**
     * Force-close the inventory; bypasses {@link #playerCanClose()}.
     * Idempotent: a no-op once closed.
     */
    public final void close() {
        if (closed || !opened) return;
        closingExplicitly = true;
        viewer.closeInventory();   // fires InventoryCloseEvent → handleClose()
    }

    // ---- close-event subscription (no public Runnable registry) ----

    /**
     * GameExecutor that schedules each submitted task to run on the main
     * thread the moment this session closes. If already closed, the task
     * is scheduled for the next main-thread tick.
     *
     * <p>This is the public way to react to close. It composes with the
     * Promise API; for a {@code Promise<Void>} that completes on close,
     * see {@link #whenClosedPromise()}.
     */
    public final GameExecutor whenClosedExecutor() {
        return this::registerCloseCallback;
    }

    /**
     * Convenience: a {@code Promise<Void>} that completes the moment this
     * session closes. Equivalent to
     * {@code Promise.runAsync(() -> {}, whenClosedExecutor())}.
     *
     * <p>Named {@code whenClosedPromise} (not {@code closed}) so it cannot
     * be confused with the {@link #isClosed()} state predicate.
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

    /**
     * Routes a click event to the tile at {@code slot}. The Bukkit event
     * has already been cancelled by the listener; this method's job is
     * dispatch only.
     */
    void handleClick(int slot, org.bukkit.event.inventory.ClickType clickType, Player who) {
        if (closed) return;
        GuiTile tile = tiles.get(slot);
        if (tile == null) return;
        tile.onClick(new ClickContext(who, this, slot, clickType));
    }

    /**
     * Called by the listener on {@link org.bukkit.event.inventory.InventoryCloseEvent}.
     * Decides whether to honour the close (run the close-flow) or veto it
     * by re-opening the inventory on the next tick.
     */
    void handleClose() {
        if (closed) return;
        if (!closingExplicitly && !playerCanClose()) {
            // Veto: re-open on next tick. Cannot reopen synchronously inside
            // the close event handler.
            scheduler.mainExecutor().execute(() -> {
                if (!closed && viewer.isOnline()) {
                    viewer.openInventory(inventory);
                }
            });
            return;
        }
        finishClose();
    }

    private void finishClose() {
        closed = true;
        try {
            onClose();
        } catch (RuntimeException ex) {
            // Don't let an onClose throw bypass callback cleanup.
            ex.printStackTrace();
        }
        for (Runnable cb : closeCallbacks) {
            try {
                cb.run();
            } catch (RuntimeException ex) {
                ex.printStackTrace();
            }
        }
        closeCallbacks.clear();
        guiService.unregister(inventory);
    }
}
