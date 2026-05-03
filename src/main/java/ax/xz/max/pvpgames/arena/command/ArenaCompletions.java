package ax.xz.max.pvpgames.arena.command;

import ax.xz.max.pvpgames.arena.ArenaName;
import ax.xz.max.pvpgames.arena.ArenaService;
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
    public static SuggestionProvider<CommandSourceStack> knownArenaNames(ArenaService service) {
        Objects.requireNonNull(service, "service");
        return Completions.fromStrings(() -> service.listNames().stream().map(ArenaName::value).toList());
    }
}
