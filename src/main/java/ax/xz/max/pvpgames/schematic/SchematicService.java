package ax.xz.max.pvpgames.schematic;

import org.bukkit.World;

/**
 * Abstraction over a schematic-loading plugin (currently WorldEdit).
 *
 * <p>Lookup is by short name (e.g. {@code "myarena"}); the implementation
 * resolves it to a file under WorldEdit's schematics directory. When the
 * underlying plugin is missing, a fallback impl throws
 * {@link SchematicException} from every method so the rest of the plugin
 * keeps working.
 */
public interface SchematicService {

    /**
     * Pastes the schematic at the given origin in the target world. Air blocks
     * in the schematic do replace existing blocks (so an empty void world ends
     * up with exactly the schematic's contents).
     *
     * @throws SchematicException.NotFound    if no file matches the name
     * @throws SchematicException.LoadFailed  if the file exists but cannot be parsed/pasted
     * @throws SchematicException             if the schematic plugin is unavailable
     */
    void pasteAtOrigin(String schematicName, World targetWorld, BlockVec3 origin) throws SchematicException;

    /**
     * @return {@code true} if a schematic file with this name resolves to a
     *         readable file on disk
     */
    boolean schematicExists(String schematicName);
}
