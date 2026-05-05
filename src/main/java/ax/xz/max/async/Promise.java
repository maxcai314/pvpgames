package ax.xz.max.async;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A promise that can be composed.
 * Based on the CompletableFuture system, but simplified specifically for BukkitScheduler functions
 * in the context of handling Minecraft events. This object acts as a handle to a future value,
 * with features to compose more asynchronous events off of it.
 * Uses a {@link GameExecutor} in order to determine where the next completion stage should be run.
 */
public final class Promise<T> {
    private final CompletableFuture<T> delegate;

    private Promise(CompletableFuture<T> delegate) {
        this.delegate = delegate;
    }

    /**
     * @return true if the computed result is available
     */
    public boolean isDone() {
        return delegate.isDone();
    }

    /**
     * Gets the result without waiting.
     * Should only be called if the code has already checked that {@link #isDone()} is true.
     */
    public T resultNow() {
        return delegate.resultNow();
    }

    /**
     * Returns a new Promise that, when this stage completes normally,
     * is executed using the supplied Executor,
     * with this stage's result as the argument to the supplied function.
     * <p>Equivalent to "map" in some functional programming environments.
     */
    public <U> Promise<U> thenApplyAsync(Function<? super T,? extends U> fn, GameExecutor executor) {
        return new Promise<>(delegate.thenApplyAsync(fn, executor));
    }

    /**
     * Returns a new Promise that, when this stage completes normally,
     * is executed using the supplied GameExecutor,
     * with this stage's result as the argument to the supplied function.
     * <p>Equivalent to "flatMap" in some functional programming environments.
     */
    public <U> Promise<U> thenComposeAsync(Function<? super T,Promise<U>> fn, GameExecutor executor) {
        return new Promise<>(delegate.thenComposeAsync(t -> fn.apply(t).delegate, executor));
    }

    /**
     * Returns a new Promise that, when this stage completes normally,
     * executes the given action using the supplied GameExecutor.
     */
    public Promise<Void> thenRunAsync(Runnable action, GameExecutor executor) {
        return new Promise<>(delegate.thenRunAsync(action, executor));
    }

    /**
     * Returns a new Promise that, when this stage completes normally,
     * is executed using the supplied Executor, with this stage's result as the argument to the supplied action.
     */
    public Promise<Void> thenAcceptAsync(Consumer<? super T> action, GameExecutor executor) {
        return new Promise<>(delegate.thenAcceptAsync(action, executor));
    }

    /**
     * Returns a new Promise that, when this and the other given stage both complete normally,
     * is executed using the supplied executor, with the two results as arguments to the supplied action.
     */
    public <U> Promise<Void> thenAcceptBothAsync(Promise<? extends U> other, BiConsumer<? super T,? super U> action, GameExecutor executor) {
        return new Promise<>(delegate.thenAcceptBothAsync(other.delegate, action, executor));
    }

    /**
     * Returns a new Promise that, when this and the other given stage complete normally,
     * is executed using the supplied executor, with the two results as arguments to the supplied function.
     */
    public <U,V> Promise<V> thenCombineAsync(Promise<? extends U> other, BiFunction<? super T,? super U,? extends V> fn, GameExecutor executor) {
        return new Promise<>(delegate.thenCombineAsync(other.delegate, fn, executor));
    }

    /**
     * Returns a new Promise that, when either this or the other given stage complete normally,
     * is executed using the supplied executor, with the corresponding result as argument to the supplied function.
     */
    public Promise<Void> acceptEitherAsync(Promise<? extends T> other, Consumer<? super T> action, GameExecutor executor) {
        return new Promise<>(delegate.acceptEitherAsync(other.delegate, action, executor));
    }

    /**
     * Returns a new Promise that, when either this or the other given stage complete normally,
     * is executed using the supplied executor, with the corresponding result as argument to the supplied function.
     */
    public <U> Promise<U> applyToEitherAsync(Promise<? extends T> other, Function<? super T,U> fn, GameExecutor executor) {
        return new Promise<>(delegate.applyToEitherAsync(other.delegate, fn, executor));
    }

    /**
     * Returns a new Promise that, when this and the other given stage both complete normally,
     * executes the given action using the supplied executor.
     */
    public Promise<Void> runAfterBothAsync(Promise<?> other, Runnable action, GameExecutor executor) {
        return new Promise<>(delegate.runAfterBothAsync(other.delegate, action, executor));
    }

    /**
     * Returns a new Promise that, when either this or the other given stage complete normally,
     * executes the given action using the supplied executor.
     */
    public Promise<Void> runAfterEitherAsync(Promise<?> other, Runnable action, GameExecutor executor) {
        return new Promise<>(delegate.runAfterEitherAsync(other.delegate, action, executor));
    }

    /**
     * Returns a new Promise that, when this stage completes either normally or exceptionally,
     * is executed using the supplied executor, with this stage's result and exception as arguments to the supplied function.
     */
    public <U> Promise<U> handleAsync(BiFunction<? super T,Throwable,? extends U> fn, GameExecutor executor) {
        return new Promise<>(delegate.handleAsync(fn, executor));
    }

    /**
     * Returns a new Promise with the same result or exception as this stage,
     * that executes the given action using the supplied Executor when this stage completes.
     */
    public Promise<T> whenCompleteAsync(BiConsumer<? super T,? super Throwable> action, GameExecutor executor) {
        return new Promise<>(delegate.whenCompleteAsync(action, executor));
    }

    /**
     * Returns a new Promise that is asynchronously completed
     * by a task running in the given executor after it runs the given action.
     */
    public static Promise<Void> runAsync(Runnable runnable, GameExecutor executor) {
        return new Promise<>(CompletableFuture.runAsync(runnable, executor));
    }

    /**
     * Returns a new Promise that is asynchronously completed
     * by a task running in the given executor with the value obtained by calling the given Supplier.
     */
    public static <U> Promise<U> supplyAsync(Supplier<U> supplier, GameExecutor executor) {
        return new Promise<>(CompletableFuture.supplyAsync(supplier, executor));
    }

    /**
     * Returns a new Promise that is already completed with the given value.
     */
    public static <U> Promise<U> completedFuture(U value) {
        return new Promise<>(CompletableFuture.completedFuture(value));
    }

    /**
     * Returns a new Promise that is completed when all of the given Promises complete.
     * If no Promises are provided, returns a Promise completed with the value null.
     */
    public static Promise<Void> allOf(Promise<?>... promises) {
        return new Promise<>(CompletableFuture.allOf(
                Stream.of(promises).map(p -> p.delegate).toArray(CompletableFuture[]::new)
        ));
    }

    /**
     * Returns a new CompletableFuture that is completed when any of the given Promises complete, with the same result.
     * If no Promises are provided, returns an incomplete Promise.
     */
    public static Promise<Object> anyOf(Promise<?>... promises) {
        return new Promise<>(CompletableFuture.anyOf(
                Stream.of(promises).map(p -> p.delegate).toArray(CompletableFuture[]::new)
        ));
    }

}
