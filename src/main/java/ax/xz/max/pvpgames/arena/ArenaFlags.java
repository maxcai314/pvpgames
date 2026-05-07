package ax.xz.max.pvpgames.arena;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Strongly-typed bag of WorldGuard region flags that arenas can configure.
 *
 * <p>Each field is a boolean where {@code true} corresponds to WorldGuard's
 * {@code allow} state and {@code false} corresponds to {@code deny}; the
 * underlying flags are all WG state flags.
 *
 * <p>The flag set is intentionally narrow: not every WorldGuard flag is
 * exposed to admins, only those relevant to a PvP arena. Adding a new flag
 * means adding a field, updating {@link #defaults}, {@link #FLAG_NAMES},
 * {@link #withFlag}, {@link #readFlag}, and {@link #toWorldGuardFlags}; the
 * compiler will tell you which switch arms still need cases.
 *
 * <p>Region-wide invariants like {@code exit: deny} live in
 * {@link #BASELINE_WG_FLAGS} instead of this record because they are not
 * something admins should be able to disable per-arena.
 *
 * todo: although these flags are what are applied when initializing the physical arena,
 * in the game system, these are actually set by the duel config, not the arena.
 * players will choose these settings when they choose the kit/gamemode,
 * not when they choose an arena.
 * This is more of a parameter that's used *when* we create an arena, not stored
 * with the arena itself.
 */
public record ArenaFlags(
        boolean pvp,
        boolean blockBreak,
        boolean blockPlace,
        boolean fallDamage,
        boolean naturalHealthRegen,
        boolean naturalHungerDrain
) {

    /** Names admins use in YAML and the {@code /arena setflag} command. */
    public static final List<String> FLAG_NAMES = List.of(
            "pvp",
            "block-break",
            "block-place",
            "fall-damage",
            "natural-health-regen",
            "natural-hunger-drain"
    );

    /**
     * Flags applied to every arena region regardless of configuration. These
     * encode invariants of "this is an arena instance": players cannot leave
     * the region.
     */
    public static final Map<String, String> BASELINE_WG_FLAGS = Map.of(
            "exit", "deny",
            "exit-deny-message", "You may not leave the arena!"
    );

    /** Defaults for a newly created PvP arena. */
    public static ArenaFlags defaults() {
        return new ArenaFlags(
                true,    // pvp: allow
                false,   // block-break: deny
                false,   // block-place: deny
                true,    // fall-damage: allow
                true,   // natural-health-regen: allow
                true    // natural-hunger-drain: allow
        );
    }

    /**
     * Returns a copy with one flag updated by name.
     *
     * @throws IllegalArgumentException if {@code name} is not in
     *         {@link #FLAG_NAMES}; the message lists the valid names so it
     *         can be shown to players
     */
    public ArenaFlags withFlag(String name, boolean value) {
        return switch (name) {
            case "pvp"                    -> new ArenaFlags(value, blockBreak, blockPlace, fallDamage, naturalHealthRegen, naturalHungerDrain);
            case "block-break"            -> new ArenaFlags(pvp, value, blockPlace, fallDamage, naturalHealthRegen, naturalHungerDrain);
            case "block-place"            -> new ArenaFlags(pvp, blockBreak, value, fallDamage, naturalHealthRegen, naturalHungerDrain);
            case "fall-damage"            -> new ArenaFlags(pvp, blockBreak, blockPlace, value, naturalHealthRegen, naturalHungerDrain);
            case "natural-health-regen"   -> new ArenaFlags(pvp, blockBreak, blockPlace, fallDamage, value, naturalHungerDrain);
            case "natural-hunger-drain"   -> new ArenaFlags(pvp, blockBreak, blockPlace, fallDamage, naturalHealthRegen, value);
            default -> throw new IllegalArgumentException(
                    "Unknown arena flag '" + name + "'. Valid flags: " + String.join(", ", FLAG_NAMES));
        };
    }

    /**
     * Reads one flag by name.
     *
     * @throws IllegalArgumentException if {@code name} is not in
     *         {@link #FLAG_NAMES}
     */
    public boolean readFlag(String name) {
        return switch (name) {
            case "pvp"                    -> pvp;
            case "block-break"            -> blockBreak;
            case "block-place"            -> blockPlace;
            case "fall-damage"            -> fallDamage;
            case "natural-health-regen"   -> naturalHealthRegen;
            case "natural-hunger-drain"   -> naturalHungerDrain;
            default -> throw new IllegalArgumentException("Unknown arena flag '" + name + "'.");
        };
    }

    /**
     * Encodes this set as the {@code Map<String, String>} expected by
     * {@code WorldGuardService.applyFlags}. All entries are state-flag
     * values ({@code allow} / {@code deny}).
     */
    public Map<String, String> toWorldGuardFlags() {
        Map<String, String> map = new LinkedHashMap<>(FLAG_NAMES.size());
        for (String name : FLAG_NAMES) {
            map.put(name, readFlag(name) ? "allow" : "deny");
        }
        return map;
    }
}
