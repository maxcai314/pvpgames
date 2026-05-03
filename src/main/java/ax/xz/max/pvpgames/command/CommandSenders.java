package ax.xz.max.pvpgames.command;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.Optional;

/**
 * Utility helpers for command handlers that need to inspect the
 * {@link CommandSourceStack}'s sender.
 */
public final class CommandSenders {

    private CommandSenders() {}

    /**
     * Returns the sender as a {@link Player} if the command was issued by one;
     * Otherwise, sends a styled error message ("Only players can &lt;action&gt;.")
     * to the sender and returns {@link Optional#empty()}.
     *
     * @param source the command source
     * @param style  the feature's {@link MessageStyle} for the error
     * @param action a short description of what the command would do
     *               (used in the error: "Only players can {@code <action>}.")
     */
    public static Optional<Player> requirePlayer(CommandSourceStack source, MessageStyle style, String action) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(action, "action");
        if (source.getSender() instanceof Player player) {
            return Optional.of(player);
        }
        source.getSender().sendMessage(style.error("Only players can " + action + "."));
        return Optional.empty();
    }
}
