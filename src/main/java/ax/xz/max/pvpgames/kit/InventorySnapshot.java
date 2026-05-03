package ax.xz.max.pvpgames.kit;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Objects;

/**
 * Immutable snapshot of a player's inventory layout.
 *
 * <p>Backed by three slot-arrays whose lengths match Bukkit's
 * {@link PlayerInventory} accessors:
 * <ul>
 *   <li>{@link #storage}, 36 slots: hotbar (0–8) + main inventory (9–35).
 *       Mirrors {@link PlayerInventory#getStorageContents()}.</li>
 *   <li>{@link #armor}, 4 slots: boots, leggings, chestplate, helmet,
 *       in the same order as {@link PlayerInventory#getArmorContents()}.</li>
 *   <li>{@link #offHand}, single slot.</li>
 * </ul>
 *
 * <p>Empty slots are represented as {@code null}.
 *
 * <p>Because {@link ItemStack} is mutable, the canonical constructor and
 * accessors deep-clone the arrays so the record is genuinely immutable. Treat
 * any {@code ItemStack} returned from this record as a fresh copy.
 */
public record InventorySnapshot(ItemStack[] storage, ItemStack[] armor, ItemStack offHand) {

    public static final int STORAGE_SIZE = 36;
    public static final int ARMOR_SIZE = 4;

    public InventorySnapshot {
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(armor, "armor");
        if (storage.length != STORAGE_SIZE) {
            throw new IllegalArgumentException(
                    "storage must have " + STORAGE_SIZE + " slots, got " + storage.length);
        }
        if (armor.length != ARMOR_SIZE) {
            throw new IllegalArgumentException(
                    "armor must have " + ARMOR_SIZE + " slots, got " + armor.length);
        }
        storage = deepClone(storage);
        armor = deepClone(armor);
        offHand = (offHand == null) ? null : offHand.clone();
    }

    /**
     * Captures the current state of {@code player}'s inventory.
     */
    public static InventorySnapshot captureFrom(Player player) {
        Objects.requireNonNull(player, "player");
        return captureFrom(player.getInventory());
    }

    /**
     * Captures the current state of the given {@link PlayerInventory}.
     */
    public static InventorySnapshot captureFrom(PlayerInventory inventory) {
        Objects.requireNonNull(inventory, "inventory");
        return new InventorySnapshot(
                inventory.getStorageContents(),
                inventory.getArmorContents(),
                inventory.getItemInOffHand());
    }

    /**
     * Replaces the contents of {@code player}'s inventory with this snapshot.
     * Must be called on the server main thread.
     */
    public void applyTo(Player player) {
        Objects.requireNonNull(player, "player");
        applyTo(player.getInventory());
    }

    /**
     * Replaces the contents of {@code inventory} with this snapshot.
     * Must be called on the server main thread.
     */
    public void applyTo(PlayerInventory inventory) {
        Objects.requireNonNull(inventory, "inventory");
        inventory.setStorageContents(deepClone(storage));
        inventory.setArmorContents(deepClone(armor));
        inventory.setItemInOffHand(offHand == null ? null : offHand.clone());
    }

    /**
     * Returns {@code true} if every slot is empty or AIR. An empty snapshot
     * usually indicates a mistaken {@code /kit create} from an empty inventory.
     */
    public boolean isEmpty() {
        for (ItemStack item : storage) if (!isSlotEmpty(item)) return false;
        for (ItemStack item : armor) if (!isSlotEmpty(item)) return false;
        return isSlotEmpty(offHand);
    }

    @Override
    public ItemStack[] storage() {
        return deepClone(storage);
    }

    @Override
    public ItemStack[] armor() {
        return deepClone(armor);
    }

    @Override
    public ItemStack offHand() {
        return offHand == null ? null : offHand.clone();
    }

    private static boolean isSlotEmpty(ItemStack item) {
        return item == null || item.isEmpty();
    }

    private static ItemStack[] deepClone(ItemStack[] source) {
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] == null ? null : source[i].clone();
        }
        return copy;
    }
}
