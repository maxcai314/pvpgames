package ax.xz.max.pvpgames.kit;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/**
 * High-level kit management API used by command handlers and (later) game
 * lifecycle code.
 *
 * <p>All methods take raw, unvalidated names and return sealed
 * {@link KitResult} variants so the caller can pattern-match on each failure
 * mode (invalid name, kit not found, I/O error) without try/catch chains.
 *
 * <p>Unless documented otherwise, methods must be invoked from the server
 * main thread because they touch live {@link Player} state.
 */
public interface KitService {

    /**
     * Snapshots {@code owner}'s inventory and saves it under {@code rawName}.
     * If a kit with this name already exists it is replaced.
     */
    KitResult.CreateResult create(Player owner, String rawName);

    /**
     * Replaces {@code target}'s inventory with the kit named {@code rawName}.
     */
    KitResult.LoadResult load(Player target, String rawName);

    /**
     * Permanently deletes the kit named {@code rawName}.
     */
    KitResult.DeleteResult delete(String rawName);

    /**
     * All known kit names, sorted alphabetically. Safe to call from
     * tab-completion suggestion providers.
     */
    List<KitName> listNames();

    /**
     * Looks up a kit by raw name. Returns {@link Optional#empty()} both when
     * the name is invalid and when no kit with that name exists.
     */
    Optional<Kit> find(String rawName);
}
