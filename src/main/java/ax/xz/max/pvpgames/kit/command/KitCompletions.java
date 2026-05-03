package ax.xz.max.pvpgames.kit.command;

import ax.xz.max.pvpgames.kit.KitName;
import ax.xz.max.pvpgames.kit.KitService;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.util.Locale;
import java.util.Objects;

/**
 * Brigadier {@link SuggestionProvider}s for kit-related arguments.
 */
public final class KitCompletions {

    private KitCompletions() {}

    /**
     * Suggests every known kit name, filtered by the prefix the user has typed.
     * Reads from the in-memory cache so it is safe to invoke from any thread
     * Brigadier hands to a suggestion provider.
     */
    public static SuggestionProvider<CommandSourceStack> knownKitNames(KitService service) {
        Objects.requireNonNull(service, "service");
        return (ctx, builder) -> {
            String prefix = builder.getRemainingLowerCase();
            for (KitName name : service.listNames()) {
                if (name.value().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    builder.suggest(name.value());
                }
            }
            return builder.buildFuture();
        };
    }
}
