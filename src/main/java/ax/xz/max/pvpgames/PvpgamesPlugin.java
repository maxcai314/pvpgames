package ax.xz.max.pvpgames;

import ax.xz.max.async.GameScheduler;
import ax.xz.max.gui.GuiService;
import ax.xz.max.gui.test.TestGuiCommand;
import ax.xz.max.gui.test.paint.TestPaintGuiCommand;
import ax.xz.max.pvpgames.arena.ArenaManager;
import ax.xz.max.pvpgames.arena.ArenaName;
import ax.xz.max.pvpgames.arena.ArenaRepository;
import ax.xz.max.pvpgames.arena.command.ArenaCommand;
import ax.xz.max.pvpgames.arena.internal.ArenaSessionListener;
import ax.xz.max.pvpgames.arena.internal.ArenaAllocator;
import ax.xz.max.pvpgames.arena.internal.DefaultArenaManager;
import ax.xz.max.pvpgames.arena.internal.PlayerStateCache;
import ax.xz.max.pvpgames.arena.internal.UnavailableArenaManager;
import ax.xz.max.pvpgames.arena.internal.persistence.FileArenaRepository;
import ax.xz.max.pvpgames.kit.KitRepository;
import ax.xz.max.pvpgames.kit.KitService;
import ax.xz.max.pvpgames.kit.command.KitCommand;
import ax.xz.max.pvpgames.kit.internal.DefaultKitService;
import ax.xz.max.pvpgames.kit.internal.FileKitRepository;
import ax.xz.max.pvpgames.schematic.SchematicService;
import ax.xz.max.pvpgames.schematic.UnavailableSchematicService;
import ax.xz.max.pvpgames.schematic.WorldEditSchematicService;
import ax.xz.max.async.test.TestAsyncCommand;
import ax.xz.max.pvpgames.world.WorldService;
import ax.xz.max.pvpgames.world.WorldServiceException;
import ax.xz.max.pvpgames.world.internal.BukkitWorldService;
import ax.xz.max.pvpgames.worldguard.BukkitWorldGuardService;
import ax.xz.max.pvpgames.worldguard.UnavailableWorldGuardService;
import ax.xz.max.pvpgames.worldguard.WorldGuardService;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Plugin entry point.
 *
 * <p>This is the only place we touch {@link Server} and other Bukkit
 * statics; everything else receives its dependencies through constructors.
 *
 * <p>Soft dependencies (WorldEdit, WorldGuard) are detected at enable;
 * missing ones cause the corresponding service to be wired with its
 * {@code Unavailable*} stub instead of the real implementation. If either
 * is missing, the arena manager is replaced with
 * {@link UnavailableArenaManager} and the shared arenas world is not
 * created because no session can be opened anyway. The arenas world
 * itself is built directly through {@link WorldService}, which uses
 * Bukkit's own {@code WorldCreator}; no third-party world-management
 * plugin is required.
 */
public final class PvpgamesPlugin extends JavaPlugin {

    private GameScheduler gameScheduler;
    private GuiService guiService;

    private WorldService worldService;
    private SchematicService schematicService;
    private WorldGuardService worldGuardService;

    private KitRepository kitRepository;
    private KitService kitService;

    private ArenaRepository arenaRepository;
    private ArenaManager arenaManager;
    private World arenaWorld;
    private boolean ownsArenaWorld;

