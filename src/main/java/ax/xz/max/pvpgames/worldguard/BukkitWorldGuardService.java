package ax.xz.max.pvpgames.worldguard;

import ax.xz.max.pvpgames.schematic.BlockVec3;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.FlagContext;
import com.sk89q.worldguard.protection.flags.InvalidFlagFormat;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.storage.StorageException;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.World;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link WorldGuardService} backed by WorldGuard 7.0.x, bound to a
 * single {@link World} at construction.
 *
 * <p>This is the only file in the plugin that imports
 * {@code com.sk89q.worldguard.*}; everything else interacts with WG
 * through the {@link WorldGuardService} interface and the opaque
 * {@link ProtectedArenaRegion} handle.
 *
 * <p>Each handle is backed by a single WorldGuard region today; the
 * inner {@link BukkitProtectedArenaRegion} owns the WG region id and
 * is the only place that knows the id format. Callers cannot read it,
 * so a future multi-region hardening (inner safe zone + outer no-build
 * ring, etc.) can replace this class without changing any external
 * surface.
 *
 * <p>Flags are parsed via WG's own {@link Flag#parseInput(FlagContext)},
 * which means the value strings accepted in {@code arena.flags()} are
 * exactly what WG accepts on the {@code /rg flag} command (for example
 * {@code allow} / {@code deny} for state flags, {@code true} /
 * {@code false} for boolean flags). All flags are applied to the
 * default group only; per-group overrides are out of scope.
 */
public final class BukkitWorldGuardService implements WorldGuardService {

    private static final String REGION_ID_PREFIX = "pvpgames_arena_";

    private final World world;
    private final Logger logger;

    /** Regions this service still considers open. */
    private final Set<BukkitProtectedArenaRegion> openRegions = ConcurrentHashMap.newKeySet();

    /** Monotonic counter for the underlying WG region ids. */
    private final AtomicLong nextRegionId = new AtomicLong(1);

    public BukkitWorldGuardService(World world, Logger logger) {
        this.world = Objects.requireNonNull(world, "world");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public World world() { return world; }

    @Override
    public ProtectedArenaRegion createRegion(BlockVec3 min, BlockVec3 max) throws WorldGuardException {
        Objects.requireNonNull(min, "min");
        Objects.requireNonNull(max, "max");

        String wgId = REGION_ID_PREFIX + nextRegionId.getAndIncrement();
        RegionManager manager = regionManager();
        if (manager.hasRegion(wgId)) {
            // hopefully doesn't happen
            throw new WorldGuardException("Region id collision: '" + wgId + "'.");
        }
        ProtectedCuboidRegion wgRegion = new ProtectedCuboidRegion(
                wgId,
                BlockVector3.at(min.x(), min.y(), min.z()),
                BlockVector3.at(max.x(), max.y(), max.z()));
        manager.addRegion(wgRegion);

        BukkitProtectedArenaRegion region = new BukkitProtectedArenaRegion(wgId, min, max);
        openRegions.add(region);
        return region;
    }

    @Override
    public void removeRegion(ProtectedArenaRegion region) {
        Objects.requireNonNull(region, "region");
        if (!(region instanceof BukkitProtectedArenaRegion impl)) {
            // foreign handle from a different WorldGuardService; ignore.
            return;
        }
        impl.deregister();
        openRegions.remove(impl);
    }

    @Override
    public void shutdown() {
        // drop any regions the caller did not close cleanly so the
        // saved region database does not keep ghost regions around.
        for (BukkitProtectedArenaRegion region : new HashSet<>(openRegions)) {
            region.deregister();
        }
        openRegions.clear();

        // Force a synchronous save so in-memory removals land on disk
        // before the world is unloaded.
        try {
            regionManager().save();
        } catch (WorldGuardException ex) {
            logger.warn("Failed to look up region manager for world '{}' on shutdown: {}",
                    world.getName(), ex.getMessage());
        } catch (StorageException ex) {
            logger.warn("Failed to save WorldGuard regions for world '{}' on shutdown: {}",
                    world.getName(), ex.getMessage());
        }
    }

    private RegionManager regionManager() throws WorldGuardException {
        WorldGuard wg = WorldGuard.getInstance(); // todo: singleton access pattern should be dependency-injected into object constructor
        if (wg == null) {
            throw new WorldGuardException("WorldGuard is not yet loaded.");
        }
        RegionContainer container = wg.getPlatform().getRegionContainer();
        if (container == null) {
            throw new WorldGuardException("WorldGuard region container is not yet initialised.");
        }
        RegionManager manager = container.get(BukkitAdapter.adapt(world));
        if (manager == null) {
            throw new WorldGuardException("WorldGuard has no region manager for world '"
                    + world.getName() + "'.");
        }
        return manager;
    }

    /**
     * Parses {@code rawValue} through {@code flag}'s own parser and stores the
     * result on the region. The wildcard helper preserves the {@code Flag<T>}
     * type binding across {@code parseInput} and {@code setFlag}.
     */
    private <T> void applyOneFlag(ProtectedRegion region, Flag<T> flag, String rawValue)
            throws WorldGuardException {
        try {
            T value = flag.parseInput(FlagContext.create()
                    .setInput(rawValue)
                    .build());
            region.setFlag(flag, value);
        } catch (InvalidFlagFormat ex) {
            throw new WorldGuardException("Invalid value '" + rawValue + "' for flag '"
                    + flag.getName() + "': " + ex.getMessage(), ex);
        }
    }

    /**
     * Single-WG-region implementation of {@link ProtectedArenaRegion}.
     * Lives inside this service so the underlying WG region id is
     * never exposed to callers.
     */
    private final class BukkitProtectedArenaRegion implements ProtectedArenaRegion {

        private final String wgId;
        private final BlockVec3 min;
        private final BlockVec3 max;

        BukkitProtectedArenaRegion(String wgId, BlockVec3 min, BlockVec3 max) {
            this.wgId = wgId;
            this.min = min;
            this.max = max;
        }

        @Override
        public boolean contains(int x, int y, int z) {
            return x >= min.x() && x <= max.x()
                    && y >= min.y() && y <= max.y()
                    && z >= min.z() && z <= max.z();
        }

        @Override
        public void applyFlags(Map<String, String> flags) throws WorldGuardException {
            Objects.requireNonNull(flags, "flags");
            if (flags.isEmpty()) {
                return;
            }

            RegionManager manager = regionManager();
            ProtectedRegion wgRegion = manager.getRegion(wgId);
            if (wgRegion == null) {
                throw new WorldGuardException("Region '" + wgId
                        + "' was removed before flags could be applied.");
            }
            FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
            for (Map.Entry<String, String> entry : flags.entrySet()) {
                String flagName = entry.getKey();
                String rawValue = entry.getValue();
                Flag<?> flag = registry.get(flagName);
                if (flag == null) {
                    throw new WorldGuardException("Unknown WorldGuard flag '" + flagName + "'.");
                }
                applyOneFlag(wgRegion, flag, rawValue);
            }
        }

        /** Remove the underlying WG region. Called by the enclosing service. */
        void deregister() {
            try {
                RegionManager manager = regionManager();
                if (manager.hasRegion(wgId)) {
                    manager.removeRegion(wgId);
                }
            } catch (WorldGuardException ex) {
                logger.warn("Failed to remove region '{}' on world '{}': {}",
                        wgId, world.getName(), ex.getMessage());
            }
        }
    }
}
