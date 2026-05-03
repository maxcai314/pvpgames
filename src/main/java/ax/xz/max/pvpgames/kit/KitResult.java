package ax.xz.max.pvpgames.kit;

/**
 * Outcomes of {@link KitService} operations.
 *
 * <p>Sealed result types let command handlers exhaustively pattern-match on
 * each operation's failure surface and emit precise messages without juggling
 * exceptions or losing information through {@code Optional}.
 */
public sealed interface KitResult {

    /** Common interface for variants that carry an I/O failure message. */
    sealed interface IoFailure extends KitResult {
        String message();
    }

    /** Outcome of a {@code create} operation. */
    sealed interface CreateResult extends KitResult {
        record Created(Kit kit, boolean overwrote) implements CreateResult {}
        record InvalidName(String reason) implements CreateResult {}
        record EmptyInventory() implements CreateResult {}
        record IoError(String message) implements CreateResult, IoFailure {
            @Override public String message() { return message; }
        }
    }

    /** Outcome of a {@code load} operation. */
    sealed interface LoadResult extends KitResult {
        record Loaded(Kit kit) implements LoadResult {}
        record NotFound(String requestedName) implements LoadResult {}
        record InvalidName(String reason) implements LoadResult {}
    }

    /** Outcome of a {@code delete} operation. */
    sealed interface DeleteResult extends KitResult {
        record Deleted(KitName name) implements DeleteResult {}
        record NotFound(String requestedName) implements DeleteResult {}
        record InvalidName(String reason) implements DeleteResult {}
        record IoError(String message) implements DeleteResult, IoFailure {
            @Override public String message() { return message; }
        }
    }
}
