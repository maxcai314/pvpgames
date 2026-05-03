package ax.xz.max.pvpgames;

import ax.xz.max.pvpgames.kit.KitRepository;
import ax.xz.max.pvpgames.kit.KitService;
import ax.xz.max.pvpgames.kit.command.KitCommand;
import ax.xz.max.pvpgames.kit.internal.DefaultKitService;
import ax.xz.max.pvpgames.kit.internal.FileKitRepository;
import ax.xz.max.pvpgames.server.BukkitServerHelper;
import ax.xz.max.pvpgames.server.ServerHelper;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

/**
 * Plugin entry point.
 *
 * <p>This is the only place we touch {@link Server} and other Bukkit
 * statics; everything else receives its dependencies through constructors.
 */
public final class PvpgamesPlugin extends JavaPlugin {

    private ServerHelper serverHelper;
    private KitRepository kitRepository;
    private KitService kitService;

    @Override
    public void onEnable() {
        Server server = getServer();

        this.serverHelper = new BukkitServerHelper(server, this);

        Path kitsDir = getDataFolder().toPath().resolve("kits");
        try {
            Files.createDirectories(kitsDir);
        } catch (IOException ex) {
            getSLF4JLogger().error("Failed to create kits directory at {}; disabling plugin.", kitsDir, ex);
            server.getPluginManager().disablePlugin(this);
            return;
        }

        this.kitRepository = new FileKitRepository(kitsDir, getSLF4JLogger());
        this.kitService = new DefaultKitService(kitRepository, Clock.systemUTC());

        // register the kit command
        new KitCommand(kitService, serverHelper).register(getLifecycleManager());

        getSLF4JLogger().info("Checking for other plugins...");
        PluginManager pm = getServer().getPluginManager();
        if (pm.isPluginEnabled("WorldEdit")) {
            getSLF4JLogger().info("Worldedit plugin found");
        }
        if (pm.isPluginEnabled("Multiverse-Core")) {
            getSLF4JLogger().info("Multiverse plugin found");
        }

        getSLF4JLogger().info("Pvpgames enabled with {} kit(s) loaded.", kitRepository.all().size());
    }

    @Override
    public void onDisable() {
        // No flush needed: kit writes are durable per-command.
    }

    public ServerHelper serverHelper() {
        return serverHelper;
    }

    public KitService kitService() {
        return kitService;
    }
}
