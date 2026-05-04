package ax.xz.max.pvpgames.arena.internal;

import ax.xz.max.pvpgames.arena.Arena;
import ax.xz.max.pvpgames.arena.ArenaName;
import ax.xz.max.pvpgames.arena.ArenaPersistenceException;
import ax.xz.max.pvpgames.arena.ArenaRepository;
import ax.xz.max.pvpgames.arena.ArenaResult;
import ax.xz.max.pvpgames.arena.ArenaService;
import ax.xz.max.pvpgames.arena.ArenaSession;
import ax.xz.max.pvpgames.arena.SpawnPoint;
import ax.xz.max.pvpgames.command.NameParseResult;
import ax.xz.max.pvpgames.player.PlayerStateSnapshot;
import ax.xz.max.pvpgames.schematic.BlockVec3;
import ax.xz.max.pvpgames.schematic.SchematicException;
import ax.xz.max.pvpgames.schematic.SchematicName;
import ax.xz.max.pvpgames.schematic.SchematicService;
import ax.xz.max.pvpgames.world.WorldService;
import ax.xz.max.pvpgames.world.WorldServiceException;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.slf4j.Logger;

import java.time.Clock;
import java.util.ArrayList;
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
 * Default {@link ArenaService} implementation.
 *
 * <p>Owns two pieces of in-memory state:
 * <ul>
 *   <li>{@link #sessionPool} — id to active session</li>
 *   <li>{@link #savedStates} — per-player snapshot taken before they entered
 *       any session, popped on {@code /arena leave} or restored on
 *       {@link #shutdown}</li>
 * </ul>
 *
 * <p>"Which session is this player currently inside?" is intentionally not a
 * separate map: the answer is derivable by matching
 * {@code player.getWorld().getName()} against the session pool, so we read it
 * directly in {@link #resolveCurrentArena} instead of duplicating that state.
 *
 * <p>All methods touch live {@link Player} state and must be called on the
 * server main thread.
 */
public final class DefaultArenaService implements ArenaService {

    private final ArenaRepository repository;
    private final WorldService worldService;
    private final SchematicService schematicService;
    private final Server server;
    private final Clock clock;
    private final Logger logger;

    private final Map<Long, ArenaSession> sessionPool = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<UUID, PlayerStateSnapshot> savedStates = Collections.synchronizedMap(new LinkedHashMap<>());
    private final AtomicLong nextSessionId = new AtomicLong(1);

    public DefaultArenaService(
            ArenaRepository repository,
            WorldService worldService,
            SchematicService schematicService,
            Server server,
            Clock clock,
            Logger logger) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.worldService = Objects.requireNonNull(worldService, "worldService");
        this.schematicService = Objects.requireNonNull(schematicService, "schematicService");
        this.server = Objects.requireNonNull(server, "server");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    // --- CRUD ---------------------------------------------------------------

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

    // --- Sessions -----------------------------------------------------------

    @Override
    public ArenaResult.PreviewResult preview(Player player, String rawArenaName) {
        Objects.requireNonNull(player, "player");

        NameParseResult<ArenaName> parsed = ArenaName.tryParse(rawArenaName);
        if (parsed instanceof NameParseResult.Invalid<ArenaName>(String reason)) {
            return new ArenaResult.PreviewResult.InvalidName(reason);
        }
        ArenaName arenaName = ((NameParseResult.Valid<ArenaName>) parsed).name();

        Optional<Arena> maybeArena = repository.find(arenaName);
        if (maybeArena.isEmpty()) {
            return new ArenaResult.PreviewResult.NotFound(rawArenaName);
        }
        Arena arena = maybeArena.get();

        long sessionId = nextSessionId.getAndIncrement();
        String worldName = ArenaName.WORLD_PREFIX + sessionId;

        World world;
        try {
            world = worldService.getOrCreateVoidWorld(worldName);
        } catch (WorldServiceException ex) {
            return new ArenaResult.PreviewResult.WorldFailed(ex.getMessage());
        }

        try {
            schematicService.pasteAtOrigin(arena.schematicName(), world, BlockVec3.ORIGIN);
        } catch (SchematicException.NotFound ex) {
            tryDeleteWorld(worldName);
            return new ArenaResult.PreviewResult.SchematicMissing(arena.schematicName().value());
        } catch (SchematicException ex) {
            tryDeleteWorld(worldName);
            return new ArenaResult.PreviewResult.SchematicLoadFailed(arena.schematicName().value(), ex.getMessage());
        }

        ArenaSession session = new ArenaSession(sessionId, arenaName, worldName);
        sessionPool.put(sessionId, session);

        boolean noSpawnsYet = arena.spawns().isEmpty();
        joinSessionInternal(player, session, arena, world);

        return new ArenaResult.PreviewResult.Started(session, world, noSpawnsYet);
    }

    @Override
    public ArenaResult.JoinResult join(Player player, long sessionId) {
        Objects.requireNonNull(player, "player");

        ArenaSession session = sessionPool.get(sessionId);
        if (session == null) {
            return new ArenaResult.JoinResult.SessionNotFound(sessionId);
        }
        World world = server.getWorld(session.worldName());
        // World should exist as long as the session is in the pool; if it
        // doesn't, treat as a missing session.
        if (world == null) {
            sessionPool.remove(sessionId);
            return new ArenaResult.JoinResult.SessionNotFound(sessionId);
        }

        Arena arena = repository.find(session.arena()).orElse(null);
        boolean noSpawnsYet = arena == null || arena.spawns().isEmpty();
        joinSessionInternal(player, session, arena, world);

        return new ArenaResult.JoinResult.Joined(session, noSpawnsYet);
    }

    /**
     * Caches the player's pre-session state (if not already cached), wipes
     * their state to {@link PlayerStateSnapshot#DEFAULT}, and teleports them
     * to the arena's first spawn point (or the world spawn if no spawns
     * exist). The player→session association is implicit: after the
     * teleport the player's current world matches {@code session.worldName()},
     * which is what {@link #resolveCurrentArena} reads.
     *
     * <p>{@code arena} may be {@code null} (deleted out from under the
     * session); we still teleport into the world but to its spawn point.
     */
    private void joinSessionInternal(Player player, ArenaSession session, Arena arena, World sessionWorld) {
        UUID playerId = player.getUniqueId();

        savedStates.computeIfAbsent(playerId, ignored -> PlayerStateSnapshot.captureFrom(player));

        PlayerStateSnapshot.DEFAULT.applyTo(player);

        Location target;
        if (arena != null && !arena.spawns().isEmpty()) {
            target = arena.spawns().getFirst().toLocation(sessionWorld);
        } else {
            target = sessionWorld.getSpawnLocation();
        }
        player.teleport(target);
    }

    /**
     * Mirror of {@link #joinSessionInternal}: pops the player's pre-session
     * snapshot from {@link #savedStates} and applies it to restore the world
     * / location / inventory / health / etc. they had when they first joined
     * any session. The player→session association is implicit (derived from
     * world name), so there is no separate map to clear.
     *
     * <p>Idempotent: if no snapshot is cached (the player was never in a
     * session, or has already been restored), this is a no-op and returns
     * {@code false}. Otherwise the snapshot is applied and {@code true} is
     * returned.
     *
     * <p>Called from {@link #leave(Player)} (a player explicitly running
     * {@code /arena leave}) and from {@link #shutdown()} (one final restore
     * for every online player still tracked in the saved-state map).
     */
    private boolean leaveSessionInternal(Player player) {
        UUID playerId = player.getUniqueId();

        PlayerStateSnapshot saved = savedStates.remove(playerId);
        if (saved == null) return false;
        saved.applyTo(player);
        return true;
    }

    @Override
    public ArenaResult.LeaveResult leave(Player player) {
        Objects.requireNonNull(player, "player");
        return leaveSessionInternal(player)
                ? new ArenaResult.LeaveResult.Returned()
                : new ArenaResult.LeaveResult.NoActiveSession();
    }

    @Override
    public Collection<ArenaSession> activeSessions() {
        synchronized (sessionPool) {
            return List.copyOf(sessionPool.values());
        }
    }

    // --- Spawn ops ----------------------------------------------------------

    @Override
    public ArenaResult.AddSpawnResult addSpawnAtPlayer(Player player) {
        Objects.requireNonNull(player, "player");
        return addSpawnInternal(player, SpawnPoint.of(player.getLocation()));
    }

    @Override
    public ArenaResult.AddSpawnResult addSpawnExplicit(
            Player player, double x, double y, double z, Float yaw, Float pitch) {
        Objects.requireNonNull(player, "player");
        Location playerLoc = player.getLocation();
        float effectiveYaw = yaw != null ? yaw : playerLoc.getYaw();
        float effectivePitch = pitch != null ? pitch : playerLoc.getPitch();
        return addSpawnInternal(player, new SpawnPoint(x, y, z, effectiveYaw, effectivePitch));
    }

    private ArenaResult.AddSpawnResult addSpawnInternal(Player player, SpawnPoint spawn) {
        return switch (resolveCurrentArena(player)) {
            case CurrentArena.NotInArenaWorld ignored -> new ArenaResult.AddSpawnResult.NotInArenaWorld();
            case CurrentArena.ArenaMissing(String requestedName) ->
                    new ArenaResult.AddSpawnResult.ArenaMissing(requestedName);
            case CurrentArena.Found(Arena arena) -> {
                Arena updated = arena.addSpawn(spawn);
                try {
                    repository.save(updated);
                    yield new ArenaResult.AddSpawnResult.Added(updated, updated.spawns().size());
                } catch (ArenaPersistenceException ex) {
                    yield new ArenaResult.AddSpawnResult.IoError(ex.getMessage());
                }
            }
        };
    }

    @Override
    public ArenaResult.ListSpawnResult listSpawns(Player player) {
        Objects.requireNonNull(player, "player");
        return switch (resolveCurrentArena(player)) {
            case CurrentArena.NotInArenaWorld ignored -> new ArenaResult.ListSpawnResult.NotInArenaWorld();
            case CurrentArena.ArenaMissing(String requestedName) ->
                    new ArenaResult.ListSpawnResult.ArenaMissing(requestedName);
            case CurrentArena.Found(Arena arena) ->
                    new ArenaResult.ListSpawnResult.Listed(arena, arena.spawns());
        };
    }

    @Override
    public ArenaResult.VisitSpawnResult visitSpawn(Player player, int oneBasedIndex) {
        Objects.requireNonNull(player, "player");
        return switch (resolveCurrentArena(player)) {
            case CurrentArena.NotInArenaWorld ignored -> new ArenaResult.VisitSpawnResult.NotInArenaWorld();
            case CurrentArena.ArenaMissing(String requestedName) ->
                    new ArenaResult.VisitSpawnResult.ArenaMissing(requestedName);
            case CurrentArena.Found(Arena arena) -> {
                List<SpawnPoint> spawns = arena.spawns();
                if (oneBasedIndex < 1 || oneBasedIndex > spawns.size()) {
                    yield new ArenaResult.VisitSpawnResult.IndexOutOfRange(oneBasedIndex, spawns.size());
                }
                SpawnPoint spawn = spawns.get(oneBasedIndex - 1);
                player.teleport(spawn.toLocation(player.getWorld()));
                yield new ArenaResult.VisitSpawnResult.Visited(arena, oneBasedIndex, spawn);
            }
        };
    }

    @Override
    public ArenaResult.RemoveSpawnResult removeSpawn(Player player, int oneBasedIndex) {
        Objects.requireNonNull(player, "player");
        return switch (resolveCurrentArena(player)) {
            case CurrentArena.NotInArenaWorld ignored -> new ArenaResult.RemoveSpawnResult.NotInArenaWorld();
            case CurrentArena.ArenaMissing(String requestedName) ->
                    new ArenaResult.RemoveSpawnResult.ArenaMissing(requestedName);
            case CurrentArena.Found(Arena arena) -> {
                List<SpawnPoint> spawns = arena.spawns();
                if (oneBasedIndex < 1 || oneBasedIndex > spawns.size()) {
                    yield new ArenaResult.RemoveSpawnResult.IndexOutOfRange(oneBasedIndex, spawns.size());
                }
                SpawnPoint removed = spawns.get(oneBasedIndex - 1);
                Arena updated = arena.removeSpawnAt(oneBasedIndex - 1);
                try {
                    repository.save(updated);
                    yield new ArenaResult.RemoveSpawnResult.Removed(updated, oneBasedIndex, removed);
                } catch (ArenaPersistenceException ex) {
                    yield new ArenaResult.RemoveSpawnResult.IoError(ex.getMessage());
                }
            }
        };
    }

    // --- Lifecycle ----------------------------------------------------------

    @Override
    public void shutdown() {
        Location fallback = pickFallbackLocation();

        // Restore any cached states for online players via leaveSessionInternal.
        List<UUID> cached;
        synchronized (savedStates) {
            cached = new ArrayList<>(savedStates.keySet());
        }
        for (UUID playerId : cached) {
            Player player = server.getPlayer(playerId);
            if (player == null) {
                savedStates.remove(playerId);
                logger.warn("Player {} is offline at shutdown; pre-session state was not restored.", playerId);
                continue;
            }
            try {
                leaveSessionInternal(player);
            } catch (Exception ex) {
                logger.warn("Failed to restore {} on shutdown: {}", player.getName(), ex.getMessage());
                if (fallback != null) {
                    player.teleport(fallback);
                    player.setGameMode(GameMode.SURVIVAL);
                }
            }
        }
        // Anything still cached belonged to offline players we couldn't restore.
        savedStates.clear();

        // Tear down every session world. Anyone still inside gets the fallback
        // (e.g. they joined via /world tp without using /arena join, so we
        // never captured their state).
        List<ArenaSession> toTearDown;
        synchronized (sessionPool) {
            toTearDown = new ArrayList<>(sessionPool.values());
        }
        for (ArenaSession session : toTearDown) {
            World world = server.getWorld(session.worldName());
            if (world != null) {
                for (Player stuck : new ArrayList<>(world.getPlayers())) {
                    logger.warn("Player {} remained in arena world '{}' at shutdown; sending to fallback.",
                            stuck.getName(), session.worldName());
                    if (fallback != null) {
                        stuck.teleport(fallback);
                        stuck.setGameMode(GameMode.SURVIVAL);
                    }
                }
            }
            try {
                worldService.deleteWorld(session.worldName());
            } catch (WorldServiceException ex) {
                logger.warn("Failed to delete arena world '{}' at shutdown: {}",
                        session.worldName(), ex.getMessage());
            }
        }
        sessionPool.clear();
    }

    // --- Helpers ------------------------------------------------------------

    /**
     * Internal three-way result of "which arena is this player currently
     * editing". Used by every spawn-list command.
     */
    private sealed interface CurrentArena {
        record NotInArenaWorld() implements CurrentArena {}
        record ArenaMissing(String requestedName) implements CurrentArena {}
        record Found(Arena arena) implements CurrentArena {}
    }

    private CurrentArena resolveCurrentArena(Player player) {
        // The player is in an arena session iff their current world name
        // matches one of the sessions we created. No separate player→session
        // map needed — the world the player is actually standing in is the
        // source of truth.
        String currentWorld = player.getWorld().getName();
        ArenaSession session;
        synchronized (sessionPool) {
            session = sessionPool.values().stream()
                    .filter(s -> s.worldName().equals(currentWorld))
                    .findFirst()
                    .orElse(null);
        }
        if (session == null) return new CurrentArena.NotInArenaWorld();

        return repository.find(session.arena())
                .<CurrentArena>map(CurrentArena.Found::new)
                .orElseGet(() -> new CurrentArena.ArenaMissing(session.arena().value()));
    }

    private Location pickFallbackLocation() {
        for (World world : server.getWorlds()) {
            if (!world.getName().startsWith(ArenaName.WORLD_PREFIX)) {
                return world.getSpawnLocation();
            }
        }
        return null;
    }

    private void tryDeleteWorld(String worldName) {
        try {
            worldService.deleteWorld(worldName);
        } catch (WorldServiceException ex) {
            logger.warn("Failed to roll back arena world '{}': {}", worldName, ex.getMessage());
        }
    }
}
