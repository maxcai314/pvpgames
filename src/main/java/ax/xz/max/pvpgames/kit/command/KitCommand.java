package ax.xz.max.pvpgames.kit.command;

import ax.xz.max.pvpgames.kit.Kit;
import ax.xz.max.pvpgames.kit.KitName;
import ax.xz.max.pvpgames.kit.KitResult;
import ax.xz.max.pvpgames.kit.KitService;
import ax.xz.max.pvpgames.server.ServerHelper;
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
 * {@link Component} messages so players get precise feedback.
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

    private static final Component PREFIX = Component.text("[Kit] ", NamedTextColor.AQUA);

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final KitService service;
    private final ServerHelper serverHelper;

    public KitCommand(KitService service, ServerHelper serverHelper) {
        this.service = Objects.requireNonNull(service, "service");
        this.serverHelper = Objects.requireNonNull(serverHelper, "serverHelper");
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
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(error("Only players can save a kit (the kit comes from your inventory)."));
            return 0;
        }
        String rawName = StringArgumentType.getString(ctx, "name");
        return switch (service.create(player, rawName)) {
            case KitResult.CreateResult.Created(Kit kit, boolean overwrote) -> {
                String verb = overwrote ? "Replaced" : "Created";
                player.sendMessage(success(verb + " kit ").append(kitName(kit.name())));
                yield Command.SINGLE_SUCCESS;
            }
            case KitResult.CreateResult.InvalidName(String reason) -> {
                player.sendMessage(error(reason));
                yield 0;
            }
            case KitResult.CreateResult.EmptyInventory ignored -> {
                player.sendMessage(error("Your inventory is empty; nothing to save."));
                yield 0;
            }
            case KitResult.CreateResult.IoError(String message) -> {
                player.sendMessage(error("Could not save kit: " + message));
                yield 0;
            }
        };
    }

    private int handleLoad(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(error("Only players can load a kit into their inventory."));
            return 0;
        }
        String rawName = StringArgumentType.getString(ctx, "name");
        return switch (service.load(player, rawName)) {
            case KitResult.LoadResult.Loaded(Kit kit) -> {
                player.sendMessage(success("Loaded kit ").append(kitName(kit.name())));
                yield Command.SINGLE_SUCCESS;
            }
            case KitResult.LoadResult.NotFound(String requested) -> {
                player.sendMessage(error("No kit named '" + requested + "' exists."));
                yield 0;
            }
            case KitResult.LoadResult.InvalidName(String reason) -> {
                player.sendMessage(error(reason));
                yield 0;
            }
        };
    }

    private int handleDelete(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String rawName = StringArgumentType.getString(ctx, "name");
        return switch (service.delete(rawName)) {
            case KitResult.DeleteResult.Deleted(KitName name) -> {
                sender.sendMessage(success("Deleted kit ").append(kitName(name)));
                yield Command.SINGLE_SUCCESS;
            }
            case KitResult.DeleteResult.NotFound(String requested) -> {
                sender.sendMessage(error("No kit named '" + requested + "' exists."));
                yield 0;
            }
            case KitResult.DeleteResult.InvalidName(String reason) -> {
                sender.sendMessage(error(reason));
                yield 0;
            }
            case KitResult.DeleteResult.IoError(String message) -> {
                sender.sendMessage(error("Could not delete kit: " + message));
                yield 0;
            }
        };
    }

    private int handleList(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        List<KitName> names = service.listNames();
        if (names.isEmpty()) {
            sender.sendMessage(info("No kits have been created yet."));
            return Command.SINGLE_SUCCESS;
        }
        Component message = info("Kits (" + names.size() + "): ");
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                message = message.append(Component.text(", ", NamedTextColor.GRAY));
            }
            message = message.append(kitName(names.get(i)));
        }
        sender.sendMessage(message);
        return Command.SINGLE_SUCCESS;
    }

    private int handleInfo(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String rawName = StringArgumentType.getString(ctx, "name");
        Optional<Kit> found = service.find(rawName);
        if (found.isEmpty()) {
            sender.sendMessage(error("No kit named '" + rawName + "' exists."));
            return 0;
        }
        Kit kit = found.get();

        String creator = kit.createdBy() == null
                ? "<unknown>"
                : serverHelper.resolveOfflineName(kit.createdBy()).orElse(kit.createdBy().toString());
        String created = DATE_FORMAT.format(kit.createdAt());

        sender.sendMessage(info("Kit ").append(kitName(kit.name())));
        sender.sendMessage(info("  Creator: ").append(Component.text(creator, NamedTextColor.YELLOW)));
        sender.sendMessage(info("  Created: ").append(Component.text(created, NamedTextColor.YELLOW)));
        return Command.SINGLE_SUCCESS;
    }

    private int handleHelp(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        sender.sendMessage(info("Kit commands:"));
        if (sender.hasPermission(PERMISSION_CREATE)) {
            sender.sendMessage(helpEntry("/kit create <name>", "Save your inventory as a kit."));
        }
        if (sender.hasPermission(PERMISSION_LOAD)) {
            sender.sendMessage(helpEntry("/kit load <name>", "Replace your inventory with a kit."));
        }
        if (sender.hasPermission(PERMISSION_DELETE)) {
            sender.sendMessage(helpEntry("/kit delete <name>", "Permanently delete a kit."));
        }
        if (sender.hasPermission(PERMISSION_INFO)) {
            sender.sendMessage(helpEntry("/kit info <name>", "Show kit metadata."));
        }
        if (sender.hasPermission(PERMISSION_LIST)) {
            sender.sendMessage(helpEntry("/kit list", "List all kit names."));
        }
        sender.sendMessage(helpEntry("/kit help", "Show this help."));
        return Command.SINGLE_SUCCESS;
    }

    private static Component success(String text) {
        return PREFIX.append(Component.text(text, NamedTextColor.GREEN));
    }

    private static Component error(String text) {
        return PREFIX.append(Component.text(text, NamedTextColor.RED));
    }

    private static Component info(String text) {
        return PREFIX.append(Component.text(text, NamedTextColor.GRAY));
    }

    private static Component kitName(KitName name) {
        return Component.text(name.value(), NamedTextColor.YELLOW);
    }

    private static Component helpEntry(String usage, String description) {
        return PREFIX
                .append(Component.text(usage, NamedTextColor.YELLOW))
                .append(Component.text(" — " + description, NamedTextColor.GRAY));
    }
}
