package ax.xz.max.pvpgames.arena;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/**
 * High-level arena management API used by command handlers and (later) game
 * lifecycle code.
 *
 * <p>All methods that take raw user input return sealed {@link ArenaResult}
 * variants so the caller can pattern-match on each failure mode (invalid name,
 * arena not found, dependency missing, I/O error) without try/catch chains.
 *
 * <p>Unless documented otherwise, methods must be invoked from the server main
 * thread because they read or mutate live {@link Player} state and load /
 * delete worlds.
 */
public interface ArenaService {

    /**
     * Creates a new arena named {@code rawArenaName} that references the
     * schematic {@code schematicName}. The schematic file does not have to
     * exist yet; existence is checked lazily at {@code preview} time.
     *
     * <p>If an arena with this name already exists, it is replaced. The
     * caller's {@link CommandSender} identity is recorded as the creator if
     * the sender is a player.
     */
    ArenaResult.CreateResult create(CommandSender creator, String rawArenaName, String schematicName);

    /**
     * Permanently deletes the arena named {@code rawArenaName}. Does not
     * touch the corresponding preview world if one is loaded; world cleanup
     * happens at plugin disable / enable.
     */
    ArenaResult.DeleteResult delete(String rawArenaName);

    /**
     * All known arena names, sorted alphabetically. Safe to call from
     * tab-completion suggestion providers.
     */
    List<ArenaName> listNames();

    /**
     * Looks up an arena by raw name. Returns {@link Optional#empty()} both when
     * the name is invalid and when no arena with that name exists.
     */
    Optional<Arena> find(String rawArenaName);

    /**
     * Loads (or reuses) the arena's preview world, pastes the schematic if the
     * world is fresh, captures the admin's current location and gamemode in a
     * session, and teleports them in as a spectator.
     */
    ArenaResult.PreviewResult preview(Player admin, String rawArenaName);

    /**
     * Adds a spawn point at the admin's current position and rotation to the
     * arena whose preview world they are currently in.
     */
    ArenaResult.AddSpawnResult addSpawnAtPlayer(Player admin);

    /**
     * Adds an explicit spawn point to the arena whose preview world the admin
     * is currently in. {@code yaw} and {@code pitch} may be {@code null}, in
     * which case the admin's current rotation is used (vanilla {@code /tp}
     * semantics).
     */
    ArenaResult.AddSpawnResult addSpawnExplicit(Player admin, double x, double y, double z, Float yaw, Float pitch);

    /**
     * Returns the spawn list for the arena whose preview world the admin is
     * currently in.
     */
    ArenaResult.ListSpawnResult listSpawns(Player admin);

    /**
     * Teleports the admin to spawn {@code oneBasedIndex} in the arena whose
     * preview world they are currently in.
     */
    ArenaResult.VisitSpawnResult visitSpawn(Player admin, int oneBasedIndex);

    /**
     * Removes spawn {@code oneBasedIndex} from the arena whose preview world
     * the admin is currently in.
     */
    ArenaResult.RemoveSpawnResult removeSpawn(Player admin, int oneBasedIndex);

    /**
     * Returns the admin to the location and gamemode they had before
     * {@link #preview}. If they have no active session,
     * {@link ArenaResult.LeaveResult.NoActiveSession} is returned.
     */
    ArenaResult.LeaveResult leave(Player admin);
}
