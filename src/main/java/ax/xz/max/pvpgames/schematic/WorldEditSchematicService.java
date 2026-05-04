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
import org.bukkit.World;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * {@link SchematicService} backed by WorldEdit.
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
    private final Logger logger;

    public WorldEditSchematicService(Path schematicsDir, Logger logger) {
        this.schematicsDir = Objects.requireNonNull(schematicsDir, "schematicsDir");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public boolean schematicExists(SchematicName schematicName) {
        Objects.requireNonNull(schematicName, "schematicName");
        return resolveFile(schematicName.value()) != null;
    }

    @Override
    public void pasteAtOrigin(SchematicName schematicName, World targetWorld, BlockVec3 origin) throws SchematicException {
        Objects.requireNonNull(schematicName, "schematicName");
        Objects.requireNonNull(targetWorld, "targetWorld");
        Objects.requireNonNull(origin, "origin");

        File file = resolveFile(schematicName.value());
        if (file == null) {
            throw new SchematicException.NotFound(
                    "Schematic '" + schematicName.value() + "' not found in " + schematicsDir);
        }

        // todo: deprecated worldedit logic
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) {
            throw new SchematicException.NotFound(
                    "Could not detect schematic format for " + file.getName());
        }

        Clipboard clipboard;
        try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
            clipboard = reader.read();
        } catch (Exception ex) {
            throw new SchematicException.LoadFailed(
                    "Failed to read schematic '" + file.getName() + "': " + ex.getMessage(), ex);
        }

        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(targetWorld);
        try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
            Operations.complete(new ClipboardHolder(clipboard)
                    .createPaste(editSession)
                    .to(BlockVector3.at(origin.x(), origin.y(), origin.z()))
                    .ignoreAirBlocks(false)
                    .build());
        } catch (Exception ex) {
            throw new SchematicException.LoadFailed(
                    "Failed to paste schematic '" + file.getName() + "' into world '"
                            + targetWorld.getName() + "': " + ex.getMessage(), ex);
        }
        logger.info("Pasted schematic '{}' into world '{}' at {},{},{}.",
                file.getName(), targetWorld.getName(), origin.x(), origin.y(), origin.z());
    }

    private File resolveFile(String schematicName) {
        if (!Files.isDirectory(schematicsDir)) return null;
        // Allow callers to pass a name with or without extension.
        Path direct = schematicsDir.resolve(schematicName);
        if (Files.isRegularFile(direct)) return direct.toFile();
        for (String ext : EXTENSIONS) {
            Path candidate = schematicsDir.resolve(schematicName + ext);
            if (Files.isRegularFile(candidate)) return candidate.toFile();
        }
        return null;
    }
}
