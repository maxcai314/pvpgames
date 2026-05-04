package ax.xz.max.pvpgames.arena.command;

import ax.xz.max.pvpgames.arena.Arena;
import ax.xz.max.pvpgames.arena.ArenaName;
import ax.xz.max.pvpgames.arena.ArenaResult;
import ax.xz.max.pvpgames.arena.ArenaService;
import ax.xz.max.pvpgames.arena.ArenaSession;
import ax.xz.max.pvpgames.arena.SpawnPoint;
import ax.xz.max.pvpgames.command.CommandSenders;
import ax.xz.max.pvpgames.command.MessageStyle;
import ax.xz.max.pvpgames.server.ServerHelper;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Brigadier command tree for {@code /arena create|delete|list|info|preview|join|sessions|leave|addspawn|listspawn|visitspawn|removespawn|help}.
 *
 * <p>Each subcommand gates itself with a granular permission via
 * {@code .requires(...)}. Brigadier hides nodes the source cannot use, so
 * unauthorized players see "Unknown command" rather than a permission error.
 *
 * <p>Handlers translate {@link ArenaResult} variants into Adventure
 * {@link Component} messages via the shared {@link MessageStyle}.
 */
public final class ArenaCommand {

    public static final String PERM_CREATE  = "pvpgames.arena.create";
    public static final String PERM_DELETE  = "pvpgames.arena.delete";
    public static final String PERM_LIST    = "pvpgames.arena.list";
    public static final String PERM_INFO    = "pvpgames.arena.info";
    public static final String PERM_PREVIEW = "pvpgames.arena.preview";
    public static final String PERM_MODIFY  = "pvpgames.arena.modify";

    private static final String[] ALL_PERMISSIONS = {
            PERM_CREATE, PERM_DELETE, PERM_LIST, PERM_INFO, PERM_PREVIEW, PERM_MODIFY
    };

    private static final MessageStyle MSG = new MessageStyle("[Arena] ", NamedTextColor.AQUA, NamedTextColor.YELLOW);

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final ArenaService service;
    private final ServerHelper serverHelper;
    private final Server server;

    public ArenaCommand(ArenaService service, ServerHelper serverHelper, Server server) {
        this.service = Objects.requireNonNull(service, "service");
        this.serverHelper = Objects.requireNonNull(serverHelper, "serverHelper");
        this.server = Objects.requireNonNull(server, "server");
    }

