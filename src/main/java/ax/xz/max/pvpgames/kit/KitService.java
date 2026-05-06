package ax.xz.max.pvpgames.kit;

import ax.xz.max.async.Result;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/**
 * High-level kit management API used by command handlers and (later) game
 * lifecycle code.
 *
 * <p>Methods take raw, unvalidated names and return {@code Result<T, String>}
 * so callers can pattern-match on success / error in one place. Error
 * messages are pre-formatted by the service and meant to be shown directly
 * to players.
 *
 * <p>Unless documented otherwise, methods must be invoked from the server
 * main thread because they touch live {@link Player} state.
 */
public interface KitService {

    /**
     * Snapshots {@code owner}'s inventory and saves it under {@code rawName}.
     * If a kit with this name already exists it is replaced.
     *
     * @return on success the new kit and whether it replaced an existing
     *         one; on failure an error message that can be shown to players
     */
    Result<KitCreation, String> create(Player owner, String rawName);

    /**
     * Replaces {@code target}'s inventory with the kit named {@code rawName}.
     *
     * @return on success the loaded kit; on failure an error message that
     *         can be shown to players
     */
    Result<Kit, String> load(Player target, String rawName);

    /**
     * Permanently deletes the kit named {@code rawName}.
     *
     * @return {@code Ok(true)} if a kit was deleted, {@code Ok(false)} if no
     *         kit with that name existed, or {@code Err} for invalid names
     *         and I/O failures
     */
    Result<Boolean, String> delete(String rawName);

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
