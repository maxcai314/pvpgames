package ax.xz.max.gui.test.paint;

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
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Sandbox command that opens a {@link PaintGuiSession} for the running
 * player and chains a color-histogram chat report off the session's
 * close-promise. Mirrors {@link ax.xz.max.gui.test.TestGuiCommand} for
 * the layer-1 paint sandbox.
 */
public final class TestPaintGuiCommand {

    private final GameScheduler scheduler;
    private final GuiService guiService;

    public TestPaintGuiCommand(GameScheduler scheduler, GuiService guiService) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.guiService = Objects.requireNonNull(guiService, "guiService");
    }

    /** Registers the command with Paper's command lifecycle. */
    public void register(LifecycleEventManager<? extends Plugin> events) {
        Objects.requireNonNull(events, "events");
        events.registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(
                        Commands.literal("testguipaint")
                                .requires(s -> s.getSender().isOp())
                                .executes(this::run)
                                .build(),
                        "Open the paint GUI sandbox",
                        List.of()));
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            ctx.getSource().getSender().sendMessage(
                    Component.text("/testguipaint must be run by a player.", NamedTextColor.RED));
            return 0;
        }

        PaintGuiSession gui = new PaintGuiSession(player, scheduler, guiService);
        gui.open();

        gui.whenClosedPromise()
                .thenApplyAsync(unused -> {
                    Map<Material, Integer> hist = gui.histogram();
                    if (hist.isEmpty()) {
                        player.sendMessage(Component.text(
                                "[testguipaint] Empty canvas.", NamedTextColor.GRAY));
                    } else {
                        player.sendMessage(Component.text(
                                "[testguipaint] Final painting:", NamedTextColor.AQUA));
                        hist.entrySet().stream()
                                .sorted(Comparator.comparingInt(e -> e.getKey().ordinal()))
                                .forEach(e -> player.sendMessage(Component.text("  ", NamedTextColor.GRAY)
                                        .append(Component.text(e.getKey().name(), NamedTextColor.AQUA))
                                        .append(Component.text(": ", NamedTextColor.GRAY))
                                        .append(Component.text(e.getValue(), NamedTextColor.YELLOW))));
                    }
                    return null;
                }, scheduler.mainExecutor());

        return Command.SINGLE_SUCCESS;
    }
}
