package ax.xz.max.pvpgames.server;

import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link ServerHelper} backed by a real Paper {@link Server}.
 */
public final class BukkitServerHelper implements ServerHelper {

    private final Server server;
    private final Plugin plugin;

    public BukkitServerHelper(Server server, Plugin plugin) {
        this.server = Objects.requireNonNull(server, "server");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public void broadcast(Component message) {
        Objects.requireNonNull(message, "message");
        server.broadcast(message);
    }

    @Override
    public void consoleMessage(Component message) {
        Objects.requireNonNull(message, "message");
        server.getConsoleSender().sendMessage(message);
    }

    @Override
    public BukkitTask runMainThread(Runnable task) {
        Objects.requireNonNull(task, "task");
        return server.getScheduler().runTask(plugin, task);
    }

    @Override
    public BukkitTask runAsync(Runnable task) {
        Objects.requireNonNull(task, "task");
        return server.getScheduler().runTaskAsynchronously(plugin, task);
    }

    @Override
    public boolean isPrimaryThread() {
        return server.isPrimaryThread();
    }

    @Override
    public Optional<String> resolveOfflineName(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        return Optional.ofNullable(server.getOfflinePlayer(uuid).getName());
    }
}
