package ax.xz.max.gui.test;

import ax.xz.max.async.GameScheduler;
import ax.xz.max.gui.GuiService;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Objects;

/**
 * Sandbox command that opens a {@link TestGuiSession} for the running
 * player and chains a chat-message report off the session's close-promise.
 *
 * <p>Demonstrates the intended usage: the launcher constructs the
 * session, opens it, and immediately attaches a {@code thenApplyAsync}
 * continuation to {@code whenClosedPromise()}. The continuation captures
 * the session reference and reads its final state ({@link TestGuiSession#count()}
 * and {@link TestGuiSession#currentIngot}) on the main thread.
 *
 * <p>Gated by {@code .requires(isOp)} so it does not need a permission
 * node in {@code plugin.yml}; this is a developer-facing tool.
 */
public final class TestGuiCommand {

    private final GameScheduler scheduler;
    private final GuiService guiService;

    public TestGuiCommand(GameScheduler scheduler, GuiService guiService) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.guiService = Objects.requireNonNull(guiService, "guiService");
    }

    /** Registers the command with Paper's command lifecycle. */
    public void register(LifecycleEventManager<? extends Plugin> events) {
        Objects.requireNonNull(events, "events");
        events.registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(
                        Commands.literal("testgui")
                                .requires(s -> s.getSender().isOp())
                                .executes(this::run)
                                .build(),
                        "Open the GUI framework sandbox",
                        List.of()));
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            ctx.getSource().getSender().sendMessage(
                    Component.text("/testgui must be run by a player.", NamedTextColor.RED));
            return 0;
        }

        TestGuiSession gui = new TestGuiSession(player, scheduler, guiService);
        gui.open();

        // Capture the gui reference; the continuation reads its final
        // state once whenClosedPromise() resolves on the main thread.
        gui.whenClosedPromise()
                .thenApplyAsync(unused -> {
                    player.sendMessage(Component.text("[testgui] closed — count = ", NamedTextColor.AQUA)
                            .append(Component.text(gui.count(), NamedTextColor.YELLOW))
                            .append(Component.text(", ingot = ", NamedTextColor.AQUA))
                            .append(Component.text(gui.currentIngot().name(), NamedTextColor.YELLOW)));
                    return null;
                }, scheduler.mainExecutor());

        return Command.SINGLE_SUCCESS;
    }
}
