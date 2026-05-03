package ax.xz.max.pvpgames.kit;

import java.util.Collection;
import java.util.Optional;

/**
 * Persistent store of kits.
 *
 * <p>Implementations are responsible for durability: when {@link #save} or
 * {@link #delete} returns successfully, the change must survive a server
 * restart. Reads ({@link #all}, {@link #find}) are expected to be cheap and
 * safe to call from the server main thread; implementations typically back
 * them with an in-memory cache so that command handlers and tab-completion
 * suggestion providers do not perform disk I/O.
 *
 * <p>Implementations are not required to be thread-safe; the calling
 * {@link KitService} is responsible for marshalling concurrent calls.
 */
public interface KitRepository {

    /**
     * All currently known kits, in undefined order. Cheap.
     */
    Collection<Kit> all();

    /**
     * Returns the kit with this name, if any.
     */
    Optional<Kit> find(KitName name);

    /**
     * Inserts or replaces {@code kit}.
     *
     * @return {@code true} if a kit with this name already existed and was
     *         replaced; {@code false} if this was a fresh insertion.
     * @throws KitPersistenceException if the change could not be persisted
     */
    boolean save(Kit kit) throws KitPersistenceException;

    /**
     * Removes the kit with this name.
     *
     * @return {@code true} if a kit existed and was removed; {@code false} if
     *         no such kit was present.
     * @throws KitPersistenceException if the change could not be persisted
     */
    boolean delete(KitName name) throws KitPersistenceException;
}
