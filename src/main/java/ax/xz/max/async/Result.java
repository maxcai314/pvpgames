package ax.xz.max.async;

/**
 * Represents a result, with an {@link Result.Ok} variant and an {@link Result.Err} variant,
 * with separate data types.
 * @param <T> The successful result type
 * @param <V> The error result type
 */
public sealed interface Result<T, V> permits Result.Ok, Result.Err {
    // silly helper methods
    boolean isOk();
    boolean isErr();

    record Ok<T, V>(T val) implements Result<T, V> {
        @Override
        public boolean isOk() {
            return true;
        }

        @Override
        public boolean isErr() {
            return false;
        }
    }

    record Err<T, V>(V val) implements Result<T, V> {
        @Override
        public boolean isOk() {
            return false;
        }

        @Override
        public boolean isErr() {
            return true;
        }
    }
}
