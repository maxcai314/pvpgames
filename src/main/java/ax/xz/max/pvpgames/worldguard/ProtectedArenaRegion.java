package ax.xz.max.pvpgames.worldguard;

import java.util.Map;

/**
 * Opaque handle to a protected region created by
 * {@link WorldGuardService#createRegion}. Implementations may be backed
 * by one or several underlying WorldGuard regions; callers do not see
 * the difference. The only operations exposed are containment tests
 * and live flag mutation.
 *
 * <p>Lifetime: a region is valid from the moment
 * {@link WorldGuardService#createRegion} returns it until either
 * {@link WorldGuardService#removeRegion} is called with it or the
 * owning service is {@linkplain WorldGuardService#shutdown shut down}.
 */
public interface ProtectedArenaRegion {

    /**
     * True if the integer point {@code (x, y, z)} lies inside this
     * region's boundary. For multi-region implementations the boundary
     * is the outermost region (the one a player would have to cross to
     * leave the protected area).
     */
    boolean contains(int x, int y, int z);

    /**
     * Apply textual flag values to this region. Each entry's flag name
     * is looked up in WorldGuard's flag registry; the raw string value
     * is parsed by the flag's own {@code parseInput}, so the format is
     * whatever WG accepts (for example {@code "allow"} / {@code "deny"}
     * for state flags, {@code "true"} / {@code "false"} for boolean
     * flags, integers for integer flags).
     *
     * <p>Flags are applied to the default group only; group-targeted
     * flag overrides are out of scope.
     *
     * @throws WorldGuardException if a flag name is unknown to the
     *         registry, a value fails to parse, or the underlying
     *         WorldGuard region has already been removed
     */
    void applyFlags(Map<String, String> flags) throws WorldGuardException;
}