    /**
     * Registers the command tree with Paper's command lifecycle. Must be called
     * during plugin enable.
     */
    public void register(LifecycleEventManager<? extends Plugin> events) {
        Objects.requireNonNull(events, "events");
        events.registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(buildRoot(), "Manage PvP arenas", List.of("arenas")));
    }

    private LiteralCommandNode<CommandSourceStack> buildRoot() {
        return Commands.literal("arena")
                .requires(s -> hasAnyArenaPermission(s.getSender()))
                .executes(this::handleHelp)
                .then(Commands.literal("help")
                        .executes(this::handleHelp))
                .then(Commands.literal("create")
                        .requires(s -> s.getSender().hasPermission(PERM_CREATE))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("schematic", StringArgumentType.word())
                                        .executes(this::handleCreate))))
                .then(Commands.literal("delete")
                        .requires(s -> s.getSender().hasPermission(PERM_DELETE))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(ArenaCompletions.knownArenaNames(service))
                                .executes(this::handleDelete)))
                .then(Commands.literal("list")
                        .requires(s -> s.getSender().hasPermission(PERM_LIST))
                        .executes(this::handleList))
                .then(Commands.literal("info")
                        .requires(s -> s.getSender().hasPermission(PERM_INFO))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(ArenaCompletions.knownArenaNames(service))
                                .executes(this::handleInfo)))
                .then(Commands.literal("preview")
                        .requires(s -> s.getSender().hasPermission(PERM_PREVIEW))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(ArenaCompletions.knownArenaNames(service))
                                .executes(this::handlePreview)))
                .then(Commands.literal("join")
                        .requires(s -> s.getSender().hasPermission(PERM_PREVIEW))
                        .then(Commands.argument("session", LongArgumentType.longArg(1))
                                .executes(this::handleJoin)))
                .then(Commands.literal("sessions")
                        .requires(s -> s.getSender().hasPermission(PERM_PREVIEW))
                        .executes(this::handleSessions))
                .then(Commands.literal("leave")
                        .requires(s -> s.getSender().hasPermission(PERM_PREVIEW))
                        .executes(this::handleLeave))
                .then(Commands.literal("addspawn")
                        .requires(s -> s.getSender().hasPermission(PERM_MODIFY))
                        .executes(this::handleAddSpawnNoArgs)
                        .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                .executes(this::handleAddSpawnXYZ)
                                                .then(Commands.argument("yaw", FloatArgumentType.floatArg(-180f, 180f))
                                                        .then(Commands.argument("pitch", FloatArgumentType.floatArg(-90f, 90f))
                                                                .executes(this::handleAddSpawnFull)))))))
                .then(Commands.literal("listspawn")
                        .requires(s -> s.getSender().hasPermission(PERM_MODIFY))
                        .executes(this::handleListSpawn))
                .then(Commands.literal("visitspawn")
                        .requires(s -> s.getSender().hasPermission(PERM_MODIFY))
                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                .executes(this::handleVisitSpawn)))
                .then(Commands.literal("removespawn")
                        .requires(s -> s.getSender().hasPermission(PERM_MODIFY))
                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                .executes(this::handleRemoveSpawn)))
                .build();
    }

    private static boolean hasAnyArenaPermission(CommandSender sender) {
        for (String perm : ALL_PERMISSIONS) {
            if (sender.hasPermission(perm)) return true;
        }
        return false;
    }

    private int handleCreate(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String rawName = StringArgumentType.getString(ctx, "name");
        String schematic = StringArgumentType.getString(ctx, "schematic");
        return switch (service.create(sender, rawName, schematic)) {
            case ArenaResult.CreateResult.Created(Arena arena, boolean overwrote) -> {
                String verb = overwrote ? "Replaced" : "Created";
                sender.sendMessage(MSG.success(verb + " arena ")
                        .append(MSG.highlight(arena.name().value()))
                        .append(Component.text(" using schematic ", NamedTextColor.GRAY))
                        .append(MSG.highlight(arena.schematicName().value())));
                yield Command.SINGLE_SUCCESS;
            }
            case ArenaResult.CreateResult.InvalidName(String reason) -> {
                sender.sendMessage(MSG.error(reason));
                yield 0;
            }
            case ArenaResult.CreateResult.InvalidSchematic(String reason) -> {
                sender.sendMessage(MSG.error(reason));
                yield 0;
            }
            case ArenaResult.CreateResult.IoError(String message) -> {
                sender.sendMessage(MSG.error("Could not save arena: " + message));
                yield 0;
            }
        };
    }

    private int handleDelete(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String rawName = StringArgumentType.getString(ctx, "name");
        return switch (service.delete(rawName)) {
            case ArenaResult.DeleteResult.Deleted(ArenaName name) -> {
                sender.sendMessage(MSG.success("Deleted arena ").append(MSG.highlight(name.value())));
                yield Command.SINGLE_SUCCESS;
            }
            case ArenaResult.DeleteResult.NotFound(String requested) -> {
                sender.sendMessage(MSG.error("No arena named '" + requested + "' exists."));
                yield 0;
            }
            case ArenaResult.DeleteResult.InvalidName(String reason) -> {
                sender.sendMessage(MSG.error(reason));
                yield 0;
            }
            case ArenaResult.DeleteResult.IoError(String message) -> {
                sender.sendMessage(MSG.error("Could not delete arena: " + message));
                yield 0;
            }
        };
    }

    private int handleList(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        List<ArenaName> names = service.listNames();
        if (names.isEmpty()) {
            sender.sendMessage(MSG.info("No arenas have been created yet."));
            return Command.SINGLE_SUCCESS;
        }
        Component message = MSG.info("Arenas (" + names.size() + "): ");
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                message = message.append(Component.text(", ", NamedTextColor.GRAY));
            }
            message = message.append(MSG.highlight(names.get(i).value()));
        }
        sender.sendMessage(message);
        return Command.SINGLE_SUCCESS;
    }

    private int handleInfo(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String rawName = StringArgumentType.getString(ctx, "name");
        Optional<Arena> found = service.find(rawName);
        if (found.isEmpty()) {
            sender.sendMessage(MSG.error("No arena named '" + rawName + "' exists."));
            return 0;
        }
        Arena arena = found.get();

        String creator = arena.createdBy() == null
                ? "<unknown>"
                : serverHelper.resolveOfflineName(arena.createdBy()).orElse(arena.createdBy().toString());
        String created = DATE_FORMAT.format(arena.createdAt());

        sender.sendMessage(MSG.info("Arena ").append(MSG.highlight(arena.name().value())));
        sender.sendMessage(MSG.info("  Schematic: ").append(MSG.highlight(arena.schematicName().value())));
        sender.sendMessage(MSG.info("  Spawn points: ").append(MSG.highlight(String.valueOf(arena.spawns().size()))));
        sender.sendMessage(MSG.info("  Creator: ").append(MSG.highlight(creator)));
        sender.sendMessage(MSG.info("  Created: ").append(MSG.highlight(created)));
        return Command.SINGLE_SUCCESS;
    }

    private int handlePreview(CommandContext<CommandSourceStack> ctx) {
        Optional<Player> maybe = CommandSenders.requirePlayer(ctx.getSource(), MSG, "preview an arena");
        if (maybe.isEmpty()) return 0;
        Player player = maybe.get();
        String rawName = StringArgumentType.getString(ctx, "name");
        return switch (service.preview(player, rawName)) {
            case ArenaResult.PreviewResult.Started(ArenaSession session, World world, boolean noSpawnsYet) -> {
                player.sendMessage(MSG.success("Started session ")
                        .append(MSG.highlight("#" + session.id()))
                        .append(Component.text(" for arena ", NamedTextColor.GRAY))
                        .append(MSG.highlight(session.arena().value()))
                        .append(Component.text(".", NamedTextColor.GRAY)));
                if (noSpawnsYet) {
                    player.sendMessage(MSG.info("This arena has no spawn points; run ")
                            .append(MSG.highlight("/arena addspawn"))
                            .append(Component.text(" to add one.", NamedTextColor.GRAY)));
                }
                yield Command.SINGLE_SUCCESS;
            }
            case ArenaResult.PreviewResult.NotFound(String requested) -> {
                player.sendMessage(MSG.error("No arena named '" + requested + "' exists."));
                yield 0;
            }
            case ArenaResult.PreviewResult.InvalidName(String reason) -> {
                player.sendMessage(MSG.error(reason));
                yield 0;
            }
            case ArenaResult.PreviewResult.SchematicMissing(String name) -> {
                player.sendMessage(MSG.error("Schematic '" + name + "' was not found."));
                yield 0;
            }
            case ArenaResult.PreviewResult.SchematicLoadFailed(String name, String message) -> {
                player.sendMessage(MSG.error("Could not load schematic '" + name + "': " + message));
                yield 0;
            }
            case ArenaResult.PreviewResult.WorldFailed(String message) -> {
                player.sendMessage(MSG.error("Could not load arena world: " + message));
                yield 0;
            }
            case ArenaResult.PreviewResult.DependencyMissing(String message) -> {
                player.sendMessage(MSG.error("Dependency missing: " + message));
                yield 0;
            }
        };
    }

    private int handleJoin(CommandContext<CommandSourceStack> ctx) {
        Optional<Player> maybe = CommandSenders.requirePlayer(ctx.getSource(), MSG, "join an arena session");
        if (maybe.isEmpty()) return 0;
        Player player = maybe.get();
        long sessionId = LongArgumentType.getLong(ctx, "session");
        return switch (service.join(player, sessionId)) {
            case ArenaResult.JoinResult.Joined(ArenaSession session, boolean noSpawnsYet) -> {
                player.sendMessage(MSG.success("Joined session ")
                        .append(MSG.highlight("#" + session.id()))
                        .append(Component.text(" for arena ", NamedTextColor.GRAY))
                        .append(MSG.highlight(session.arena().value()))
                        .append(Component.text(".", NamedTextColor.GRAY)));
                if (noSpawnsYet) {
                    player.sendMessage(MSG.info("This arena has no spawn points; run ")
                            .append(MSG.highlight("/arena addspawn"))
                            .append(Component.text(" to add one.", NamedTextColor.GRAY)));
                }
                yield Command.SINGLE_SUCCESS;
            }
            case ArenaResult.JoinResult.SessionNotFound(long id) -> {
                player.sendMessage(MSG.error("No active session with ID #" + id + "."));
                yield 0;
            }
            case ArenaResult.JoinResult.DependencyMissing(String message) -> {
                player.sendMessage(MSG.error("Dependency missing: " + message));
                yield 0;
            }
        };
    }

    private int handleSessions(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Collection<ArenaSession> sessions = service.activeSessions();
        if (sessions.isEmpty()) {
            sender.sendMessage(MSG.info("No active arena sessions."));
            return Command.SINGLE_SUCCESS;
        }
        sender.sendMessage(MSG.info("Active sessions (" + sessions.size() + "):"));
        for (ArenaSession session : sessions) {
            World world = server.getWorld(session.worldName());
            int playerCount = world == null ? 0 : world.getPlayers().size();
            sender.sendMessage(MSG.info("  ")
                    .append(MSG.highlight("#" + session.id()))
                    .append(Component.text(" - arena ", NamedTextColor.GRAY))
                    .append(MSG.highlight(session.arena().value()))
                    .append(Component.text(" (" + playerCount + " player"
                            + (playerCount == 1 ? "" : "s") + ")", NamedTextColor.GRAY)));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int handleLeave(CommandContext<CommandSourceStack> ctx) {
        Optional<Player> maybe = CommandSenders.requirePlayer(ctx.getSource(), MSG, "leave an arena");
        if (maybe.isEmpty()) return 0;
        Player player = maybe.get();
        return switch (service.leave(player)) {
            case ArenaResult.LeaveResult.Returned ignored -> {
                player.sendMessage(MSG.success("Returned to your previous location."));
                yield Command.SINGLE_SUCCESS;
            }
            case ArenaResult.LeaveResult.NoActiveSession ignored -> {
                player.sendMessage(MSG.error("You are not currently in any arena session."));
                yield 0;
            }
        };
    }

    private int handleAddSpawnNoArgs(CommandContext<CommandSourceStack> ctx) {
        Optional<Player> maybe = CommandSenders.requirePlayer(ctx.getSource(), MSG, "add a spawn point");
        if (maybe.isEmpty()) return 0;
        Player player = maybe.get();
        return reportAddSpawn(player, service.addSpawnAtPlayer(player));
    }

    private int handleAddSpawnXYZ(CommandContext<CommandSourceStack> ctx) {
        Optional<Player> maybe = CommandSenders.requirePlayer(ctx.getSource(), MSG, "add a spawn point");
        if (maybe.isEmpty()) return 0;
        Player player = maybe.get();
        double x = DoubleArgumentType.getDouble(ctx, "x");
        double y = DoubleArgumentType.getDouble(ctx, "y");
        double z = DoubleArgumentType.getDouble(ctx, "z");
        return reportAddSpawn(player, service.addSpawnExplicit(player, x, y, z, null, null));
    }

    private int handleAddSpawnFull(CommandContext<CommandSourceStack> ctx) {
        Optional<Player> maybe = CommandSenders.requirePlayer(ctx.getSource(), MSG, "add a spawn point");
        if (maybe.isEmpty()) return 0;
        Player player = maybe.get();
        double x = DoubleArgumentType.getDouble(ctx, "x");
        double y = DoubleArgumentType.getDouble(ctx, "y");
        double z = DoubleArgumentType.getDouble(ctx, "z");
        float yaw = FloatArgumentType.getFloat(ctx, "yaw");
        float pitch = FloatArgumentType.getFloat(ctx, "pitch");
        return reportAddSpawn(player, service.addSpawnExplicit(player, x, y, z, yaw, pitch));
    }

    private int reportAddSpawn(Player player, ArenaResult.AddSpawnResult result) {
        return switch (result) {
            case ArenaResult.AddSpawnResult.Added(Arena arena, int oneBasedIndex) -> {
                player.sendMessage(MSG.success("Added spawn ")
                        .append(MSG.highlight(String.valueOf(oneBasedIndex)))
                        .append(Component.text(" to arena ", NamedTextColor.GRAY))
                        .append(MSG.highlight(arena.name().value()))
                        .append(Component.text(".", NamedTextColor.GRAY)));
                yield Command.SINGLE_SUCCESS;
            }
            case ArenaResult.AddSpawnResult.NotInArenaWorld ignored -> {
                player.sendMessage(MSG.error("You must be inside an arena session to add spawns."));
                yield 0;
            }
            case ArenaResult.AddSpawnResult.ArenaMissing(String requested) -> {
                player.sendMessage(MSG.error("The arena for this session ('" + requested + "') no longer exists."));
                yield 0;
            }
            case ArenaResult.AddSpawnResult.IoError(String message) -> {
                player.sendMessage(MSG.error("Could not save spawn: " + message));
                yield 0;
            }
        };
    }

    private int handleListSpawn(CommandContext<CommandSourceStack> ctx) {
        Optional<Player> maybe = CommandSenders.requirePlayer(ctx.getSource(), MSG, "list spawn points");
        if (maybe.isEmpty()) return 0;
        Player player = maybe.get();
        return switch (service.listSpawns(player)) {
            case ArenaResult.ListSpawnResult.Listed(Arena arena, List<SpawnPoint> spawns) -> {
                if (spawns.isEmpty()) {
                    player.sendMessage(MSG.info("No spawn points defined in arena ")
                            .append(MSG.highlight(arena.name().value()))
                            .append(Component.text(".", NamedTextColor.GRAY)));
                    yield Command.SINGLE_SUCCESS;
                }
                player.sendMessage(MSG.info("Spawn points in arena ")
                        .append(MSG.highlight(arena.name().value()))
                        .append(Component.text(":", NamedTextColor.GRAY)));
                for (int i = 0; i < spawns.size(); i++) {
                    player.sendMessage(MSG.info("  ")
                            .append(MSG.highlight(String.valueOf(i + 1)))
                            .append(Component.text(": " + formatSpawn(spawns.get(i)), NamedTextColor.GRAY)));
                }
                yield Command.SINGLE_SUCCESS;
            }
            case ArenaResult.ListSpawnResult.NotInArenaWorld ignored -> {
                player.sendMessage(MSG.error("You must be inside an arena session to list spawns."));
                yield 0;
            }
            case ArenaResult.ListSpawnResult.ArenaMissing(String requested) -> {
                player.sendMessage(MSG.error("The arena for this session ('" + requested + "') no longer exists."));
                yield 0;
            }
        };
    }

    private int handleVisitSpawn(CommandContext<CommandSourceStack> ctx) {
        Optional<Player> maybe = CommandSenders.requirePlayer(ctx.getSource(), MSG, "visit a spawn point");
        if (maybe.isEmpty()) return 0;
        Player player = maybe.get();
        int index = IntegerArgumentType.getInteger(ctx, "index");
        return switch (service.visitSpawn(player, index)) {
            case ArenaResult.VisitSpawnResult.Visited(Arena arena, int oneBasedIndex, SpawnPoint spawn) -> {
                player.sendMessage(MSG.success("Teleported to spawn ")
                        .append(MSG.highlight(String.valueOf(oneBasedIndex)))
                        .append(Component.text(" of arena ", NamedTextColor.GRAY))
                        .append(MSG.highlight(arena.name().value()))
                        .append(Component.text(".", NamedTextColor.GRAY)));
                yield Command.SINGLE_SUCCESS;
            }
            case ArenaResult.VisitSpawnResult.NotInArenaWorld ignored -> {
                player.sendMessage(MSG.error("You must be inside an arena session to visit spawns."));
                yield 0;
            }
            case ArenaResult.VisitSpawnResult.ArenaMissing(String requested) -> {
                player.sendMessage(MSG.error("The arena for this session ('" + requested + "') no longer exists."));
                yield 0;
            }
            case ArenaResult.VisitSpawnResult.IndexOutOfRange(int requested, int size) -> {
                player.sendMessage(MSG.error("No spawn at index " + requested
                        + " (arena has " + size + " spawn point" + (size == 1 ? "" : "s") + ")."));
                yield 0;
            }
        };
    }

    private int handleRemoveSpawn(CommandContext<CommandSourceStack> ctx) {
        Optional<Player> maybe = CommandSenders.requirePlayer(ctx.getSource(), MSG, "remove a spawn point");
        if (maybe.isEmpty()) return 0;
        Player player = maybe.get();
        int index = IntegerArgumentType.getInteger(ctx, "index");
        return switch (service.removeSpawn(player, index)) {
            case ArenaResult.RemoveSpawnResult.Removed(Arena arena, int oneBasedIndex, SpawnPoint spawn) -> {
                player.sendMessage(MSG.success("Removed spawn ")
                        .append(MSG.highlight(String.valueOf(oneBasedIndex)))
                        .append(Component.text(" from arena ", NamedTextColor.GRAY))
                        .append(MSG.highlight(arena.name().value()))
                        .append(Component.text(".", NamedTextColor.GRAY)));
                yield Command.SINGLE_SUCCESS;
            }
            case ArenaResult.RemoveSpawnResult.NotInArenaWorld ignored -> {
                player.sendMessage(MSG.error("You must be inside an arena session to remove spawns."));
                yield 0;
            }
            case ArenaResult.RemoveSpawnResult.ArenaMissing(String requested) -> {
                player.sendMessage(MSG.error("The arena for this session ('" + requested + "') no longer exists."));
                yield 0;
            }
            case ArenaResult.RemoveSpawnResult.IndexOutOfRange(int requested, int size) -> {
                player.sendMessage(MSG.error("No spawn at index " + requested
                        + " (arena has " + size + " spawn point" + (size == 1 ? "" : "s") + ")."));
                yield 0;
            }
            case ArenaResult.RemoveSpawnResult.IoError(String message) -> {
                player.sendMessage(MSG.error("Could not remove spawn: " + message));
                yield 0;
            }
        };
    }

    private int handleHelp(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        sender.sendMessage(MSG.info("Arena commands:"));
        if (sender.hasPermission(PERM_CREATE)) {
            sender.sendMessage(MSG.helpEntry("/arena create <name> <schematic>", "Create a new arena."));
        }
        if (sender.hasPermission(PERM_DELETE)) {
            sender.sendMessage(MSG.helpEntry("/arena delete <name>", "Delete an arena."));
        }
        if (sender.hasPermission(PERM_LIST)) {
            sender.sendMessage(MSG.helpEntry("/arena list", "List all arenas."));
        }
        if (sender.hasPermission(PERM_INFO)) {
            sender.sendMessage(MSG.helpEntry("/arena info <name>", "Show arena metadata."));
        }
        if (sender.hasPermission(PERM_PREVIEW)) {
            sender.sendMessage(MSG.helpEntry("/arena preview <name>", "Start a new session for an arena."));
            sender.sendMessage(MSG.helpEntry("/arena join <id>", "Join an existing session."));
            sender.sendMessage(MSG.helpEntry("/arena sessions", "List active sessions."));
            sender.sendMessage(MSG.helpEntry("/arena leave", "Leave the session and restore your state."));
        }
        if (sender.hasPermission(PERM_MODIFY)) {
            sender.sendMessage(MSG.helpEntry("/arena addspawn [x y z [yaw pitch]]", "Add a spawn (defaults to your pose)."));
            sender.sendMessage(MSG.helpEntry("/arena listspawn", "List spawn points."));
            sender.sendMessage(MSG.helpEntry("/arena visitspawn <index>", "Teleport to a spawn point."));
            sender.sendMessage(MSG.helpEntry("/arena removespawn <index>", "Remove a spawn point."));
        }
        sender.sendMessage(MSG.helpEntry("/arena help", "Show this help."));
        return Command.SINGLE_SUCCESS;
    }

    private static String formatSpawn(SpawnPoint spawn) {
        return String.format(Locale.ROOT, "%.2f, %.2f, %.2f (yaw=%.1f, pitch=%.1f)",
                spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
    }
}
