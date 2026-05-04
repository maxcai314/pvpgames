package ax.xz.max.pvpgames.kit.command;

import ax.xz.max.pvpgames.command.CommandSenders;
import ax.xz.max.pvpgames.command.MessageStyle;
import ax.xz.max.pvpgames.kit.Kit;
import ax.xz.max.pvpgames.kit.KitName;
import ax.xz.max.pvpgames.kit.KitResult;
import ax.xz.max.pvpgames.kit.KitService;
import com.mojang.brigadier.Command;
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
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Brigadier command tree for {@code /kit create|load|delete|list|info|help}.
 *
 * <p>Each subcommand gates itself with a granular permission via
 * {@code .requires(...)}; Brigadier hides nodes the source cannot use, so
 * unauthorized players see "Unknown command" rather than a permission error.
 *
 * <p>Handlers translate {@link KitResult} variants into Adventure
 * {@link Component} messages via the shared {@link MessageStyle}.
 */
public final class KitCommand {

    public static final String PERMISSION_CREATE = "pvpgames.kit.create";
    public static final String PERMISSION_LOAD   = "pvpgames.kit.load";
    public static final String PERMISSION_DELETE = "pvpgames.kit.delete";
    public static final String PERMISSION_LIST   = "pvpgames.kit.list";
    public static final String PERMISSION_INFO   = "pvpgames.kit.info";

    private static final String[] ALL_PERMISSIONS = {
            PERMISSION_CREATE, PERMISSION_LOAD, PERMISSION_DELETE, PERMISSION_LIST, PERMISSION_INFO
    };

    private static final MessageStyle MSG = new MessageStyle("[Kit] ", NamedTextColor.AQUA, NamedTextColor.YELLOW);

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final KitService service;
    private final Server server;

    public KitCommand(KitService service, Server server) {
        this.service = Objects.requireNonNull(service, "service");
        this.server = Objects.requireNonNull(server, "server");
    }

