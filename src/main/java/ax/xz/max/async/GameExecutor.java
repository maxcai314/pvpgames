package ax.xz.max.async;

import org.jspecify.annotations.NonNull;

import java.util.concurrent.Executor;

/**
 * A direct copy of the {@link java.util.concurrent.Executor} interface.
 * This interface type is extended to hint the code to supply an Executor
 * specifically intended to process game tasks.
 */
public interface GameExecutor extends Executor {
    @Override
    void execute(@NonNull Runnable command);
}
