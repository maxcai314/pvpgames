package ax.xz.max.async;

import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;

/**
 * A helper for creating {@link GameExecutor} executors from a Bukkit plugin.
 * Provides a main executor, which runs commands on the next server tick in the main thread,
 * as well as an async executor, which runs commands on a background thread.
 */
public class GameScheduler {
    private final Logger logger;
    private final Plugin plugin;

    public GameScheduler(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getSLF4JLogger();
    }

    public Logger logger() {
        return this.logger;
    }

    /**
     * Returns a GameExecutor that runs commands on the next server tick in the main thread.
     * This executor should not run blocking code that will freeze the server tick.
     * This executor may be used to interact with the Bukkit API.
     */
    public GameExecutor mainExecutor() {
        final var executor = plugin.getServer().getScheduler().getMainThreadExecutor(plugin);
        return executor::execute;
    }

    /**
     * Returns a GameExecutor that runs commands in a background thread.
     * This executor runs independently of the server ticks, and is allowed to run blocking tasks.
     * This executor should not be used to interact with the Bukkit API.
     */
    public GameExecutor asyncExecutor() {
        final var scheduler = plugin.getServer().getScheduler();
        final var plugin = this.plugin;
        return command -> scheduler.runTaskAsynchronously(plugin, command);
    }

    /**
     * Returns a GameExecutor that will run commands a specified number of ticks later on the main thread.
     * This executor may be used to interact with the Bukkit API.
     * @param delayTicks the number of ticks of delay before scheduling the task
     */
    public GameExecutor mainExecutorWithDelay(long delayTicks) {
        final var scheduler = plugin.getServer().getScheduler();
        final var plugin = this.plugin;
        return command -> scheduler.runTaskLater(plugin, command, delayTicks);
    }

    /**
     * Returns a GameExecutor that will run commands a specified number of ticks later on a background thread.
     * This executor should not be used to interact with the Bukkit API.
     * @param delayTicks the number of ticks of delay before scheduling the task
     */
    public GameExecutor asyncExecutorWithDelay(long delayTicks) {
        final var scheduler = plugin.getServer().getScheduler();
        final var plugin = this.plugin;
        return command -> scheduler.runTaskLaterAsynchronously(plugin, command, delayTicks);
    }
}
