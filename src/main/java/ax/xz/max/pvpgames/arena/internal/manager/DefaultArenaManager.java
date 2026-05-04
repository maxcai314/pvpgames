package ax.xz.max.pvpgames.arena.internal.manager;

import ax.xz.max.pvpgames.arena.Arena;
import ax.xz.max.pvpgames.arena.ArenaManager;
import ax.xz.max.pvpgames.arena.ArenaName;
import ax.xz.max.pvpgames.arena.ArenaPersistenceException;
import ax.xz.max.pvpgames.arena.ArenaRepository;
import ax.xz.max.pvpgames.arena.ArenaResult;
import ax.xz.max.pvpgames.arena.ArenaSession;
import ax.xz.max.pvpgames.arena.internal.session.DefaultArenaSession;
import ax.xz.max.pvpgames.command.NameParseResult;
import ax.xz.max.pvpgames.schematic.BlockVec3;
import ax.xz.max.pvpgames.schematic.SchematicException;
import ax.xz.max.pvpgames.schematic.SchematicName;
import ax.xz.max.pvpgames.schematic.SchematicService;
import ax.xz.max.pvpgames.worldguard.ProtectedArenaRegion;
import ax.xz.max.pvpgames.worldguard.WorldGuardException;
import ax.xz.max.pvpgames.worldguard.WorldGuardService;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.slf4j.Logger;

import java.time.Clock;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default {@link ArenaManager} implementation.
 *
 * <p>Owns the shared arenas world, the {@link ArenaAllocator} that hands out
 * grid origins for it, the pool of {@link ArenaSession}s, and the
 * {@link PlayerStateCache} that the sessions share. Persistent CRUD is
 * delegated to the injected {@link ArenaRepository}.
 *
 * <p>All methods must run on the server main thread; concurrent use is not
 * supported.
 */
public final class DefaultArenaManager implements ArenaManager {

    private static final String REGION_ID_PREFIX = "pvpgames_arena_";

    private final ArenaRepository repository;
    private final SchematicService schematicService;
    private final WorldGuardService worldGuardService;
    private final ArenaAllocator allocator;
    private final PlayerStateCache playerStateCache;
    private final World arenaWorld;
    private final Server server;
    private final Clock clock;
    private final Logger logger;

    private final Map<Long, DefaultArenaSession> sessionPool =
            Collections.synchronizedMap(new LinkedHashMap<>());
    private final AtomicLong nextSessionId = new AtomicLong(1);

    public DefaultArenaManager(
            ArenaRepository repository,
            SchematicService schematicService,
            WorldGuardService worldGuardService,
            ArenaAllocator allocator,
            PlayerStateCache playerStateCache,
            World arenaWorld,
            Server server,
            Clock clock,
            Logger logger) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.schematicService = Objects.requireNonNull(schematicService, "schematicService");
        this.worldGuardService = Objects.requireNonNull(worldGuardService, "worldGuardService");
        this.allocator = Objects.requireNonNull(allocator, "allocator");
        this.playerStateCache = Objects.requireNonNull(playerStateCache, "playerStateCache");
        this.arenaWorld = Objects.requireNonNull(arenaWorld, "arenaWorld");
        this.server = Objects.requireNonNull(server, "server");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    // ---- persistent CRUD ----------------------------------------------

