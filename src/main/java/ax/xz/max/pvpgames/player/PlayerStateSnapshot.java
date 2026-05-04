package ax.xz.max.pvpgames.player;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable snapshot of a player's state, stored as deep copies of the raw
 * Bukkit fields we care about. {@link #captureFrom(Player)} reads off a live
 * player; {@link #applyTo(Player)} writes everything back in one call.
 *
 * <p>The {@link #location} field uses Bukkit's {@link Location} directly (not a
 * custom wrapper) because it carries the {@link org.bukkit.World} reference,
 * which is what makes restoration cross-world. {@link #location} is nullable:
 * when {@code null}, {@link #applyTo} skips the teleport and only restores the
 * remaining fields. {@link #DEFAULT} uses this to act as a "fresh-join" wipe
 * that does not move the player.
 *
 * <p>Mutable Bukkit objects ({@link Location}, {@link Vector}, {@link ItemStack})
 * are deep-copied at the boundary so post-capture edits to the live player
 * don't corrupt the snapshot, and applying a snapshot doesn't share references
 * back with it.
 *
 * <p>{@link #attributes} carries the base value of every {@link Attribute} the
 * player had at capture time. Capturing and applying the whole map (rather
 * than special-casing {@code MAX_HEALTH}) keeps any custom modifiers admins
 * have configured (movement speed, attack damage, etc.) intact across a
 * preview.
 */
public record PlayerStateSnapshot(
        Location location,
        GameMode gameMode,
        ItemStack[] storage,
        ItemStack[] armor,
        ItemStack offHand,
        double health,
        int foodLevel,
        float saturation,
        float exhaustion,
        int level,
        float exp,
        int totalExperience,
        Vector velocity,
        float fallDistance,
        int fireTicks,
        int remainingAir,
        int maximumAir,
        boolean allowFlight,
        boolean flying,
        float walkSpeed,
        float flySpeed,
        List<PotionEffect> potionEffects,
        Map<Attribute, Double> attributes
) {

    public PlayerStateSnapshot {
        // location is intentionally nullable (DEFAULT has no location).
        Objects.requireNonNull(gameMode, "gameMode");
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(armor, "armor");
        // offHand is intentionally nullable.
        Objects.requireNonNull(velocity, "velocity");
        Objects.requireNonNull(potionEffects, "potionEffects");
        Objects.requireNonNull(attributes, "attributes");
        location = location == null ? null : location.clone();
        velocity = velocity.clone();
        storage = deepCopy(storage);
        armor = deepCopy(armor);
        offHand = offHand == null ? null : offHand.clone();
        // PotionEffect itself is immutable, so List.copyOf is enough.
        potionEffects = List.copyOf(potionEffects);
        // Doubles and Attribute keys are both immutable, so a shallow copy is
        // genuinely immutable.
        attributes = Map.copyOf(attributes);
    }

    /**
     * "Fresh-join" baseline: empty inventory, full health and food, default
     * speeds, no potion effects, no flight, no fire, no momentum. {@code
     * location} is {@code null} so {@link #applyTo} only wipes state without
     * moving the player; the caller teleports separately. {@code attributes}
     * is empty so {@link #applyTo} touches no attribute base-values, leaving
     * the player's existing modifiers (from items, effects, etc.) alone.
     */
    public static final PlayerStateSnapshot DEFAULT = new PlayerStateSnapshot(
            null,
            GameMode.SURVIVAL,
            new ItemStack[36],
            new ItemStack[4],
            null,
            20.0,
            20,
            5.0f,
            0.0f,
            0,
            0.0f,
            0,
            new Vector(0, 0, 0),
            0.0f,
            0,
            300,
            300,
            false,
            false,
            0.2f,
            0.1f,
            List.of(),
            Map.of()
    );

    /**
     * Captures every field listed on this record off the given player,
     * including the base value of every {@link Attribute} the player has an
     * instance of.
     *
     * <p>Must be called from the server main thread; reads live player data.
     */
    public static PlayerStateSnapshot captureFrom(Player player) {
        Objects.requireNonNull(player, "player");
        PlayerInventory inv = player.getInventory();
        return new PlayerStateSnapshot(
                player.getLocation(),
                player.getGameMode(),
                inv.getStorageContents(),
                inv.getArmorContents(),
                inv.getItemInOffHand(),
                player.getHealth(),
                player.getFoodLevel(),
                player.getSaturation(),
                player.getExhaustion(),
                player.getLevel(),
                player.getExp(),
                player.getTotalExperience(),
                player.getVelocity(),
                player.getFallDistance(),
                player.getFireTicks(),
                player.getRemainingAir(),
                player.getMaximumAir(),
                player.getAllowFlight(),
                player.isFlying(),
                player.getWalkSpeed(),
                player.getFlySpeed(),
                new ArrayList<>(player.getActivePotionEffects()),
                captureAttributes(player)
        );
    }

    private static Map<Attribute, Double> captureAttributes(Player player) {
        Map<Attribute, Double> out = new HashMap<>();
        for (Attribute attr : Registry.ATTRIBUTE) {
            AttributeInstance instance = player.getAttribute(attr);
            if (instance != null) {
                out.put(attr, instance.getBaseValue());
            }
        }
        return out;
    }

    /**
     * Restores the snapshot onto {@code player}. Teleports first when a
     * {@link #location} is set, then sets gamemode and the rest of the
     * fields, then sets velocity last (some teleport implementations zero
     * velocity).
     *
     * <p>Must be called from the server main thread.
     */
    public void applyTo(Player player) {
        Objects.requireNonNull(player, "player");

        if (location != null) {
            player.eject();
            player.teleport(location);
        }
        player.setGameMode(gameMode);

        PlayerInventory inv = player.getInventory();
        inv.setStorageContents(deepCopy(storage));
        inv.setArmorContents(deepCopy(armor));
        inv.setItemInOffHand(offHand == null ? null : offHand.clone());

        // Restore attribute base values FIRST so the subsequent setHealth
        // call clamps against the captured MAX_HEALTH (not whatever the live
        // player happens to have right now). The map is empty for DEFAULT,
        // making this a no-op there.
        attributes.forEach((attr, attrValue) -> {
            AttributeInstance instance = player.getAttribute(attr);
            if (instance != null) {
                instance.setBaseValue(attrValue);
            }
        });

        AttributeInstance maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0;
        player.setHealth(Math.max(0.0, Math.min(health, maxHealth)));
        player.setFoodLevel(foodLevel);
        player.setSaturation(saturation);
        player.setExhaustion(exhaustion);

        player.setLevel(level);
        player.setExp(exp);
        player.setTotalExperience(totalExperience);

        player.setFallDistance(fallDistance);
        player.setFireTicks(fireTicks);
        player.setRemainingAir(remainingAir);
        player.setMaximumAir(maximumAir);

        player.setWalkSpeed(walkSpeed);
        player.setFlySpeed(flySpeed);
        // setFlying throws when allowFlight is false, so set the flag first
        // and gate the actual flying state on it.
        player.setAllowFlight(allowFlight);
        player.setFlying(allowFlight && flying);

        player.clearActivePotionEffects();
        for (PotionEffect effect : potionEffects) {
            player.addPotionEffect(effect);
        }

        player.setVelocity(velocity.clone());
    }

    private static ItemStack[] deepCopy(ItemStack[] source) {
        ItemStack[] out = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            out[i] = source[i] == null ? null : source[i].clone();
        }
        return out;
    }
}
