package ax.xz.max.pvpgames.world;

import org.bukkit.World;

import java.util.List;

/**
 * Fallback {@link WorldService} used when Multiverse-Core is not installed.
 *
 * <p>Mutating operations throw {@link WorldServiceException}; query operations
 * return safe empty values. The plugin enables and the rest of the system
 * (kits, arena data, etc.) works fine; world-touching arena commands surface
 * the missing-dependency message.
 */
public final class UnavailableWorldService implements WorldService {

    public static final String MESSAGE = "Multiverse-Core is not installed.";

    @Override
    public World getOrCreateVoidWorld(String name) throws WorldServiceException {
        throw new WorldServiceException(MESSAGE);
    }

    @Override
    public void deleteWorld(String name) throws WorldServiceException {
        throw new WorldServiceException(MESSAGE);
    }

    @Override
    public boolean worldExists(String name) {
        return false;
    }

    @Override
    public List<String> findWorldsByPrefix(String prefix) {
        return List.of();
    }
}