    @Override
    public ArenaResult.CreateResult create(CommandSender creator, String rawArenaName, String rawSchematicName) {
        Objects.requireNonNull(creator, "creator");

        NameParseResult<ArenaName> parsedName = ArenaName.tryParse(rawArenaName);
        if (parsedName instanceof NameParseResult.Invalid<ArenaName>(String reason)) {
            return new ArenaResult.CreateResult.InvalidName(reason);
        }
        ArenaName name = ((NameParseResult.Valid<ArenaName>) parsedName).name();

        NameParseResult<SchematicName> parsedSchematic = SchematicName.tryParse(rawSchematicName);
        if (parsedSchematic instanceof NameParseResult.Invalid<SchematicName>(String reason)) {
            return new ArenaResult.CreateResult.InvalidSchematic(reason);
        }
        SchematicName schematic = ((NameParseResult.Valid<SchematicName>) parsedSchematic).name();

        Arena arena = new Arena(
                name,
                schematic,
                List.of(),
                Map.of(),
                clock.instant(),
                creator instanceof Player p ? p.getUniqueId() : null);
        try {
            boolean replaced = repository.save(arena);
            return new ArenaResult.CreateResult.Created(arena, replaced);
        } catch (ArenaPersistenceException ex) {
            return new ArenaResult.CreateResult.IoError(ex.getMessage());
        }
    }

    @Override
    public ArenaResult.DeleteResult delete(String rawArenaName) {
        NameParseResult<ArenaName> parsed = ArenaName.tryParse(rawArenaName);
        if (parsed instanceof NameParseResult.Invalid<ArenaName>(String reason)) {
            return new ArenaResult.DeleteResult.InvalidName(reason);
        }
        ArenaName name = ((NameParseResult.Valid<ArenaName>) parsed).name();

        try {
            boolean existed = repository.delete(name);
            return existed
                    ? new ArenaResult.DeleteResult.Deleted(name)
                    : new ArenaResult.DeleteResult.NotFound(rawArenaName);
        } catch (ArenaPersistenceException ex) {
            return new ArenaResult.DeleteResult.IoError(ex.getMessage());
        }
    }

    @Override
    public List<ArenaName> listNames() {
        return repository.all().stream()
                .map(Arena::name)
                .sorted(Comparator.comparing(ArenaName::value))
                .toList();
    }

    @Override
    public Optional<Arena> find(String rawArenaName) {
        NameParseResult<ArenaName> parsed = ArenaName.tryParse(rawArenaName);
        if (parsed instanceof NameParseResult.Valid<ArenaName>(ArenaName name)) {
            return repository.find(name);
        }
        return Optional.empty();
    }

    // ---- session lifecycle --------------------------------------------

    @Override
    public ArenaResult.OpenSessionResult openSession(String rawArenaName) {
        NameParseResult<ArenaName> parsed = ArenaName.tryParse(rawArenaName);
        if (parsed instanceof NameParseResult.Invalid<ArenaName>(String reason)) {
            return new ArenaResult.OpenSessionResult.InvalidName(reason);
        }
        ArenaName arenaName = ((NameParseResult.Valid<ArenaName>) parsed).name();

        Optional<Arena> maybeArena = repository.find(arenaName);
        if (maybeArena.isEmpty()) {
            return new ArenaResult.OpenSessionResult.NotFound(rawArenaName);
        }
        Arena arena = maybeArena.get();

        Optional<ArenaAllocator.Allocation> maybeSlot = allocator.allocate();
        if (maybeSlot.isEmpty()) {
            return new ArenaResult.OpenSessionResult.AllocatorExhausted(ArenaAllocator.MAX_SLOTS);
        }
        ArenaAllocator.Allocation allocation = maybeSlot.get();

        try {
            schematicService.pasteAtOrigin(arena.schematicName(), arenaWorld, allocation.origin());
        } catch (SchematicException.NotFound ex) {
            allocator.release(allocation.slotIndex());
            return new ArenaResult.OpenSessionResult.SchematicMissing(arena.schematicName().value());
        } catch (SchematicException ex) {
            allocator.release(allocation.slotIndex());
            return new ArenaResult.OpenSessionResult.SchematicLoadFailed(arena.schematicName().value(), ex.getMessage());
        }

        long sessionId = nextSessionId.getAndIncrement();
        String regionId = REGION_ID_PREFIX + sessionId;
        BlockVec3 min = allocator.minOf(allocation.slotIndex());
        BlockVec3 max = allocator.maxOf(allocation.slotIndex());

        ProtectedArenaRegion region;
        try {
            region = worldGuardService.createRegion(arenaWorld, regionId, min, max);
        } catch (WorldGuardException ex) {
            allocator.release(allocation.slotIndex());
            return new ArenaResult.OpenSessionResult.RegionFailed(ex.getMessage());
        }

        try {
            worldGuardService.applyFlags(region, arena.flags());
        } catch (WorldGuardException ex) {
            worldGuardService.removeRegion(arenaWorld, regionId);
            allocator.release(allocation.slotIndex());
            return new ArenaResult.OpenSessionResult.InvalidFlag(
                    extractFlagName(ex.getMessage(), arena.flags()), ex.getMessage());
        }

        DefaultArenaSession session = new DefaultArenaSession(
                sessionId, arena, arenaWorld, allocation.origin(), allocation.slotIndex(),
                region, repository, playerStateCache);
        sessionPool.put(sessionId, session);

        logger.info("Opened arena session {} ('{}') at slot {} origin ({}, {}, {}).",
                sessionId, arena.name().value(), allocation.slotIndex(),
                allocation.origin().x(), allocation.origin().y(), allocation.origin().z());
        return new ArenaResult.OpenSessionResult.Opened(session, arena.spawns().isEmpty());
    }

