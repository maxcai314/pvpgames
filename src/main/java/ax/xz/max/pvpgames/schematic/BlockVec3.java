package ax.xz.max.pvpgames.schematic;

/**
 * Integer (x, y, z) triple. Plugin-internal type that stands in for
 * WorldEdit's {@code BlockVector3} so the WE class doesn't leak through the
 * {@link SchematicService} interface.
 */
public record BlockVec3(int x, int y, int z) {
}
