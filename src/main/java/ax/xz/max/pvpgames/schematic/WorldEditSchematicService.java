package ax.xz.max.pvpgames.schematic;

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
import com.sk89q.worldedit.util.SideEffectSet;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * {@link SchematicService} backed by FastAsyncWorldEdit (FAWE).
 *
 * <p>FAWE is a fork of WorldEdit that exposes the same API but also makes
 * block-touching operations safe to call from non-main threads. This service
 * relies on that property to schedule the actual paste on Bukkit's async
 * executor; the {@link #pasteAtOrigin} call returns as soon as the schematic
 * file has been resolved and its format has been detected, and the paste
 * itself happens in the background. Errors raised during the async paste are
 * logged but cannot be propagated to the caller.
 *
 * <p>The service still compiles against vanilla WorldEdit's classes; FAWE is
 * a drop-in replacement at runtime. If a server is running vanilla WorldEdit
 * instead of FAWE, the async paste will fail with thread-safety errors. The
 * plugin's {@code softdepend} therefore lists both names and the runServer
 * task installs FAWE specifically.
 *
 * <p>Resolves schematic short-names against a configured directory (typically
 * {@code plugins/WorldEdit/schematics/}) and supports any extension WorldEdit
 * recognises ({@code .schem}, {@code .schematic}, etc.). The file name on
 * disk is found by trying each extension in order.
 */
public final class WorldEditSchematicService implements SchematicService {

    /** Extensions tried, in order, when a short name is given without one. */
    private static final List<String> EXTENSIONS = List.of(".schem", ".schematic");

    private final Path schematicsDir;
    private final Plugin plugin;
    private final Logger logger;

    public WorldEditSchematicService(Path schematicsDir, Plugin plugin, Logger logger) {
        this.schematicsDir = Objects.requireNonNull(schematicsDir, "schematicsDir");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public boolean schematicExists(SchematicName schematicName) {
        Objects.requireNonNull(schematicName, "schematicName");
        return resolvePath(schematicName.value()) != null;
    }

    @Override
    public void pasteAtOrigin(SchematicName schematicName, World targetWorld, BlockVec3 origin) throws SchematicException {
        Objects.requireNonNull(schematicName, "schematicName");
        Objects.requireNonNull(targetWorld, "targetWorld");
        Objects.requireNonNull(origin, "origin");

        Path path = resolvePath(schematicName.value());
        if (path == null) {
            throw new SchematicException.NotFound(
                    "Schematic '" + schematicName.value() + "' not found in " + schematicsDir);
        }

        // findByFile is the only alternative supported by FAWE
        ClipboardFormat format = ClipboardFormats.findByFile(path.toFile());
        if (format == null) {
            throw new SchematicException.NotFound(
                    "Could not detect schematic format for " + path.toFile().getName());
        }

        // run worldedit task asynchronously using FAWE
        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(targetWorld);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                () -> runPaste(path, format, weWorld, targetWorld.getName(), origin));
    }

    /**
     * Reads the schematic file and pastes it. Always invoked from a
     * non-main thread; FAWE makes the block-touching calls inside the
     * {@link EditSession} safe to run there.
     */
    private void runPaste(Path path, ClipboardFormat format,
                          com.sk89q.worldedit.world.World weWorld,
                          String worldName, BlockVec3 origin) {
        Clipboard clipboard;
        try (ClipboardReader reader = format.getReader(new FileInputStream(path.toFile()))) {
            clipboard = reader.read();
        } catch (Exception ex) {
            logger.error("Failed to read schematic '{}': {}", path.toFile().getName(), ex.getMessage(), ex);
            return;
        }
        try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
            // skip side effects for performance
            editSession.setSideEffectApplier(SideEffectSet.none());
            Operations.complete(new ClipboardHolder(clipboard)
                    .createPaste(editSession)
                    .to(BlockVector3.at(origin.x(), origin.y(), origin.z()))
                    .ignoreAirBlocks(false)
                    .build());
        } catch (Exception ex) {
            logger.error("Failed to paste schematic '{}' into world '{}': {}",
                    path.toFile().getName(), worldName, ex.getMessage(), ex);
            return;
        }
        logger.info("Pasted schematic '{}' into world '{}' at {},{},{}.",
                path.toFile().getName(), worldName, origin.x(), origin.y(), origin.z());
    }

    // todo: is this the idiomatic way to access worldedit schematics folder?
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