    @Override
    public ArenaResult.CloseSessionResult closeSession(long sessionId) {
        DefaultArenaSession session = sessionPool.remove(sessionId);
        if (session == null) {
            return new ArenaResult.CloseSessionResult.NotFound(sessionId);
        }
        worldGuardService.removeRegion(arenaWorld, session.region().regionId());
        allocator.release(session.slotIndex());
        logger.info("Closed arena session {} ('{}').", sessionId, session.arenaName().value());
        return new ArenaResult.CloseSessionResult.Closed(sessionId);
    }

    @Override
    public Optional<ArenaSession> findSession(long sessionId) {
        return Optional.ofNullable(sessionPool.get(sessionId));
    }

    @Override
    public Optional<ArenaSession> findSessionFor(Player player) {
        Objects.requireNonNull(player, "player");
        if (!Objects.equals(player.getWorld(), arenaWorld)) {
            return Optional.empty();
        }
        synchronized (sessionPool) {
            for (DefaultArenaSession session : sessionPool.values()) {
                if (session.contains(player)) {
                    return Optional.of(session);
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Collection<ArenaSession> activeSessions() {
        synchronized (sessionPool) {
            return List.copyOf(sessionPool.values());
        }
    }

    @Override
    public void shutdown() {
        // Restore every cached player; do this before closing sessions so the
        // restoration teleport happens out of the arena world cleanly.
        for (UUID playerId : playerStateCache.cachedPlayers()) {
            Player online = server.getPlayer(playerId);
            if (online != null) {
                playerStateCache.restore(online);
            } else {
                logger.warn("Player {} was in an arena session but is offline at shutdown; their state is being discarded.",
                        playerId);
                playerStateCache.forget(playerId);
            }
        }
        // Close every session: removes WG regions and releases allocator slots.
        List<Long> ids;
        synchronized (sessionPool) {
            ids = List.copyOf(sessionPool.keySet());
        }
        for (long id : ids) {
            closeSession(id);
        }
        // Force WorldGuard to flush region removals to disk before the
        // arena world is deleted; otherwise the region database files
        // outlive the world directory and ghost regions appear on the
        // next plugin enable.
        worldGuardService.shutdown();
    }

    /**
     * Best-effort recovery of which flag name caused the
     * {@link WorldGuardException}. Used purely for the player-facing
     * {@link ArenaResult.OpenSessionResult.InvalidFlag} message; if no name
     * can be guessed, returns an empty string and the full reason still
     * carries the detail.
     */
    private static String extractFlagName(String message, Map<String, String> flags) {
        if (message == null) return "";
        for (String name : flags.keySet()) {
            if (message.contains("'" + name + "'")) {
                return name;
            }
        }
        return "";
    }
}
