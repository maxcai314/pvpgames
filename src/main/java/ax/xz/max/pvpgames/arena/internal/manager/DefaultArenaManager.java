package ax.xz.max.pvpgames.arena.internal.manager;

import ax.xz.max.async.GameExecutor;
import ax.xz.max.async.GameScheduler;
import ax.xz.max.async.Promise;
import ax.xz.max.async.Result;
import ax.xz.max.pvpgames.arena.Arena;
import ax.xz.max.pvpgames.arena.ArenaCreation;
import ax.xz.max.pvpgames.arena.ArenaManager;
import ax.xz.max.pvpgames.arena.ArenaName;
import ax.xz.max.pvpgames.arena.ArenaPersistenceException;
import ax.xz.max.pvpgames.arena.ArenaRepository;
import ax.xz.max.pvpgames.arena.ArenaSession;
import ax.xz.max.pvpgames.arena.internal.session.DefaultArenaSession;
import ax.xz.max.pvpgames.schematic.BlockVec3;
import ax.xz.max.pvpgames.schematic.SchematicError;
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
 * <p>Owns the shared arenas world, the {@link ArenaAllocator}, the
 * {@link ArenaSession} pool, and the {@link PlayerStateCache} the sessions
 * share. Persistent CRUD is delegated to the injected {@link ArenaRepository}.
 *
 * <p>{@link #openSession} is the only async entry point. It splits into
 * three phases:
 * <ol>
 *   <li><b>prelude</b> on main: parse the name, look up the arena, allocate
 *       a grid slot.</li>
 *   <li><b>paste</b> on async: paste the schematic via the
 *       {@link SchematicService}'s background promise.</li>
 *   <li><b>finalize</b> on main: register the WorldGuard region, apply the
 *       arena's flags, and add the session to the pool.</li>
 * </ol>
 * Failures at any phase short-circuit the chain with an error message and
 * release any resources that were already acquired.
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
    private final GameScheduler scheduler;
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
            GameScheduler scheduler,
            Clock clock,
            Logger logger) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.schematicService = Objects.requireNonNull(schematicService, "schematicService");
        this.worldGuardService = Objects.requireNonNull(worldGuardService, "worldGuardService");
        this.allocator = Objects.requireNonNull(allocator, "allocator");
        this.playerStateCache = Objects.requireNonNull(playerStateCache, "playerStateCache");
        this.arenaWorld = Objects.requireNonNull(arenaWorld, "arenaWorld");
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    // ---- persistent CRUD ----------------------------------------------

    @Override
    public Result<ArenaCreation, String> create(CommandSender creator, String rawArenaName, String rawSchematicName) {
        Objects.requireNonNull(creator, "creator");

        Result<ArenaName, String> parsedName = ArenaName.tryParse(rawArenaName);
        if (parsedName instanceof Result.Err<ArenaName, String>(String reason)) {
            return new Result.Err<>(reason);
        }
        ArenaName name = ((Result.Ok<ArenaName, String>) parsedName).val();

        Result<SchematicName, String> parsedSchematic = SchematicName.tryParse(rawSchematicName);
        if (parsedSchematic instanceof Result.Err<SchematicName, String>(String reason)) {
            return new Result.Err<>(reason);
        }
        SchematicName schematic = ((Result.Ok<SchematicName, String>) parsedSchematic).val();

        Arena arena = new Arena(
                name,
                schematic,
                List.of(),
                Map.of(),
                clock.instant(),
                creator instanceof Player p ? p.getUniqueId() : null);
        try {
            boolean replaced = repository.save(arena);
            return new Result.Ok<>(new ArenaCreation(arena, replaced));
        } catch (ArenaPersistenceException ex) {
            return new Result.Err<>("Could not save arena: " + ex.getMessage());
        }
    }

    @Override
    public Result<Boolean, String> delete(String rawArenaName) {
        Result<ArenaName, String> parsed = ArenaName.tryParse(rawArenaName);
        if (parsed instanceof Result.Err<ArenaName, String>(String reason)) {
            return new Result.Err<>(reason);
        }
        ArenaName name = ((Result.Ok<ArenaName, String>) parsed).val();

        try {
            return new Result.Ok<>(repository.delete(name));
        } catch (ArenaPersistenceException ex) {
            return new Result.Err<>("Could not delete arena: " + ex.getMessage());
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
        Result<ArenaName, String> parsed = ArenaName.tryParse(rawArenaName);
        if (parsed instanceof Result.Ok<ArenaName, String>(ArenaName name)) {
            return repository.find(name);
        }
        return Optional.empty();
    }

    // ---- session lifecycle --------------------------------------------

    @Override
    public Promise<Result<ArenaSession, String>> openSession(String rawArenaName) {
        GameExecutor main = scheduler.mainExecutor();

        // prelude (main) -> paste (async, run by SchematicService) -> finalize (main).
        return Promise.supplyAsync(() -> prepareOpen(rawArenaName), main)
                .thenComposeAsync(prelude -> switch (prelude) {
                    case Result.Err<Prelude, String>(String err) ->
                            Promise.<Result<ArenaSession, String>>completedFuture(new Result.Err<>(err));
                    case Result.Ok<Prelude, String>(Prelude p) ->
                            schematicService.pasteAtOrigin(
                                            p.arena().schematicName(), arenaWorld, p.allocation().origin())
                                    .thenApplyAsync(paste -> finalizeOpen(p, paste), main);
                }, main);
    }

    /**
     * Phase 1, on the main thread. Validates the name, looks up the arena,
     * and reserves a grid slot. Returns the bundled prelude data on success
     * or a player-facing error message on failure.
     */
    private Result<Prelude, String> prepareOpen(String rawArenaName) {
        Result<ArenaName, String> parsed = ArenaName.tryParse(rawArenaName);
        if (parsed instanceof Result.Err<ArenaName, String>(String reason)) {
            return new Result.Err<>(reason);
        }
        ArenaName arenaName = ((Result.Ok<ArenaName, String>) parsed).val();

        Optional<Arena> maybeArena = repository.find(arenaName);
        if (maybeArena.isEmpty()) {
            return new Result.Err<>("No arena named '" + rawArenaName + "' exists.");
        }
        Arena arena = maybeArena.get();

        Optional<ArenaAllocator.Allocation> maybeSlot = allocator.allocate();
        if (maybeSlot.isEmpty()) {
            return new Result.Err<>("All " + ArenaAllocator.MAX_SLOTS
                    + " arena slots are in use; close a session and try again.");
        }
        return new Result.Ok<>(new Prelude(arena, maybeSlot.get()));
    }

    /**
     * Phase 3, on the main thread. Runs after the schematic paste finishes.
     * Translates a paste failure into a player-facing message, otherwise
     * registers the WorldGuard region, applies the arena's flags, and adds
     * the session to the pool.
     */
    private Result<ArenaSession, String> finalizeOpen(
            Prelude p, Result<Void, SchematicError> pasteResult) {
        if (pasteResult instanceof Result.Err<Void, SchematicError>(SchematicError err)) {
            allocator.release(p.allocation().slotIndex());
            String name = p.arena().schematicName().value();
            return new Result.Err<>(switch (err) {
                case SchematicError.NotFound ignored -> "Schematic '" + name + "' was not found.";
                case SchematicError.LoadFailed lf ->
                        "Could not load schematic '" + name + "': " + lf.message();
            });
        }

        long sessionId = nextSessionId.getAndIncrement();
        String regionId = REGION_ID_PREFIX + sessionId;
        BlockVec3 min = allocator.minOf(p.allocation().slotIndex());
        BlockVec3 max = allocator.maxOf(p.allocation().slotIndex());

        ProtectedArenaRegion region;
        try {
            region = worldGuardService.createRegion(arenaWorld, regionId, min, max);
        } catch (WorldGuardException ex) {
            allocator.release(p.allocation().slotIndex());
            return new Result.Err<>("Could not register WorldGuard region: " + ex.getMessage());
        }

        try {
            worldGuardService.applyFlags(region, p.arena().flags());
        } catch (WorldGuardException ex) {
            worldGuardService.removeRegion(arenaWorld, regionId);
            allocator.release(p.allocation().slotIndex());
            String flagName = extractFlagName(ex.getMessage(), p.arena().flags());
            return new Result.Err<>("Could not apply flag '" + flagName + "': " + ex.getMessage());
        }

        DefaultArenaSession session = new DefaultArenaSession(
                sessionId, p.arena(), arenaWorld, p.allocation().origin(), p.allocation().slotIndex(),
                region, repository, playerStateCache);
        sessionPool.put(sessionId, session);

        logger.info("Opened arena session {} ('{}') at slot {} origin ({}, {}, {}).",
                sessionId, p.arena().name().value(), p.allocation().slotIndex(),
                p.allocation().origin().x(), p.allocation().origin().y(), p.allocation().origin().z());
        return new Result.Ok<>(session);
    }

    /** Bundle of values that flow from the prelude phase into finalize. */
    private record Prelude(Arena arena, ArenaAllocator.Allocation allocation) {}

    @Override
    public boolean closeSession(long sessionId) {
        DefaultArenaSession session = sessionPool.remove(sessionId);
        if (session == null) {
            return false;
        }
        worldGuardService.removeRegion(arenaWorld, session.region().regionId());
        allocator.release(session.slotIndex());
        logger.info("Closed arena session {} ('{}').", sessionId, session.arenaName().value());
        return true;
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
     * Best-effort recovery of which flag name caused a
     * {@link WorldGuardException}. Used purely for the player-facing error
     * message; if no name can be guessed, returns an empty string.
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