    @Override
    public void onEnable() {
        Server server = getServer();

        // used as a dependency in systems for scheduling async tasks
        this.gameScheduler = new GameScheduler(this);

        // single Bukkit listener routing inventory events to GuiSessions;
        // dependency-injected into every GuiSession subclass.
        this.guiService = new GuiService(this);

        PluginManager pm = server.getPluginManager();
        // We specifically require FastAsyncWorldEdit for asynchronous pasting
        boolean faweReady = pm.isPluginEnabled("FastAsyncWorldEdit");
        boolean wgReady = pm.isPluginEnabled("WorldGuard");

        // Bukkit's Server API is always present, so no soft-dep check here.
        this.worldService = new BukkitWorldService(server, getSLF4JLogger());

        Path schematicsDir = getDataFolder().getParentFile().toPath()
                .resolve("WorldEdit").resolve("schematics");
        this.schematicService = faweReady
                ? new WorldEditSchematicService(schematicsDir, gameScheduler, getSLF4JLogger())
                : new UnavailableSchematicService();

        this.worldGuardService = wgReady
                ? new BukkitWorldGuardService(getSLF4JLogger())
                : new UnavailableWorldGuardService();

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

        Path arenasDir = getDataFolder().toPath().resolve("arenas");
        try {
            Files.createDirectories(arenasDir);
        } catch (IOException ex) {
            getSLF4JLogger().error("Failed to create arenas directory at {}; disabling plugin.", arenasDir, ex);
            server.getPluginManager().disablePlugin(this);
            return;
        }

        this.arenaRepository = new FileArenaRepository(arenasDir, getSLF4JLogger());

        String missingDeps = describeMissingDependencies(faweReady, wgReady);
        // todo: ugly pattern. use Optional instead
        if (missingDeps == null) {
            try {
                this.arenaWorld = worldService.createVoidWorld(ArenaName.SHARED_WORLD_NAME);
                this.ownsArenaWorld = true;
            } catch (WorldServiceException ex) {
                getSLF4JLogger().error("Failed to create shared arenas world; disabling plugin.", ex);
                server.getPluginManager().disablePlugin(this);
                return;
            }
            ArenaAllocator allocator = new ArenaAllocator(arenaWorld);
            PlayerStateCache stateCache = new PlayerStateCache(server, this);
            this.arenaManager = new DefaultArenaManager(
                    arenaRepository, schematicService, worldGuardService,
                    allocator, stateCache, arenaWorld,
                    server, gameScheduler, Clock.systemUTC(), getSLF4JLogger());
        } else {
            this.arenaManager = new UnavailableArenaManager(arenaRepository, Clock.systemUTC(), missingDeps);
        }

        // register listeners
        server.getPluginManager().registerEvents(new ArenaSessionListener(arenaManager), this);

        // register commands
        new KitCommand(kitService, server).register(getLifecycleManager());
        new ArenaCommand(arenaManager, server, gameScheduler).register(getLifecycleManager());
        new TestAsyncCommand(gameScheduler).register(getLifecycleManager());
        new TestGuiCommand(gameScheduler, guiService).register(getLifecycleManager());
        new TestPaintGuiCommand(gameScheduler, guiService).register(getLifecycleManager());

        getSLF4JLogger().info("Checking for other plugins...");
        if (faweReady) {
            getSLF4JLogger().info("FastAsyncWorldEdit plugin found; schematic features enabled.");
        } else {
            getSLF4JLogger().warn("FastAsyncWorldEdit plugin not found; arena schematic features disabled. "
                    + "Vanilla WorldEdit is not sufficient because the schematic paste runs off the main thread.");
        }
        if (wgReady) {
            getSLF4JLogger().info("WorldGuard plugin found; arena region enforcement enabled.");
        } else {
            getSLF4JLogger().warn("WorldGuard plugin not found; arena session creation disabled.");
        }

        getSLF4JLogger().info("Pvpgames enabled with {} kit(s) and {} arena(s) loaded.",
                kitRepository.all().size(), arenaRepository.all().size());
    }

    @Override
    public void onDisable() {
        // restore player state before deleting worlds
        if (arenaManager != null) {
            arenaManager.shutdown();
        }

        // delete world
        if (ownsArenaWorld && arenaWorld != null && worldService != null && worldGuardService != null) {
            try {
                getSLF4JLogger().info("Deleting world {}", ArenaName.SHARED_WORLD_NAME);
                worldGuardService.shutdown();
                worldService.deleteWorld(arenaWorld);
            } catch (WorldServiceException ex) {
                getSLF4JLogger().warn("Failed to delete shared arenas world on disable: {}", ex.getMessage());
            }
        }
    }

    /**
     * @return human-readable list of missing soft-dependencies, or
     *         {@code null} when all are present
     */
    private static String describeMissingDependencies(boolean faweReady, boolean wgReady) {
        List<String> missing = new ArrayList<>(2);
        if (!faweReady) missing.add("FastAsyncWorldEdit");
        if (!wgReady) missing.add("WorldGuard");
        if (missing.isEmpty()) return null;
        return "Dependencies [" + String.join(", ", missing) + "] are not installed.";
    }

    public KitService kitService() {
        return kitService;
    }

    public ArenaManager arenaManager() {
        return arenaManager;
    }

    public WorldService worldService() {
        return worldService;
    }

    public SchematicService schematicService() {
        return schematicService;
    }

    public WorldGuardService worldGuardService() {
        return worldGuardService;
    }
}
