package ax.xz.max.pvpgames.schematic;

import ax.xz.max.async.Promise;
import ax.xz.max.async.Result;
import org.bukkit.World;

/**
 * Fallback {@link SchematicService} used when FastAsyncWorldEdit is not
 * installed.
 *
 * <p>Returns a completed {@link Result.Err} promise from every paste; in
 * practice the caller is also wired to its own unavailable variant when
 * dependencies are missing, so this stub mostly exists to keep the type
 * system happy.
 */
public final class UnavailableSchematicService implements SchematicService {

    public static final String MESSAGE = "FastAsyncWorldEdit is not installed.";

    @Override
    public Promise<Result<Void, SchematicError>> pasteAtOrigin(
            SchematicName schematicName, World targetWorld, BlockVec3 origin) {
        return Promise.completedFuture(new Result.Err<>(new SchematicError.LoadFailed(MESSAGE)));
    }
}
