package ax.xz.max.async.test;

import ax.xz.max.async.GameExecutor;
import ax.xz.max.async.GameScheduler;
import ax.xz.max.async.Promise;
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
 * Test command for verifying that the {@link Promise} and {@link GameScheduler}
 * tools work between the main thread and a background thread the way
 * we expect. Players run {@code /testasync}, kicking off a chain
 * that bounces between executors, sends chat messages from the main thread,
 * and sleeps from the async thread.
 *
 * <p>The shape of the chain is:
 * <ol>
 *   <li>Async: build a {@link TestInfo} carrying the player and a random
 *       integer between 1 and 5 inclusive.</li>
 *   <li>Main:  send the player a chat message with that number.</li>
 *   <li>Async: sleep 500ms; forward the value unchanged.</li>
 *   <li>Recursive {@code thenComposeAsync} loop until {@code n} reaches zero:
 *     <ul>
 *       <li>Main: send the player a chat message with the current {@code n}.</li>
 *       <li>Async: sleep 500ms.</li>
 *       <li>Decrement {@code n}.</li>
 *       <li>Recurse.</li>
 *     </ul>
 *   </li>
 * </ol>
 */
public final class TestAsyncCommand {

    private final GameScheduler scheduler;

    public TestAsyncCommand(GameScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** Pair of player and remaining iterations; threads through the promise chain. */
    public record TestInfo(Player p, int n) {}

    /**
     * Registers the command tree with Paper's command lifecycle. Must be
     * called during plugin enable.
     */
    public void register(LifecycleEventManager<? extends Plugin> events) {
        Objects.requireNonNull(events, "events");
        events.registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(
                        Commands.literal("testasync")
                                .requires(s -> s.getSender().isOp())
                                .executes(this::run)
                                .build(),
                        "Run an async/main thread bouncing test sequence",
                        List.of()));
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            ctx.getSource().getSender().sendMessage(
                    Component.text("/testasync must be run by a player.", NamedTextColor.RED));
            return 0;
        }

        GameExecutor main = scheduler.mainExecutor();
        GameExecutor async = scheduler.asyncExecutor();

        // Initial chain: async supply -> main message -> async sleep ->
        // recursive loop driven by thenComposeAsync.
        Promise.supplyAsync(() -> new TestInfo(player, (int) (Math.random() * 5) + 1), async)
                .thenApplyAsync(info -> {
                    info.p().sendMessage(Component.text(
                            "[testasync] starting with n = " + info.n(), NamedTextColor.AQUA));
                    return info;
                }, main)
                .thenApplyAsync(info -> {
                    sleepQuietly(500);
                    return info;
                }, async)
                .thenComposeAsync(this::loop, main);

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Recursive promise-returning function. Each call decides whether to stop
     * (n == 0) or to do one iteration of work and recurse via
     * {@link Promise#thenComposeAsync}. Re-fetches the executors each call so
     * the recursion doesn't pin them in a closure.
     */
    private Promise<TestInfo> loop(TestInfo info) {
        if (info.n() == 0) {
            return Promise.completedFuture(info);
        }

        GameExecutor main = scheduler.mainExecutor();
        GameExecutor async = scheduler.asyncExecutor();

        return Promise.supplyAsync(() -> {
                    info.p().sendMessage(Component.text(
                            "[testasync] loop n = " + info.n(), NamedTextColor.YELLOW));
                    return info;
                }, main)
                .thenApplyAsync(i -> {
                    sleepQuietly(500);
                    return i;
                }, async)
                .thenApplyAsync(i -> new TestInfo(i.p(), i.n() - 1), main)
                .thenComposeAsync(this::loop, main);
    }

    /**
     * Wraps {@link Thread#sleep(long)} so we can use it inside lambdas without
     * each call having to declare {@code throws InterruptedException}. If the
     * thread is interrupted the flag is restored and the sleep returns early.
     */
    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
