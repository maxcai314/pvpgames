package ax.xz.max.pvpgames.schematic;

import ax.xz.max.async.Promise;
import ax.xz.max.async.Result;
import org.bukkit.World;

/**
 * Loads and pastes WorldEdit schematics. The actual paste runs on a
 * background thread (FAWE makes the block-touching calls thread-safe), so
 * {@link #pasteAtOrigin} returns a {@link Promise} the caller can chain
 * follow-up work onto.
 *
 * <p>Errors are surfaced through the {@link Result} on the success side of
 * the promise rather than through exceptions, so callers can pattern-match
 * on {@link SchematicError} alongside other async results.
 *
 * <p>An {@code Unavailable} fallback is wired in when the underlying plugin
 * is missing; it returns a completed {@code Err} promise from every call.
 */
public interface SchematicService {

    /**
     * Pastes the named schematic into {@code targetWorld} with its bottom
     * northwest corner at {@code origin}. Air blocks in the schematic replace
     * existing blocks, so a void world ends up with exactly the schematic's
     * contents.
     *
     * @return a promise that completes with {@link Result.Ok} when the paste
     *         finishes, or {@link Result.Err} carrying a {@link SchematicError}
     *         if the file is missing or fails to load
     */
    Promise<Result<Void, SchematicError>> pasteAtOrigin(
            SchematicName schematicName, World targetWorld, BlockVec3 origin);

}
