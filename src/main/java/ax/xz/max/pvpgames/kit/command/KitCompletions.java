package ax.xz.max.pvpgames.kit.command;

import ax.xz.max.pvpgames.command.Completions;
import ax.xz.max.pvpgames.kit.KitName;
import ax.xz.max.pvpgames.kit.KitService;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.util.Objects;

/**
 * Brigadier {@link SuggestionProvider}s for kit-related arguments.
 */
public final class KitCompletions {

    private KitCompletions() {}

    /** Suggests every known kit name, filtered by the typed prefix. */
    public static SuggestionProvider<CommandSourceStack> knownKitNames(KitService service) {
        Objects.requireNonNull(service, "service");
        return Completions.fromStrings(() -> service.listNames().stream().map(KitName::value).toList());
    }
}
