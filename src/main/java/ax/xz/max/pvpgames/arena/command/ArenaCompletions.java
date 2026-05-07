package ax.xz.max.pvpgames.arena.command;

import ax.xz.max.pvpgames.arena.ArenaFlags;
import ax.xz.max.pvpgames.arena.ArenaManager;
import ax.xz.max.pvpgames.arena.ArenaName;
import ax.xz.max.pvpgames.command.Completions;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.util.Objects;

/**
 * Brigadier {@link SuggestionProvider}s for arena-related arguments.
 */
public final class ArenaCompletions {

    private ArenaCompletions() {}

    /** Suggests every known arena name, filtered by the typed prefix. */
    public static SuggestionProvider<CommandSourceStack> knownArenaNames(ArenaManager manager) {
        Objects.requireNonNull(manager, "manager");
        return Completions.fromStrings(() -> manager.listNames().stream().map(ArenaName::value).toList());
    }

    /** Suggests every configurable {@link ArenaFlags} flag name. */
    public static SuggestionProvider<CommandSourceStack> arenaFlagNames() {
        return Completions.fromStrings(() -> ArenaFlags.FLAG_NAMES);
    }
}
