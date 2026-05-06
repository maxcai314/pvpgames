package ax.xz.max.pvpgames.schematic;

import ax.xz.max.async.GameScheduler;
import ax.xz.max.async.Promise;
import ax.xz.max.async.Result;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.World;
import org.slf4j.Logger;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * {@link SchematicService} backed by FastAsyncWorldEdit (FAWE).
 *
 * <p>FAWE forks WorldEdit and exposes the same {@code com.sk89q.worldedit}
 * API surface, but adds two things we rely on:
 * <ul>
 *   <li>{@code EditSessionBuilder.build()} called from a non-main thread
 *       installs a parallel queue extent, which is what makes the actual
 *       block writes safe and fast off the main thread. We therefore build
 *       (and use) the {@code EditSession} entirely inside the async
 *       supplier.</li>
 *   <li>Builder options {@code fastMode}, {@code changeSetNull}, and
 *       {@code limitUnlimited} skip lighting / neighbor updates, history
 *       recording, and per-actor block limits respectively. Together these
 *       are FAWE's idiomatic equivalent of running {@code //perf off}.</li>
 * </ul>
 *
 * <p>The whole paste (file resolution, format detection, clipboard read,
 * block writes) runs inside a single {@link Promise#supplyAsync} on the
 * {@link GameScheduler#asyncExecutor() async executor}; the caller composes
 * follow-up work onto the returned promise.
 */
public final class WorldEditSchematicService implements SchematicService {

    /** Extensions tried, in order, when a short name is given without one. */
    private static final List<String> EXTENSIONS = List.of(".schem", ".schematic");

    private final Path schematicsDir;
    private final GameScheduler scheduler;
    private final Logger logger;

    public WorldEditSchematicService(Path schematicsDir, GameScheduler scheduler, Logger logger) {
        this.schematicsDir = Objects.requireNonNull(schematicsDir, "schematicsDir");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public Promise<Result<Void, SchematicError>> pasteAtOrigin(
            SchematicName schematicName, World targetWorld, BlockVec3 origin) {
        Objects.requireNonNull(schematicName, "schematicName");
        Objects.requireNonNull(targetWorld, "targetWorld");
        Objects.requireNonNull(origin, "origin");

        // Adapt the Bukkit world to a WE world up front; the resulting
        // wrapper is safe to use from the async thread.
        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(targetWorld);

        return Promise.supplyAsync(
                () -> doPaste(schematicName, weWorld, targetWorld.getName(), origin),
                scheduler.asyncExecutor());
    }

    /**
     * Resolves the file, reads the clipboard, and pastes into the world.
     * Always invoked from a non-main thread; building the EditSession here
     * is what causes FAWE to install its parallel queue.
     */
    private Result<Void, SchematicError> doPaste(
            SchematicName name,
            com.sk89q.worldedit.world.World weWorld,
            String worldName,
            BlockVec3 origin) {
        Path path = resolvePath(name.value());
        if (path == null) {
            return new Result.Err<>(new SchematicError.NotFound(
                    "Schematic '" + name.value() + "' not found in " + schematicsDir));
        }

        ClipboardFormat format = ClipboardFormats.findByFile(path.toFile());
        if (format == null) {
            return new Result.Err<>(new SchematicError.NotFound(
                    "Could not detect schematic format for " + path.toFile().getName()));
        }

        Clipboard clipboard;
        try (ClipboardReader reader = format.getReader(new FileInputStream(path.toFile()))) {
            clipboard = reader.read();
        } catch (Exception ex) {
            logger.info("Failed to read schematic {}: {}", path.toFile().getName(), ex.getMessage());
            return new Result.Err<>(new SchematicError.LoadFailed(
                    "Failed to read schematic '" + path.toFile().getName() + "': " + ex.getMessage()));
        }

        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                .world(weWorld)
                .fastMode(true)
                .changeSetNull()
                .limitUnlimited()
                .build()) {
            Operations.complete(new ClipboardHolder(clipboard)
                    .createPaste(editSession)
                    .to(BlockVector3.at(origin.x(), origin.y(), origin.z()))
                    .ignoreAirBlocks(false)
                    .build());
        } catch (Exception ex) {
            return new Result.Err<>(new SchematicError.LoadFailed(
                    "Failed to paste schematic '" + path.toFile().getName() + "' into world '"
                            + worldName + "': " + ex.getMessage()));
        }

        logger.info("Pasted schematic '{}' into world '{}' at {},{},{}.",
                path.toFile().getName(), worldName, origin.x(), origin.y(), origin.z());
        return new Result.Ok<>(null);
    }

    private Path resolvePath(String schematicName) {
        if (!Files.isDirectory(schematicsDir)) return null;
        // Allow callers to pass a name with or without extension.
        Path direct = schematicsDir.resolve(schematicName);
        if (Files.isRegularFile(direct)) return direct;
        for (String ext : EXTENSIONS) {
            Path candidate = schematicsDir.resolve(schematicName + ext);
            if (Files.isRegularFile(candidate)) return candidate;
        }
        return null;
    }
}
