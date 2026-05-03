package ax.xz.max.pvpgames.world;

import org.bukkit.World;

import java.util.Collection;

/**
 * Abstraction over a third-party world-management plugin (currently
 * Multiverse-Core). Lets the rest of the plugin ask for void worlds without
 * importing the dependency directly.
 *
 * <p>When the dependency is absent, an {@code UnavailableWorldService} is wired
 * in instead so the rest of the plugin keeps working. Every method on the
 * fallback throws {@link WorldServiceException} with a clear "plugin missing"
 * message.
 */
public interface WorldService {

    /**
     * Returns the existing {@link World} with the given name if one is loaded,
     * otherwise creates a new void world (no terrain, no biomes, just air) and
     * returns it.
     *
     * @throws WorldServiceException if creation fails or the world manager is
     *         unavailable
     */
    World getOrCreateVoidWorld(String name) throws WorldServiceException;

    /**
     * Unloads and deletes the named world, including its files on disk.
     *
     * @throws WorldServiceException if the world cannot be removed
     */
    void deleteWorld(String name) throws WorldServiceException;

    /**
     * @return {@code true} if a world with this name is currently loaded
     */
    boolean worldExists(String name);

    /**
     * Returns the names of all loaded worlds whose names start with
     * {@code prefix}. Used to clean up plugin-managed temporary worlds at
     * enable / disable.
     */
    Collection<String> findWorldsByPrefix(String prefix);
}
