package ax.xz.max.pvpgames.world;

import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.mvplugins.multiverse.core.MultiverseCoreApi;
import org.mvplugins.multiverse.core.world.LoadedMultiverseWorld;
import org.mvplugins.multiverse.core.world.MultiverseWorld;
import org.mvplugins.multiverse.core.world.WorldManager;
import org.mvplugins.multiverse.core.world.options.CreateWorldOptions;
import org.mvplugins.multiverse.core.world.options.DeleteWorldOptions;
import org.mvplugins.multiverse.core.utils.result.Attempt;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * {@link WorldService} backed by Multiverse-Core 5.x.
 *
 * <p>The vavr / Multiverse {@code Attempt} types are the only place this
 * project touches Multiverse's reactive surface; results are eagerly unwrapped
 * to plain Java types or {@link WorldServiceException} before leaving the
 * class, so the rest of the codebase stays vavr-free.
 *
 * <p>Worlds are created with the plugin's own {@link VoidChunkGenerator}
 * (resolved through Bukkit's {@code getDefaultWorldGenerator} on the
 * {@link Plugin} passed in).
 */
public final class MultiverseWorldService implements WorldService {

    private final Server server;
    private final Plugin plugin;
    private final Logger logger;

    public MultiverseWorldService(Server server, Plugin plugin, Logger logger) {
        this.server = Objects.requireNonNull(server, "server");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public World getOrCreateVoidWorld(String name) throws WorldServiceException {
        Objects.requireNonNull(name, "name");

        World existing = server.getWorld(name);
        if (existing != null) {
            return existing;
        }

        WorldManager worlds = api().getWorldManager();
        CreateWorldOptions options = CreateWorldOptions.worldName(name)
                .environment(World.Environment.NORMAL)
                .generator(plugin.getName())
                .generateStructures(false)
                .useSpawnAdjust(false);

        Attempt<LoadedMultiverseWorld, ?> result = worlds.createWorld(options);
        if (result.isFailure()) {
            throw new WorldServiceException(
                    "Failed to create world '" + name + "': " + readableFailure(result));
        }

        World bukkitWorld = result.get().getBukkitWorld().getOrNull();
        if (bukkitWorld == null) {
            throw new WorldServiceException(
                    "Multiverse created '" + name + "' but no Bukkit world is loaded.");
        }
        return bukkitWorld;
    }

    @Override
    public void deleteWorld(String name) throws WorldServiceException {
        Objects.requireNonNull(name, "name");
        WorldManager worlds = api().getWorldManager();

        MultiverseWorld mvWorld = worlds.getWorld(name).getOrNull();
        if (mvWorld == null) {
            // already gone
            return;
        }

        Attempt<String, ?> result = worlds.deleteWorld(DeleteWorldOptions.world(mvWorld));
        if (result.isFailure()) {
            throw new WorldServiceException(
                    "Failed to delete world '" + name + "': " + readableFailure(result));
        }
        logger.info("Deleted Multiverse world '{}'.", name);
    }

    @Override
    public boolean worldExists(String name) {
        return server.getWorld(name) != null;
    }

    @Override
    public Collection<String> findWorldsByPrefix(String prefix) {
        Objects.requireNonNull(prefix, "prefix");
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (World world : server.getWorlds()) {
            if (world.getName().toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
                matches.add(world.getName());
            }
        }
        return matches;
    }

    private MultiverseCoreApi api() throws WorldServiceException {
        if (!MultiverseCoreApi.isLoaded()) {
            throw new WorldServiceException("Multiverse-Core is not yet loaded.");
        }
        return MultiverseCoreApi.get();
    }

    private static String readableFailure(Attempt<?, ?> attempt) {
        // getFailureMessage returns Multiverse's localised Message; .raw() / fallback toString.
        try {
            return attempt.getFailureMessage().toString();
        } catch (Exception ignored) {
            return String.valueOf(attempt.getFailureReason());
        }
    }
}
