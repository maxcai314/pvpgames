package ax.xz.max.pvpgames.arena.internal.session;

import ax.xz.max.pvpgames.arena.Arena;
import ax.xz.max.pvpgames.arena.ArenaName;
import ax.xz.max.pvpgames.arena.ArenaPersistenceException;
import ax.xz.max.pvpgames.arena.ArenaRepository;
import ax.xz.max.pvpgames.arena.ArenaResult;
import ax.xz.max.pvpgames.arena.ArenaSession;
import ax.xz.max.pvpgames.arena.SpawnPoint;
import ax.xz.max.pvpgames.arena.internal.manager.PlayerStateCache;
import ax.xz.max.pvpgames.player.PlayerStateSnapshot;
import ax.xz.max.pvpgames.schematic.BlockVec3;
import ax.xz.max.pvpgames.worldguard.ProtectedArenaRegion;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;

/**
 * Default {@link ArenaSession} implementation. Created and registered by the
 * arena manager; not constructed directly by anything outside the arena
 * package.
 *
 * <p>Holds the live state for a single session: the arena snapshot (mutable
 * across spawn-list edits), the allocator slot it occupies, the WorldGuard
 * region around it, and references to the manager-owned helpers needed to
 * persist edits and snapshot players.
 */
public final class DefaultArenaSession implements ArenaSession {

    private final long id;
    private final World world;
    private final BlockVec3 origin;
    private final int slotIndex;
    private final ProtectedArenaRegion region;
    private final ArenaRepository repository;
    private final PlayerStateCache playerStateCache;

    private Arena arena;

    public DefaultArenaSession(
            long id,
            Arena arena,
            World world,
            BlockVec3 origin,
            int slotIndex,
            ProtectedArenaRegion region,
            ArenaRepository repository,
            PlayerStateCache playerStateCache) {
        this.id = id;
        this.arena = Objects.requireNonNull(arena, "arena");
        this.world = Objects.requireNonNull(world, "world");
        this.origin = Objects.requireNonNull(origin, "origin");
        this.slotIndex = slotIndex;
        this.region = Objects.requireNonNull(region, "region");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.playerStateCache = Objects.requireNonNull(playerStateCache, "playerStateCache");
    }

    @Override
    public long id() {
        return id;
    }

    @Override
    public ArenaName arenaName() {
        return arena.name();
    }

    @Override
    public World world() {
        return world;
    }

    @Override
    public BlockVec3 origin() {
        return origin;
    }

    @Override
    public Arena arena() {
        return arena;
    }

    /** Internal accessor used by the manager when closing the session. */
    public int slotIndex() {
        return slotIndex;
    }

    /** Internal accessor used by the manager when closing the session. */
    public ProtectedArenaRegion region() {
        return region;
    }

    @Override
    public Location toWorldLocation(SpawnPoint spawn) {
        Objects.requireNonNull(spawn, "spawn");
        return spawn.toWorldLocation(world, origin);
    }

    @Override
    public ArenaResult.JoinResult joinPlayer(Player player) {
        Objects.requireNonNull(player, "player");
        playerStateCache.captureIfAbsent(player);
        PlayerStateSnapshot.DEFAULT.applyTo(player);

        Location target = arena.spawns().isEmpty()
                ? new Location(world, origin.x() + 0.5, origin.y(), origin.z() + 0.5)
                : toWorldLocation(arena.spawns().getFirst());
        player.teleport(target);
        return new ArenaResult.JoinResult.Joined(this, arena.spawns().isEmpty());
    }

    @Override
    public ArenaResult.LeaveResult leavePlayer(Player player) {
        Objects.requireNonNull(player, "player");
        return playerStateCache.restore(player)
                ? new ArenaResult.LeaveResult.Returned()
                : new ArenaResult.LeaveResult.NoActiveSession();
    }

    @Override
    public ArenaResult.AddSpawnResult addSpawnAtPlayer(Player player) {
        Objects.requireNonNull(player, "player");
        SpawnPoint spawn = SpawnPoint.relativeTo(player.getLocation(), origin);
        return saveWithSpawn(spawn);
    }

    @Override
    public ArenaResult.AddSpawnResult addSpawnExplicit(
            Player player, double x, double y, double z, Float yaw, Float pitch) {
        Objects.requireNonNull(player, "player");
        Location playerLoc = player.getLocation();
        float effectiveYaw = yaw != null ? yaw : playerLoc.getYaw();
        float effectivePitch = pitch != null ? pitch : playerLoc.getPitch();
        SpawnPoint spawn = SpawnPoint.relativeTo(
                new Location(world, x, y, z, effectiveYaw, effectivePitch),
                origin);
        return saveWithSpawn(spawn);
    }

    private ArenaResult.AddSpawnResult saveWithSpawn(SpawnPoint spawn) {
        Arena updated = arena.addSpawn(spawn);
        try {
            repository.save(updated);
        } catch (ArenaPersistenceException ex) {
            return new ArenaResult.AddSpawnResult.IoError(ex.getMessage());
        }
        this.arena = updated;
        return new ArenaResult.AddSpawnResult.Added(updated, updated.spawns().size());
    }

    @Override
    public ArenaResult.ListSpawnResult listSpawns() {
        return new ArenaResult.ListSpawnResult.Listed(arena, arena.spawns());
    }

    @Override
    public ArenaResult.VisitSpawnResult visitSpawn(Player player, int oneBasedIndex) {
        Objects.requireNonNull(player, "player");
        List<SpawnPoint> spawns = arena.spawns();
        if (oneBasedIndex < 1 || oneBasedIndex > spawns.size()) {
            return new ArenaResult.VisitSpawnResult.IndexOutOfRange(oneBasedIndex, spawns.size());
        }
        SpawnPoint spawn = spawns.get(oneBasedIndex - 1);
        player.teleport(toWorldLocation(spawn));
        return new ArenaResult.VisitSpawnResult.Visited(arena, oneBasedIndex, spawn);
    }

    @Override
    public ArenaResult.RemoveSpawnResult removeSpawn(int oneBasedIndex) {
        List<SpawnPoint> spawns = arena.spawns();
        if (oneBasedIndex < 1 || oneBasedIndex > spawns.size()) {
            return new ArenaResult.RemoveSpawnResult.IndexOutOfRange(oneBasedIndex, spawns.size());
        }
        SpawnPoint removed = spawns.get(oneBasedIndex - 1);
        Arena updated = arena.removeSpawnAt(oneBasedIndex - 1);
        try {
            repository.save(updated);
        } catch (ArenaPersistenceException ex) {
            return new ArenaResult.RemoveSpawnResult.IoError(ex.getMessage());
        }
        this.arena = updated;
        return new ArenaResult.RemoveSpawnResult.Removed(updated, oneBasedIndex, removed);
    }

    @Override
    public boolean contains(Player player) {
        Objects.requireNonNull(player, "player");
        if (!Objects.equals(player.getWorld(), world)) {
            return false;
        }
        Location loc = player.getLocation();
        return region.contains(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
}
