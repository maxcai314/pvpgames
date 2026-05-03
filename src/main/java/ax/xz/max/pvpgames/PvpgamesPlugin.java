package ax.xz.max.pvpgames;

import ax.xz.max.pvpgames.arena.ArenaName;
import ax.xz.max.pvpgames.arena.ArenaRepository;
import ax.xz.max.pvpgames.arena.ArenaService;
import ax.xz.max.pvpgames.arena.command.ArenaCommand;
import ax.xz.max.pvpgames.arena.internal.ArenaSessionRegistry;
import ax.xz.max.pvpgames.arena.internal.DefaultArenaService;
import ax.xz.max.pvpgames.arena.internal.FileArenaRepository;
import ax.xz.max.pvpgames.kit.KitRepository;
import ax.xz.max.pvpgames.kit.KitService;
import ax.xz.max.pvpgames.kit.command.KitCommand;
import ax.xz.max.pvpgames.kit.internal.DefaultKitService;
import ax.xz.max.pvpgames.kit.internal.FileKitRepository;
import ax.xz.max.pvpgames.schematic.SchematicService;
import ax.xz.max.pvpgames.schematic.UnavailableSchematicService;
import ax.xz.max.pvpgames.schematic.WorldEditSchematicService;
import ax.xz.max.pvpgames.server.BukkitServerHelper;
import ax.xz.max.pvpgames.server.ServerHelper;
import ax.xz.max.pvpgames.world.MultiverseWorldService;
import ax.xz.max.pvpgames.world.UnavailableWorldService;
import ax.xz.max.pvpgames.world.VoidChunkGenerator;
import ax.xz.max.pvpgames.world.WorldService;
import ax.xz.max.pvpgames.world.WorldServiceException;
import org.bukkit.Server;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    private WorldService worldService;
    private SchematicService schematicService;

    private KitRepository kitRepository;
    private KitService kitService;

    private ArenaRepository arenaRepository;
    private ArenaService arenaService;
    private ArenaSessionRegistry arenaSessions;

    @Override
    public void onEnable() {
        Server server = getServer();

        this.serverHelper = new BukkitServerHelper(server, this);

        PluginManager pm = server.getPluginManager();
        boolean mvReady = pm.isPluginEnabled("Multiverse-Core");
        boolean weReady = pm.isPluginEnabled("WorldEdit");

        this.worldService = mvReady
                ? new MultiverseWorldService(server, this, getSLF4JLogger())
                : new UnavailableWorldService();

        Path schematicsDir = getDataFolder().getParentFile().toPath()
                .resolve("WorldEdit").resolve("schematics");
        this.schematicService = weReady
                ? new WorldEditSchematicService(schematicsDir, getSLF4JLogger())
                : new UnavailableSchematicService();

        // Sweep leftover preview worlds from a crashed prior run before we let
        // the arena service near them. Quietly skipped when Multiverse is absent.
        sweepArenaWorlds("on enable");

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

        this.arenaSessions = new ArenaSessionRegistry();
        server.getPluginManager().registerEvents(arenaSessions, this);

        this.arenaRepository = new FileArenaRepository(arenasDir, getSLF4JLogger());
        this.arenaService = new DefaultArenaService(
                arenaRepository, worldService, schematicService,
                arenaSessions, Clock.systemUTC(), getSLF4JLogger());

        // register commands
        new KitCommand(kitService, serverHelper).register(getLifecycleManager());
        new ArenaCommand(arenaService, serverHelper).register(getLifecycleManager());

        getSLF4JLogger().info("Checking for other plugins...");
        if (weReady) {
            getSLF4JLogger().info("WorldEdit plugin found; schematic features enabled.");
        } else {
            getSLF4JLogger().warn("WorldEdit plugin not found; arena schematic features disabled.");
        }
        if (mvReady) {
            getSLF4JLogger().info("Multiverse-Core plugin found; arena preview worlds enabled.");
        } else {
            getSLF4JLogger().warn("Multiverse-Core plugin not found; arena preview worlds disabled.");
        }

        getSLF4JLogger().info("Pvpgames enabled with {} kit(s) and {} arena(s) loaded.",
                kitRepository.all().size(), arenaRepository.all().size());
    }

    @Override
    public void onDisable() {
        // Clean up any preview worlds we created so a fresh enable starts clean.
        // Multiverse loads before us (softdepend) and disables after us, so its
        // API is still available here.
        sweepArenaWorlds("on disable");
    }

    /**
     * Paper / Bukkit asks the plugin for a {@link ChunkGenerator} when a world
     * is created with {@code generator: <plugin-name>}. {@link MultiverseWorldService}
     * passes our plugin name when creating preview worlds, so this routes Multiverse
     * to the {@link VoidChunkGenerator} that produces empty void worlds.
     */
    @Override
    public @Nullable ChunkGenerator getDefaultWorldGenerator(@NotNull String worldName, @Nullable String id) {
        return new VoidChunkGenerator();
    }

    private void sweepArenaWorlds(String phase) {
        if (worldService == null) return;
        for (String name : worldService.findWorldsByPrefix(ArenaName.WORLD_PREFIX)) {
            try {
                worldService.deleteWorld(name);
                getSLF4JLogger().info("Cleaned up arena world '{}' {}.", name, phase);
            } catch (WorldServiceException ex) {
                getSLF4JLogger().warn("Failed to clean up arena world '{}' {}: {}", name, phase, ex.getMessage());
            }
        }
    }

    public ServerHelper serverHelper() {
        return serverHelper;
    }

    public KitService kitService() {
        return kitService;
    }

    public ArenaService arenaService() {
        return arenaService;
    }

    public WorldService worldService() {
        return worldService;
    }

    public SchematicService schematicService() {
        return schematicService;
    }
}
