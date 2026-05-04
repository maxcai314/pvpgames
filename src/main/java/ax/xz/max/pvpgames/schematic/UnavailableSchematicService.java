package ax.xz.max.pvpgames.schematic;

import org.bukkit.World;

/**
 * Fallback {@link SchematicService} used when WorldEdit is not installed.
 */
public final class UnavailableSchematicService implements SchematicService {

    public static final String MESSAGE = "WorldEdit is not installed.";

    @Override
    public void pasteAtOrigin(SchematicName schematicName, World targetWorld, BlockVec3 origin) throws SchematicException {
        throw new SchematicException(MESSAGE);
    }

    @Override
    public boolean schematicExists(SchematicName schematicName) {
        return false;
    }
}
