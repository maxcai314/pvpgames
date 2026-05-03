package ax.xz.max.pvpgames.command;

import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Reusable Brigadier {@link SuggestionProvider}s.
 */
public final class Completions {

    private Completions() {}

    /**
     * Suggests every string in {@code source}, filtered by the prefix already
     * typed. The {@link Supplier} is queried per-completion so the suggester
     * always sees the latest list (e.g. when a kit was created mid-session).
     */
    public static SuggestionProvider<CommandSourceStack> fromStrings(
            Supplier<? extends Collection<String>> source) {
        Objects.requireNonNull(source, "source");
        return (ctx, builder) -> {
            String prefix = builder.getRemainingLowerCase();
            for (String value : source.get()) {
                if (value.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    builder.suggest(value);
                }
            }
            return builder.buildFuture();
        };
    }
}
