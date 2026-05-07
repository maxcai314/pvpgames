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
import org.bukkit.Location;
import org.bukkit.World;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link WorldGuardService} backed by WorldGuard 7.0.x.
 *
 * <p>This is the only file in the plugin that imports
 * {@code com.sk89q.worldguard.*}; everything else interacts with WG through
 * the {@link WorldGuardService} interface and the
 * {@link ProtectedArenaRegion} record.
 *
 * <p>Flags are parsed via WG's own {@link Flag#parseInput(FlagContext)}, which
 * means the value strings accepted in {@code arena.flags()} are exactly what
 * WG accepts on the {@code /rg flag} command (for example {@code allow} /
 * {@code deny} for state flags, {@code true} / {@code false} for boolean
 * flags). All flags are applied to the default group only; per-group overrides
 * are out of scope for this service.
 */
public final class BukkitWorldGuardService implements WorldGuardService {

    private final Logger logger;

    /**
     * Worlds whose region manager has been mutated by this service.
     * {@link #shutdown()} forces a save on each so in-memory removals persist
     * to disk before the world is unloaded; without this WorldGuard's
     * automatic save during world unload runs after Multiverse has already
     * deleted the world directory.
     */
    private final Set<World> touchedWorlds = ConcurrentHashMap.newKeySet();

    /**
     * Regions this service still considers open (created but not yet
     * removed). Used by {@link #shutdown()} to clear leftover sessions if
     * the manager did not close them cleanly.
     * todo: should probably be handled by the ArenaManager instead
     */
    private final Set<RegionRef> openRegions = ConcurrentHashMap.newKeySet();

    public BukkitWorldGuardService(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public ProtectedArenaRegion createRegion(World world, String regionId, BlockVec3 min, BlockVec3 max)
            throws WorldGuardException {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(regionId, "regionId");
        Objects.requireNonNull(min, "min");
        Objects.requireNonNull(max, "max");

        RegionManager manager = regionManagerFor(world);
        if (manager.hasRegion(regionId)) {
            throw new WorldGuardException("Region '" + regionId + "' already exists in world '"
                    + world.getName() + "'.");
        }
        ProtectedCuboidRegion region = new ProtectedCuboidRegion(
                regionId,
                BlockVector3.at(min.x(), min.y(), min.z()),
                BlockVector3.at(max.x(), max.y(), max.z()));
        manager.addRegion(region);
        touchedWorlds.add(world);
        openRegions.add(new RegionRef(world, regionId));
        return new ProtectedArenaRegion(world, regionId, min, max);
    }

    @Override
    public void removeRegion(World world, String regionId) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(regionId, "regionId");

        RegionManager manager;
        try {
            manager = regionManagerFor(world);
        } catch (WorldGuardException ex) {
            logger.warn("Failed to look up region manager for world '{}': {}",
                    world.getName(), ex.getMessage());
            return;
        }
        openRegions.remove(new RegionRef(world, regionId));
        if (!manager.hasRegion(regionId)) {
            return;
        }
        manager.removeRegion(regionId);
        touchedWorlds.add(world);
    }

    @Override
    public void applyFlags(ProtectedArenaRegion region, Map<String, String> flags) throws WorldGuardException {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(flags, "flags");
        if (flags.isEmpty()) {
            return;
        }

        RegionManager manager = regionManagerFor(region.world());
        ProtectedRegion wgRegion = manager.getRegion(region.regionId());
        if (wgRegion == null) {
            throw new WorldGuardException("Region '" + region.regionId()
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

    @Override
    public boolean contains(World world, String regionId, Location loc) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(regionId, "regionId");
        Objects.requireNonNull(loc, "loc");
        if (!Objects.equals(loc.getWorld(), world)) {
            return false;
        }
        RegionManager manager;
        try {
            manager = regionManagerFor(world);
        } catch (WorldGuardException ex) {
            return false;
        }
        ProtectedRegion region = manager.getRegion(regionId);
        if (region == null) {
            return false;
        }
        return region.contains(BlockVector3.at(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
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

    @Override
    public void shutdown() {
        // Drop any sessions the caller did not close cleanly so the saved
        // region database does not contain ghost regions.
        Set<RegionRef> leftover = new HashSet<>(openRegions);
        for (RegionRef ref : leftover) {
            try {
                RegionManager manager = regionManagerFor(ref.world());
                if (manager.hasRegion(ref.regionId())) {
                    manager.removeRegion(ref.regionId());
                }
                touchedWorlds.add(ref.world());
            } catch (WorldGuardException ex) {
                logger.warn("Failed to remove leftover region '{}' in world '{}' on shutdown: {}",
                        ref.regionId(), ref.world().getName(), ex.getMessage());
            }
        }
        openRegions.clear();

        // Force a synchronous save of every region manager we touched.
        for (World world : touchedWorlds) {
            try {
                RegionManager manager = regionManagerFor(world);
                manager.save();
            } catch (WorldGuardException ex) {
                logger.warn("Failed to look up region manager for world '{}' on shutdown: {}",
                        world.getName(), ex.getMessage());
            } catch (StorageException ex) {
                logger.warn("Failed to save WorldGuard regions for world '{}' on shutdown: {}",
                        world.getName(), ex.getMessage());
            }
        }
        touchedWorlds.clear();
    }

    private RegionManager regionManagerFor(World world) throws WorldGuardException {
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

    /** A (world, regionId) pair tracked so shutdown can clean up reliably. */
    private record RegionRef(World world, String regionId) {}
}
