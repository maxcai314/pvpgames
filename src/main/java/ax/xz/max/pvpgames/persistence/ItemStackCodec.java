package ax.xz.max.pvpgames.persistence;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * Encodes and decodes {@link ItemStack}s as base64-encoded NBT byte strings.
 *
 * <p>This is the only place in the plugin that touches the Paper item-serialization
 * surface. The NBT bytes produced by {@link ItemStack#serializeAsBytes()} are
 * routed through Mojang's DataFixerUpper on read, which keeps stored kits valid
 * across Minecraft version updates (renamed item ids, attribute migrations,
 * component-vs-NBT transitions, etc.).
 *
 * <p>Empty slots and {@code null} items are encoded as {@code null} entries
 * rather than blank strings; this makes round-trips exact and means
 * {@code ItemStack.empty()}-style sentinel handling stays out of the codec.
 */
public final class ItemStackCodec {

    private ItemStackCodec() {}

    /**
     * Encodes a single {@link ItemStack} to a base64 string.
     * Returns {@code null} if {@code item} is {@code null} or
     * {@link ItemStack#isEmpty() empty}.
     */
    public static String encode(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        byte[] bytes = item.serializeAsBytes();
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Decodes a single {@link ItemStack} previously produced by {@link #encode}.
     * Returns {@code null} if {@code encoded} is {@code null} or empty.
     *
     * @throws IllegalArgumentException if the string is not valid base64 or the
     *         resulting bytes cannot be deserialized into an item.
     */
    public static ItemStack decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            return ItemStack.deserializeBytes(bytes);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Failed to decode item: " + ex.getMessage(), ex);
        }
    }

    /**
     * Encodes an array of items, preserving slot positions. Empty slots become
     * empty strings so that Bukkit's {@code YamlConfiguration#getStringList}
     * (which silently drops {@code null}s) round-trips them correctly.
     */
    public static List<String> encodeArray(ItemStack[] items) {
        Objects.requireNonNull(items, "items");
        List<String> out = new ArrayList<>(items.length);
        for (ItemStack item : items) {
            String encoded = encode(item);
            out.add(encoded == null ? "" : encoded);
        }
        return out;
    }

    /**
     * Decodes an array of items previously produced by {@link #encodeArray}.
     *
     * @param encoded        the encoded list (may contain {@code null}s for empty slots)
     * @param expectedLength the required length of the resulting array
     * @return an array of length {@code expectedLength} with {@code null}s for empty slots
     * @throws IllegalArgumentException if the list length differs from
     *         {@code expectedLength} or any entry fails to decode
     */
    public static ItemStack[] decodeArray(List<String> encoded, int expectedLength) {
        Objects.requireNonNull(encoded, "encoded");
        if (encoded.size() != expectedLength) {
            throw new IllegalArgumentException(
                    "Expected " + expectedLength + " encoded slots but got " + encoded.size());
        }
        ItemStack[] out = new ItemStack[expectedLength];
        for (int i = 0; i < expectedLength; i++) {
            out[i] = decode(encoded.get(i));
        }
        return out;
    }
}
