package ax.xz.max.pvpgames;

import ax.xz.max.async.GameScheduler;
import ax.xz.max.pvpgames.arena.ArenaManager;
import ax.xz.max.pvpgames.arena.ArenaName;
import ax.xz.max.pvpgames.arena.ArenaRepository;
import ax.xz.max.pvpgames.arena.command.ArenaCommand;
import ax.xz.max.pvpgames.arena.internal.listener.ArenaSessionListener;
import ax.xz.max.pvpgames.arena.internal.manager.ArenaAllocator;
import ax.xz.max.pvpgames.arena.internal.manager.DefaultArenaManager;
import ax.xz.max.pvpgames.arena.internal.manager.PlayerStateCache;
import ax.xz.max.pvpgames.arena.internal.manager.UnavailableArenaManager;
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
import ax.xz.max.pvpgames.world.MultiverseWorldService;
import ax.xz.max.pvpgames.world.UnavailableWorldService;
import ax.xz.max.pvpgames.world.VoidChunkGenerator;
import ax.xz.max.pvpgames.world.WorldService;
import ax.xz.max.pvpgames.world.WorldServiceException;
import ax.xz.max.pvpgames.worldguard.BukkitWorldGuardService;
import ax.xz.max.pvpgames.worldguard.UnavailableWorldGuardService;
import ax.xz.max.pvpgames.worldguard.WorldGuardService;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
 * <p>Soft dependencies (Multiverse-Core, WorldEdit, WorldGuard) are detected
 * at enable; missing ones cause the corresponding service to be wired with
 * its {@code Unavailable*} stub instead of the real implementation. If any
 * of the three is missing, the arena manager is replaced with
 * {@link UnavailableArenaManager}; the shared arenas world is then NOT
 * created because no session can be opened anyway.
 */
public final class PvpgamesPlugin extends JavaPlugin {

    private GameScheduler gameScheduler;

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

        PluginManager pm = server.getPluginManager();
        boolean mvReady = pm.isPluginEnabled("Multiverse-Core");
        // We specifically require FastAsyncWorldEdit for asynchronous pasting
        boolean faweReady = pm.isPluginEnabled("FastAsyncWorldEdit");
        boolean wgReady = pm.isPluginEnabled("WorldGuard");

        this.worldService = mvReady
                ? new MultiverseWorldService(server, this, getSLF4JLogger())
                : new UnavailableWorldService();

        Path schematicsDir = getDataFolder().getParentFile().toPath()
                .resolve("WorldEdit").resolve("schematics");
        this.schematicService = faweReady
                ? new WorldEditSchematicService(schematicsDir, this, getSLF4JLogger())
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

        String missingDeps = describeMissingDependencies(mvReady, faweReady, wgReady);
        // todo: ugly pattern. use Optional instead
        if (missingDeps == null) {
            // todo: abstract this into its own cleanup function
            // Delete any leftover shared arenas
            try {
                worldService.deleteWorld(ArenaName.SHARED_WORLD_NAME);
            } catch (WorldServiceException ex) {
                getSLF4JLogger().warn("Failed to clean up shared arenas world on enable: {}",
                        ex.getMessage());
            }
            try {
                this.arenaWorld = worldService.getOrCreateVoidWorld(ArenaName.SHARED_WORLD_NAME);
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
                    server, Clock.systemUTC(), getSLF4JLogger());
        } else {
            this.arenaManager = new UnavailableArenaManager(arenaRepository, Clock.systemUTC(), missingDeps);
        }

        // register listeners
        server.getPluginManager().registerEvents(new ArenaSessionListener(arenaManager), this);

        // register commands
        new KitCommand(kitService, server).register(getLifecycleManager());
        new ArenaCommand(arenaManager, server).register(getLifecycleManager());
        new TestAsyncCommand(gameScheduler).register(getLifecycleManager());

        getSLF4JLogger().info("Checking for other plugins...");
        if (faweReady) {
            getSLF4JLogger().info("FastAsyncWorldEdit plugin found; schematic features enabled.");
        } else {
            getSLF4JLogger().warn("FastAsyncWorldEdit plugin not found; arena schematic features disabled. "
                    + "Vanilla WorldEdit is not sufficient because the schematic paste runs off the main thread.");
        }
        if (mvReady) {
            getSLF4JLogger().info("Multiverse-Core plugin found; shared arenas world enabled.");
        } else {
            getSLF4JLogger().warn("Multiverse-Core plugin not found; shared arenas world disabled.");
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
        // Restore every cached pre-session player state and close every
        // session BEFORE the shared world is deleted. Multiverse, WorldEdit,
        // and WorldGuard load before us (softdepend) and disable after us, so
        // their APIs are still alive here.
        if (arenaManager != null) {
            arenaManager.shutdown();
        }
        if (ownsArenaWorld && worldService != null && worldGuardService != null) {
            try {
                getSLF4JLogger().info("Deleting world {}", ArenaName.SHARED_WORLD_NAME);
                worldGuardService.shutdown();
                worldService.deleteWorld(ArenaName.SHARED_WORLD_NAME);
            } catch (WorldServiceException ex) {
                getSLF4JLogger().warn("Failed to delete shared arenas world on disable: {}", ex.getMessage());
            }
        }
    }

    /**
     * Paper / Bukkit asks the plugin for a {@link ChunkGenerator} when a world
     * is created with {@code generator: <plugin-name>}.
     * {@link MultiverseWorldService} passes our plugin name when creating the
     * shared arenas world, so this routes Multiverse to the
     * {@link VoidChunkGenerator} that produces empty void worlds.
     */
    @Override
    public @Nullable ChunkGenerator getDefaultWorldGenerator(@NotNull String worldName, @Nullable String id) {
        return new VoidChunkGenerator();
    }

    /**
     * @return human-readable list of missing soft-dependencies, or
     *         {@code null} when all are present
     */
    private static String describeMissingDependencies(boolean mvReady, boolean faweReady, boolean wgReady) {
        List<String> missing = new ArrayList<>(3);
        if (!mvReady) missing.add("Multiverse-Core");
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
