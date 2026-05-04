package ax.xz.max.pvpgames.arena;

import java.util.List;

/**
 * Outcomes of {@link ArenaManager} and {@link ArenaSession} operations.
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

    /** Outcome of an arena-create operation. */
    sealed interface CreateResult extends ArenaResult {
        record Created(Arena arena, boolean overwrote) implements CreateResult {}
        record InvalidName(String reason) implements CreateResult {}
        record InvalidSchematic(String reason) implements CreateResult {}
        record IoError(String message) implements CreateResult, IoFailure {
            @Override public String message() { return message; }
        }
    }

    /** Outcome of an arena-delete operation. */
    sealed interface DeleteResult extends ArenaResult {
        record Deleted(ArenaName name) implements DeleteResult {}
        record NotFound(String requestedName) implements DeleteResult {}
        record InvalidName(String reason) implements DeleteResult {}
        record IoError(String message) implements DeleteResult, IoFailure {
            @Override public String message() { return message; }
        }
    }

    /**
     * Outcome of {@link ArenaManager#openSession}: a session is allocated a
     * grid slot in the shared world, the schematic is pasted at that origin,
     * a WorldGuard region is registered, and the arena's flags are applied.
     */
    sealed interface OpenSessionResult extends ArenaResult {
        record Opened(ArenaSession session, boolean noSpawnsYet) implements OpenSessionResult {}
        record InvalidName(String reason) implements OpenSessionResult {}
        record NotFound(String requestedName) implements OpenSessionResult {}
        record SchematicMissing(String schematicName) implements OpenSessionResult {}
        record SchematicLoadFailed(String schematicName, String message) implements OpenSessionResult {}
        record AllocatorExhausted(int maxSlots) implements OpenSessionResult {}
        record RegionFailed(String message) implements OpenSessionResult {}
        record InvalidFlag(String flagName, String reason) implements OpenSessionResult {}
        /** Multiverse-Core, WorldEdit, or WorldGuard is not installed. */
        record DependencyMissing(String message) implements OpenSessionResult {}
    }

    /** Outcome of {@link ArenaManager#closeSession}. */
    sealed interface CloseSessionResult extends ArenaResult {
        record Closed(long sessionId) implements CloseSessionResult {}
        record NotFound(long sessionId) implements CloseSessionResult {}
    }

    /**
     * Outcome of {@link ArenaSession#joinPlayer}: the player's pre-session
     * state is captured (if not already cached), a clean baseline is applied,
     * and they are teleported to the first spawn (or the session origin if
     * the spawn list is empty).
     */
    sealed interface JoinResult extends ArenaResult {
        record Joined(ArenaSession session, boolean noSpawnsYet) implements JoinResult {}
    }

    /** Outcome of {@link ArenaSession#leavePlayer}. */
    sealed interface LeaveResult extends ArenaResult {
        record Returned() implements LeaveResult {}
        record NoActiveSession() implements LeaveResult {}
    }

    /** Outcome of an add-spawn operation on a session. */
    sealed interface AddSpawnResult extends ArenaResult {
        record Added(Arena arena, int oneBasedIndex) implements AddSpawnResult {}
        record IoError(String message) implements AddSpawnResult, IoFailure {
            @Override public String message() { return message; }
        }
    }

    /** Outcome of a list-spawns operation on a session. */
    sealed interface ListSpawnResult extends ArenaResult {
        record Listed(Arena arena, List<SpawnPoint> spawns) implements ListSpawnResult {}
    }

    /** Outcome of a visit-spawn operation on a session. */
    sealed interface VisitSpawnResult extends ArenaResult {
        record Visited(Arena arena, int oneBasedIndex, SpawnPoint spawn) implements VisitSpawnResult {}
        record IndexOutOfRange(int requested, int size) implements VisitSpawnResult {}
    }

    /** Outcome of a remove-spawn operation on a session. */
    sealed interface RemoveSpawnResult extends ArenaResult {
        record Removed(Arena arena, int oneBasedIndex, SpawnPoint removed) implements RemoveSpawnResult {}
        record IndexOutOfRange(int requested, int size) implements RemoveSpawnResult {}
        record IoError(String message) implements RemoveSpawnResult, IoFailure {
            @Override public String message() { return message; }
        }
    }
}
