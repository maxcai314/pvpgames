package ax.xz.max.pvpgames.world;

import org.bukkit.generator.ChunkGenerator;

/**
 * A {@link ChunkGenerator} that produces only air. Used by
 * {@link MultiverseWorldService} to build the empty void worlds that arena
 * previews paste schematics into.
 *
 * <p>{@link ax.xz.max.pvpgames.PvpgamesPlugin} returns this from
 * {@code getDefaultWorldGenerator}, so passing
 * {@code CreateWorldOptions#generator("pvpgames")} to Multiverse routes here.
 */
public final class VoidChunkGenerator extends ChunkGenerator {

    @Override public boolean shouldGenerateNoise()        { return false; }
    @Override public boolean shouldGenerateSurface()      { return false; }
    @Override public boolean shouldGenerateBedrock()      { return false; }
    @Override public boolean shouldGenerateCaves()        { return false; }
    @Override public boolean shouldGenerateDecorations()  { return false; }
    @Override public boolean shouldGenerateMobs()         { return false; }
    @Override public boolean shouldGenerateStructures()   { return false; }
}
