package ax.xz.max.pvpgames.server;

import net.kyori.adventure.text.Component;
import org.bukkit.scheduler.BukkitTask;

import java.util.Optional;
import java.util.UUID;

/**
 * Thin abstraction over the parts of {@link org.bukkit.Server} this plugin uses
 * frequently.
 *
 * <p>Domain code depends on this interface instead of reaching for
 * {@code org.bukkit.Bukkit} statics, so the rest of the codebase stays free of
 * static singleton lookups and can be exercised with a fake in tests. The
 * concrete {@link BukkitServerHelper} is the only place {@link org.bukkit.Server}
 * is actually touched.
 */
public interface ServerHelper {

    /**
     * Sends an Adventure {@link Component} to every online player and the console.
     * Replaces the deprecated {@code Bukkit.broadcastMessage(String)}.
     */
    void broadcast(Component message);

    /**
     * Sends a {@link Component} to the server console only.
     */
    void consoleMessage(Component message);

    /**
     * Schedules a one-shot task on the server main thread on the next tick.
     */
    BukkitTask runMainThread(Runnable task);

    /**
     * Schedules a one-shot task on the asynchronous worker pool. Used for I/O so
     * we do not block the main thread.
     */
    BukkitTask runAsync(Runnable task);

    /**
     * Returns {@code true} when called from the server main thread.
     */
    boolean isPrimaryThread();

    /**
     * Resolves a player {@link UUID} to the most recent known username, or
     * {@link Optional#empty()} if the server has no record of that player.
     *
     * <p>Backed by Bukkit's offline-player cache; safe to call from the main
     * thread.
     */
    Optional<String> resolveOfflineName(UUID uuid);
}
