package ax.xz.max.pvpgames.arena;

import java.util.Collection;
import java.util.Optional;

/**
 * Persistent store of arenas.
 *
 * <p>Implementations are responsible for durability: when {@link #save} or
 * {@link #delete} returns successfully, the change must survive a server
 * restart. Reads ({@link #all}, {@link #find}) are expected to be cheap and
 * safe to call from the server main thread; implementations typically back
 * them with an in-memory cache so that command handlers and tab-completion
 * suggestion providers do not perform disk I/O.
 *
 * <p>Implementations are not required to be thread-safe; the calling
 * {@link ArenaService} is responsible for marshalling concurrent calls.
 */
public interface ArenaRepository {

    /**
     * All currently known arenas, in undefined order. Cheap.
     */
    Collection<Arena> all();

    /**
     * Returns the arena with this name, if any.
     */
    Optional<Arena> find(ArenaName name);

    /**
     * Inserts or replaces {@code arena}.
     *
     * @return {@code true} if an arena with this name already existed and was
     *         replaced; {@code false} if this was a fresh insertion.
     * @throws ArenaPersistenceException if the change could not be persisted
     */
    boolean save(Arena arena) throws ArenaPersistenceException;

    /**
     * Removes the arena with this name.
     *
     * @return {@code true} if an arena existed and was removed; {@code false}
     *         if no such arena was present.
     * @throws ArenaPersistenceException if the change could not be persisted
     */
    boolean delete(ArenaName name) throws ArenaPersistenceException;
}
