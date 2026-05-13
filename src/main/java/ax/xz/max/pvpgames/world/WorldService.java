package ax.xz.max.pvpgames.world;

import ax.xz.max.pvpgames.world.internal.VoidChunkGenerator;
import org.bukkit.World;
import org.bukkit.WorldCreator;

/**
 * Plugin-managed temporary void worlds. Backed by Bukkit's
 * {@link WorldCreator} and a custom {@link VoidChunkGenerator}, so the
 * resulting worlds contain no blocks until something (the schematic
 * service, in our case) writes into them.
 *
 * <p>The plugin owns every world it creates through this interface: each
 * is created at enable, deleted at disable, and never persists across
 * server restarts.
 *
 * <p>All methods must run on the server main thread.
 */
public interface WorldService {

    /**
     * Creates a fresh void world named {@code name}. Any pre-existing
     * loaded world or on-disk directory with the same name is wiped
     * first, so the returned world is always empty.
     *
     * @return the freshly created world
     * @throws WorldServiceException if Bukkit refuses to create the
     *         world or a leftover directory cannot be deleted
     */
    World createVoidWorld(String name) throws WorldServiceException;

    /**
     * Unloads {@code world} without saving and deletes its directory on
     * disk. Calling this with an already-unloaded {@link World} reference
     * is undefined; the caller should drop the reference after this
     * returns.
     *
     * @throws WorldServiceException if Bukkit refuses to unload the
     *         world or the directory cannot be fully removed
     */
    void deleteWorld(World world) throws WorldServiceException;
}
