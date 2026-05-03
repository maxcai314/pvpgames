package ax.xz.max.pvpgames.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.Objects;

/**
 * Builds Adventure {@link Component}s in a feature's standard chat dialect.
 *
 * <p>Each feature (kit, arena, …) constructs one {@code MessageStyle} bound to
 * its prefix and accent color, and threads it through its command handlers so
 * messaging stays consistent without scattering color/format constants.
 */
public final class MessageStyle {

    private final Component prefix;
    private final TextColor highlightColor;

    /**
     * @param prefixText      e.g. {@code "[Kit] "}
     * @param prefixColor     color for the prefix text
     * @param highlightColor  color for highlighted nouns (resource names, indices, etc.)
     */
    public MessageStyle(String prefixText, TextColor prefixColor, TextColor highlightColor) {
        Objects.requireNonNull(prefixText, "prefixText");
        Objects.requireNonNull(prefixColor, "prefixColor");
        Objects.requireNonNull(highlightColor, "highlightColor");
        this.prefix = Component.text(prefixText, prefixColor);
        this.highlightColor = highlightColor;
    }

    /** A green confirmation. */
    public Component success(String text) {
        return prefix.append(Component.text(text, NamedTextColor.GREEN));
    }

    /** A red failure. */
    public Component error(String text) {
        return prefix.append(Component.text(text, NamedTextColor.RED));
    }

    /** A neutral grey informational line. */
    public Component info(String text) {
        return prefix.append(Component.text(text, NamedTextColor.GRAY));
    }

    /** A bare highlight component (resource names, indices, etc.) without the prefix. */
    public Component highlight(String text) {
        return Component.text(text, highlightColor);
    }

    /**
     * One row of a help menu: {@code [Prefix] /usage — description}.
     */
    public Component helpEntry(String usage, String description) {
        return prefix
                .append(Component.text(usage, highlightColor))
                .append(Component.text(" — " + description, NamedTextColor.GRAY));
    }

    public Component prefix() {
        return prefix;
    }
}
