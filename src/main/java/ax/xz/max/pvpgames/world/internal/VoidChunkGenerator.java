package ax.xz.max.pvpgames.world.internal;

import org.bukkit.generator.ChunkGenerator;

/**
 * A {@link ChunkGenerator} that produces only air. Arena previews and
 * sessions paste schematics into worlds built with this generator, so
 * the surrounding terrain stays invisible.
 *
 * <p>Wired directly via Bukkit's
 * {@code WorldCreator#generator(ChunkGenerator)} in
 * {@code BukkitWorldService}; no plugin-yml registration or
 * plugin-level {@code getDefaultWorldGenerator} override is needed.
 */
public final class VoidChunkGenerator extends ChunkGenerator {

    // shouldGenerateBedrock is deprecated since 1.19.2; bedrock is now
    // part of the surface step, so disabling surface disables bedrock too.
    @Override public boolean shouldGenerateNoise()        { return false; }
    @Override public boolean shouldGenerateSurface()      { return false; }
    @Override public boolean shouldGenerateCaves()        { return false; }
    @Override public boolean shouldGenerateDecorations()  { return false; }
    @Override public boolean shouldGenerateMobs()         { return false; }
    @Override public boolean shouldGenerateStructures()   { return false; }
}
