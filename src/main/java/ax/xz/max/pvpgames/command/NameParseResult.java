package ax.xz.max.pvpgames.command;

/**
 * Outcome of parsing a raw name string into a typed identifier (e.g.
 * {@code KitName}, {@code ArenaName}).
 *
 * <p>Used uniformly across resource types so service code never has to write a
 * try/catch around {@link IllegalArgumentException} from each name record's
 * canonical constructor.
 *
 * @param <N> the validated name type
 */
public sealed interface NameParseResult<N> {

    /** Parsing succeeded; {@link #name} is the validated identifier. */
    record Valid<N>(N name) implements NameParseResult<N> {}

    /** Parsing failed; {@link #reason} is a player-facing message. */
    record Invalid<N>(String reason) implements NameParseResult<N> {}
}
