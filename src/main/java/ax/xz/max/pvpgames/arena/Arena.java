package ax.xz.max.pvpgames.arena;

import ax.xz.max.pvpgames.schematic.SchematicName;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A persisted arena definition: a named reference to a WorldEdit schematic plus
 * an ordered list of {@link SpawnPoint}s.
 *
 * <p>Immutable. All "mutators" ({@link #withSchematic(String)},
 * {@link #addSpawn(SpawnPoint)}, {@link #removeSpawnAt(int)}) return a new
 * {@link Arena}; persisting the new value is the service's responsibility.
 *
 * <p>{@code createdBy} is the {@link UUID} of the player who created the arena;
 * it may be {@code null} if the arena was created by the console or another
 * non-player source. {@code createdAt} is captured at save time so the info
 * command can display age without rereading file timestamps.
 */
public record Arena(
        ArenaName name,
        SchematicName schematicName,
        List<SpawnPoint> spawns,
        Instant createdAt,
        UUID createdBy
) {

    public Arena {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(schematicName, "schematicName");
        Objects.requireNonNull(spawns, "spawns");
        Objects.requireNonNull(createdAt, "createdAt");
        // createdBy is intentionally nullable
        spawns = List.copyOf(spawns);
    }

    /**
     * Returns a copy of this arena with {@code spawn} appended to the spawn list.
     */
    public Arena addSpawn(SpawnPoint spawn) {
        Objects.requireNonNull(spawn, "spawn");
        List<SpawnPoint> updated = new ArrayList<>(spawns.size() + 1);
        updated.addAll(spawns);
        updated.add(spawn);
        return new Arena(name, schematicName, updated, createdAt, createdBy);
    }

    /**
     * Returns a copy of this arena with the spawn at {@code zeroBasedIndex}
     * removed.
     *
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public Arena removeSpawnAt(int zeroBasedIndex) {
        if (zeroBasedIndex < 0 || zeroBasedIndex >= spawns.size()) {
            throw new IndexOutOfBoundsException(
                    "Spawn index " + zeroBasedIndex + " out of range [0, " + spawns.size() + ").");
        }
        List<SpawnPoint> updated = new ArrayList<>(spawns.size() - 1);
        for (int i = 0; i < spawns.size(); i++) {
            if (i != zeroBasedIndex) {
                updated.add(spawns.get(i));
            }
        }
        return new Arena(name, schematicName, updated, createdAt, createdBy);
    }
}