    /**
     * Registers the command tree with Paper's command lifecycle. Must be called
     * during plugin enable.
     */
    public void register(LifecycleEventManager<? extends Plugin> events) {
        Objects.requireNonNull(events, "events");
        events.registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(buildRoot(), "Manage PvP kits", List.of("kits")));
    }

    private LiteralCommandNode<CommandSourceStack> buildRoot() {
        return Commands.literal("kit")
                .requires(s -> hasAnyKitPermission(s.getSender()))
                .executes(this::handleHelp)
                .then(Commands.literal("help")
                        .executes(this::handleHelp))
                .then(Commands.literal("create")
                        .requires(s -> s.getSender().hasPermission(PERMISSION_CREATE))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(this::handleCreate)))
                .then(Commands.literal("load")
                        .requires(s -> s.getSender().hasPermission(PERMISSION_LOAD))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(KitCompletions.knownKitNames(service))
                                .executes(this::handleLoad)))
                .then(Commands.literal("delete")
                        .requires(s -> s.getSender().hasPermission(PERMISSION_DELETE))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(KitCompletions.knownKitNames(service))
                                .executes(this::handleDelete)))
                .then(Commands.literal("info")
                        .requires(s -> s.getSender().hasPermission(PERMISSION_INFO))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(KitCompletions.knownKitNames(service))
                                .executes(this::handleInfo)))
                .then(Commands.literal("list")
                        .requires(s -> s.getSender().hasPermission(PERMISSION_LIST))
                        .executes(this::handleList))
                .build();
    }

    private static boolean hasAnyKitPermission(CommandSender sender) {
        for (String perm : ALL_PERMISSIONS) {
            if (sender.hasPermission(perm)) return true;
        }
        return false;
    }

    private int handleCreate(CommandContext<CommandSourceStack> ctx) {
        Optional<Player> maybe = CommandSenders.requirePlayer(ctx.getSource(), MSG, "save a kit");
        if (maybe.isEmpty()) return 0;
        Player player = maybe.get();
        String rawName = StringArgumentType.getString(ctx, "name");
        return switch (service.create(player, rawName)) {
            case KitResult.CreateResult.Created(Kit kit, boolean overwrote) -> {
                String verb = overwrote ? "Replaced" : "Created";
                player.sendMessage(MSG.success(verb + " kit ").append(MSG.highlight(kit.name().value())));
                yield Command.SINGLE_SUCCESS;
            }
            case KitResult.CreateResult.InvalidName(String reason) -> {
                player.sendMessage(MSG.error(reason));
                yield 0;
            }
            case KitResult.CreateResult.EmptyInventory ignored -> {
                player.sendMessage(MSG.error("Your inventory is empty; nothing to save."));
                yield 0;
            }
            case KitResult.CreateResult.IoError(String message) -> {
                player.sendMessage(MSG.error("Could not save kit: " + message));
                yield 0;
            }
        };
    }

    private int handleLoad(CommandContext<CommandSourceStack> ctx) {
        Optional<Player> maybe = CommandSenders.requirePlayer(ctx.getSource(), MSG, "load a kit");
        if (maybe.isEmpty()) return 0;
        Player player = maybe.get();
        String rawName = StringArgumentType.getString(ctx, "name");
        return switch (service.load(player, rawName)) {
            case KitResult.LoadResult.Loaded(Kit kit) -> {
                player.sendMessage(MSG.success("Loaded kit ").append(MSG.highlight(kit.name().value())));
                yield Command.SINGLE_SUCCESS;
            }
            case KitResult.LoadResult.NotFound(String requested) -> {
                player.sendMessage(MSG.error("No kit named '" + requested + "' exists."));
                yield 0;
            }
            case KitResult.LoadResult.InvalidName(String reason) -> {
                player.sendMessage(MSG.error(reason));
                yield 0;
            }
        };
    }

    private int handleDelete(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String rawName = StringArgumentType.getString(ctx, "name");
        return switch (service.delete(rawName)) {
            case KitResult.DeleteResult.Deleted(KitName name) -> {
                sender.sendMessage(MSG.success("Deleted kit ").append(MSG.highlight(name.value())));
                yield Command.SINGLE_SUCCESS;
            }
            case KitResult.DeleteResult.NotFound(String requested) -> {
                sender.sendMessage(MSG.error("No kit named '" + requested + "' exists."));
                yield 0;
            }
            case KitResult.DeleteResult.InvalidName(String reason) -> {
                sender.sendMessage(MSG.error(reason));
                yield 0;
            }
            case KitResult.DeleteResult.IoError(String message) -> {
                sender.sendMessage(MSG.error("Could not delete kit: " + message));
                yield 0;
            }
        };
    }

    private int handleList(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        List<KitName> names = service.listNames();
        if (names.isEmpty()) {
            sender.sendMessage(MSG.info("No kits have been created yet."));
            return Command.SINGLE_SUCCESS;
        }
        Component message = MSG.info("Kits (" + names.size() + "): ");
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
        Optional<Kit> found = service.find(rawName);
        if (found.isEmpty()) {
            sender.sendMessage(MSG.error("No kit named '" + rawName + "' exists."));
            return 0;
        }
        Kit kit = found.get();

        String creator = kit.createdBy() == null
                ? "<unknown>"
                : Optional.ofNullable(server.getOfflinePlayer(kit.createdBy()).getName()).orElse(kit.createdBy().toString());
        String created = DATE_FORMAT.format(kit.createdAt());

        sender.sendMessage(MSG.info("Kit ").append(MSG.highlight(kit.name().value())));
        sender.sendMessage(MSG.info("  Creator: ").append(MSG.highlight(creator)));
        sender.sendMessage(MSG.info("  Created: ").append(MSG.highlight(created)));
        return Command.SINGLE_SUCCESS;
    }

    private int handleHelp(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        sender.sendMessage(MSG.info("Kit commands:"));
        if (sender.hasPermission(PERMISSION_CREATE)) {
            sender.sendMessage(MSG.helpEntry("/kit create <name>", "Save your inventory as a kit."));
        }
        if (sender.hasPermission(PERMISSION_LOAD)) {
            sender.sendMessage(MSG.helpEntry("/kit load <name>", "Replace your inventory with a kit."));
        }
        if (sender.hasPermission(PERMISSION_DELETE)) {
            sender.sendMessage(MSG.helpEntry("/kit delete <name>", "Permanently delete a kit."));
        }
        if (sender.hasPermission(PERMISSION_INFO)) {
            sender.sendMessage(MSG.helpEntry("/kit info <name>", "Show kit metadata."));
        }
        if (sender.hasPermission(PERMISSION_LIST)) {
            sender.sendMessage(MSG.helpEntry("/kit list", "List all kit names."));
        }
        sender.sendMessage(MSG.helpEntry("/kit help", "Show this help."));
        return Command.SINGLE_SUCCESS;
    }
}
