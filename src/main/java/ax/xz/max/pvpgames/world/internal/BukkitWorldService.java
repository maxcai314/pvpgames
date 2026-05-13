package ax.xz.max.pvpgames.world.internal;

import ax.xz.max.pvpgames.world.WorldService;
import ax.xz.max.pvpgames.world.WorldServiceException;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * {@link WorldService} backed directly by Bukkit's {@link WorldCreator}
 * and a {@link VoidChunkGenerator}. No third-party world-management
 * plugin is involved; this is everything we need for the arenas world.
 *
 * <p>{@link #createVoidWorld} is fresh-start semantics: any leftover
 * directory or loaded world with the same name is wiped before the new
 * world is created. That folds the prior "delete-then-create" two-step
 * at the call site into a single call.
 */
public final class BukkitWorldService implements WorldService {

    private final Server server;
    private final Logger logger;

    public BukkitWorldService(Server server, Logger logger) {
        this.server = Objects.requireNonNull(server, "server");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public World createVoidWorld(String name) throws WorldServiceException {
        Objects.requireNonNull(name, "name");

        // delete any pre-existing world for this name
        World stale = server.getWorld(name);
        if (stale != null) {
            logger.info("Retrieved existing world with name {}, deleting", name);
            deleteWorld(stale);
        }

        WorldCreator creator = new WorldCreator(name)
                .environment(World.Environment.NORMAL)
                .type(WorldType.FLAT)
                .generator(new VoidChunkGenerator())
                .generateStructures(false);

        World world = server.createWorld(creator);
        if (world == null) {
            throw new WorldServiceException(
                    "Bukkit returned null for createWorld('" + name + "').");
        }
        return world;
    }

    @Override
    public void deleteWorld(World world) throws WorldServiceException {
        Objects.requireNonNull(world, "world");
        Path dir = world.getWorldFolder().toPath();
        String name = world.getName();

        // save=false: about to delete the directory anyway.
        if (!server.unloadWorld(world, false)) {
            throw new WorldServiceException(
                    "Bukkit refused to unload world '" + name + "'.");
        }
        if (Files.exists(dir)) {
            deleteRecursively(dir);
            logger.info("Deleted world directory {}.", dir);
        }
    }

    /**
     * Recursive directory delete. Walks in reverse order so children are
     * removed before their parents, then deletes each entry one by one.
     */
    private static void deleteRecursively(Path root) throws WorldServiceException {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
        } catch (IOException | UncheckedIOException ex) {
            throw new WorldServiceException(
                    "Failed to delete world directory '" + root + "'.", ex);
        }
    }
}
