package ax.xz.max.pvpgames.arena;

import org.bukkit.World;

import java.util.List;

/**
 * Outcomes of {@link ArenaService} operations.
 *
 * <p>Sealed result types let command handlers exhaustively pattern-match on
 * each operation's failure surface and emit precise messages without juggling
 * exceptions or losing information through {@link java.util.Optional Optional}.
 */
public sealed interface ArenaResult {

    /** Common interface for variants that carry an I/O failure message. */
    sealed interface IoFailure extends ArenaResult {
        String message();
    }

    /** Outcome of a {@code create} operation. */
    sealed interface CreateResult extends ArenaResult {
        record Created(Arena arena, boolean overwrote) implements CreateResult {}
        record InvalidName(String reason) implements CreateResult {}
        record InvalidSchematic(String reason) implements CreateResult {}
        record IoError(String message) implements CreateResult, IoFailure {
            @Override public String message() { return message; }
        }
    }

    /** Outcome of a {@code delete} operation. */
    sealed interface DeleteResult extends ArenaResult {
        record Deleted(ArenaName name) implements DeleteResult {}
        record NotFound(String requestedName) implements DeleteResult {}
        record InvalidName(String reason) implements DeleteResult {}
        record IoError(String message) implements DeleteResult, IoFailure {
            @Override public String message() { return message; }
        }
    }

    /**
     * Outcome of a {@code preview} operation: a brand-new session is created
     * for the named arena and the caller is joined to it.
     */
    sealed interface PreviewResult extends ArenaResult {
        record Started(ArenaSession session, World world, boolean noSpawnsYet) implements PreviewResult {}
        record InvalidName(String reason) implements PreviewResult {}
        record NotFound(String requestedName) implements PreviewResult {}
        record SchematicMissing(String schematicName) implements PreviewResult {}
        record SchematicLoadFailed(String schematicName, String message) implements PreviewResult {}
        record WorldFailed(String message) implements PreviewResult {}
        /** WorldEdit or Multiverse-Core is not installed. */
        record DependencyMissing(String message) implements PreviewResult {}
    }

    /**
     * Outcome of a {@code join} operation: the caller is added to an existing
     * session.
     */
    sealed interface JoinResult extends ArenaResult {
        record Joined(ArenaSession session, boolean noSpawnsYet) implements JoinResult {}
        record SessionNotFound(long sessionId) implements JoinResult {}
        record DependencyMissing(String message) implements JoinResult {}
    }

    /** Outcome of an {@code addSpawn} operation. */
    sealed interface AddSpawnResult extends ArenaResult {
        record Added(Arena arena, int oneBasedIndex) implements AddSpawnResult {}
        record NotInArenaWorld() implements AddSpawnResult {}
        record ArenaMissing(String requestedName) implements AddSpawnResult {}
        record IoError(String message) implements AddSpawnResult, IoFailure {
            @Override public String message() { return message; }
        }
    }

    /** Outcome of a {@code listSpawns} operation. */
    sealed interface ListSpawnResult extends ArenaResult {
        record Listed(Arena arena, List<SpawnPoint> spawns) implements ListSpawnResult {}
        record NotInArenaWorld() implements ListSpawnResult {}
        record ArenaMissing(String requestedName) implements ListSpawnResult {}
    }

    /** Outcome of a {@code visitSpawn} operation. */
    sealed interface VisitSpawnResult extends ArenaResult {
        record Visited(Arena arena, int oneBasedIndex, SpawnPoint spawn) implements VisitSpawnResult {}
        record NotInArenaWorld() implements VisitSpawnResult {}
        record ArenaMissing(String requestedName) implements VisitSpawnResult {}
        record IndexOutOfRange(int requested, int size) implements VisitSpawnResult {}
    }

    /** Outcome of a {@code removeSpawn} operation. */
    sealed interface RemoveSpawnResult extends ArenaResult {
        record Removed(Arena arena, int oneBasedIndex, SpawnPoint removed) implements RemoveSpawnResult {}
        record NotInArenaWorld() implements RemoveSpawnResult {}
        record ArenaMissing(String requestedName) implements RemoveSpawnResult {}
        record IndexOutOfRange(int requested, int size) implements RemoveSpawnResult {}
        record IoError(String message) implements RemoveSpawnResult, IoFailure {
            @Override public String message() { return message; }
        }
    }

    /** Outcome of a {@code leave} operation. */
    sealed interface LeaveResult extends ArenaResult {
        record Returned() implements LeaveResult {}
        record NoActiveSession() implements LeaveResult {}
    }
}
