package ax.xz.max.gui.test;

import ax.xz.max.async.GameScheduler;
import ax.xz.max.gui.ClickContext;
import ax.xz.max.gui.GuiService;
import ax.xz.max.gui.GuiSession;
import ax.xz.max.gui.GuiTile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Example GUI for verifying the {@code ax.xz.max.gui} framework end-to-end.
 *
 * <p>Layout (3 rows / 27 slots):
 * <pre>
 * row 0 ─ ingot cycler:    slot 2 = cycler tile (click to advance), slot 6 = display tile
 * row 1 ─ counter widget:  slot 11 = decrement, slot 13 = gold-ingot display, slot 15 = increment
 * row 2 ─ controls:        slot 22 = reset, slot 26 = close (right-click only)
 * </pre>
 *
 * <p>{@link #playerCanClose()} returns false: the player cannot press E to
 * exit. The only way out is a right-click on the red wool, whose tile calls
 * {@link #close()} to bypass the veto.
 *
 * <p>Public accessors {@link #count()} and {@link #currentIngot()} let the
 * launcher command read the final state from the close-promise lambda.
 */
final class TestGuiSession extends GuiSession {

    private static final Material[] INGOT_CYCLE = {
            Material.IRON_INGOT,
            Material.COPPER_INGOT,
            Material.GOLD_INGOT,
            Material.NETHERITE_INGOT
    };

    private int count = 0;
    private int ingotIndex = 0;

    TestGuiSession(Player viewer, GameScheduler scheduler, GuiService guiService) {
        super(viewer, scheduler, guiService, Component.text("Test GUI"), 3);

        // Ingot cycler: clicking advances to the next ingot in the cycle and
        // refreshes the whole inventory so the display tile picks up the
        // new state too.
        set(2, new GuiTile() {
            @Override public ItemStack icon() {
                return named(new ItemStack(INGOT_CYCLE[ingotIndex]),
                        "Cycler — click to advance",
                        "Currently: " + INGOT_CYCLE[ingotIndex].name());
            }
            @Override public void onClick(ClickContext ctx) {
                ingotIndex = (ingotIndex + 1) % INGOT_CYCLE.length;
                refresh();
            }
        });
        // Display tile: reads cycler state and shows the current ingot's
        // material name in its display name. Verifies that one tile can
        // observe state changes driven by another.
        set(6, new GuiTile() {
            @Override public ItemStack icon() {
                return named(new ItemStack(INGOT_CYCLE[ingotIndex]),
                        "Display: " + INGOT_CYCLE[ingotIndex].name());
            }
        });

        // Counter widget: dec / display / inc. Stack size and display name
        // both reflect the current count; stack size is clamped to [1, 64]
        // because Bukkit ItemStacks reject other amounts.
        set(11, simplePane(Material.RED_STAINED_GLASS_PANE, "Decrement (-1)",
                () -> { count--; refresh(); }));
        set(13, new GuiTile() {
            @Override public ItemStack icon() {
                int stackSize = Math.max(1, Math.min(64, count));
                return named(new ItemStack(Material.GOLD_INGOT, stackSize),
                        "Count: " + count);
            }
        });
        set(15, simplePane(Material.GREEN_STAINED_GLASS_PANE, "Increment (+1)",
                () -> { count++; refresh(); }));

        // Reset and close. Reset accepts any click; close only honours
        // right-click (so left-click misclicks don't exit the GUI).
        set(22, simplePane(Material.WHITE_STAINED_GLASS_PANE, "Reset count",
                () -> { count = 0; refresh(); }));
        set(26, new GuiTile() {
            @Override public ItemStack icon() {
                return named(new ItemStack(Material.RED_WOOL),
                        "Close (right-click)",
                        "playerCanClose() = false; only this button exits.");
            }
            @Override public void onClick(ClickContext ctx) {
                if (ctx.clickType() == ClickType.RIGHT) {
                    close();
                }
            }
        });
    }

    /** Current counter value. Read by the close-promise consumer. */
    public int count() { return count; }

    /** Currently-selected ingot from the cycler. Read by the close-promise consumer. */
    public Material currentIngot() { return INGOT_CYCLE[ingotIndex]; }

    @Override
    protected boolean playerCanClose() {
        return false;
    }

    /** Convenience for plain-pane tiles to run on any type of click. */
    private GuiTile simplePane(Material material, String displayName, Runnable action) {
        ItemStack item = named(new ItemStack(material), displayName);
        return GuiTile.of(item, ctx -> action.run());
    }

    /** Sets the display name (and optional lore) on an item and returns it. */
    private static ItemStack named(ItemStack item, String displayName, String... lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(displayName).color(NamedTextColor.AQUA));
            if (lore.length > 0) {
                java.util.List<Component> loreLines = new java.util.ArrayList<>(lore.length);
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
