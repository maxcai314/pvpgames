package ax.xz.max.pvpgames.arena.internal;

import ax.xz.max.pvpgames.schematic.BlockVec3;
import org.bukkit.World;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;

/**
 * Hands out grid origins for live arena sessions inside the shared arenas
 * world.
 *
 * <p>Slots are arranged in a single line along the positive X axis, spaced
 * {@link #STRIDE} blocks apart at a fixed Y. Released slots are reused before
 * fresh ones are minted, so a server doing many open / close cycles stays
 * packed near the origin. Allocations are NOT persisted: the shared world is
 * wiped on every plugin disable, so the next enable starts from a clean
 * slate.
 *
 * <p>Each slot owns a fixed-size cuboid bounded by {@link #HALF_EXTENT} on the
 * horizontal axes and the world's full vertical range; the WorldGuard region
 * created for the session uses these bounds. The {@link #STRIDE} of 10,000
 * blocks leaves an enormous buffer between any two arenas, so the fixed
 * extent is safe even for very large schematics.
 */
public final class ArenaAllocator {

    /** Distance between adjacent slot origins along the X axis, in blocks. */
    public static final int STRIDE = 10_000;

    /** Y coordinate every slot's origin sits at. */
    public static final int BASE_Y = 64;

    /** Horizontal radius of each slot's cuboid region, in blocks. */
    public static final int HALF_EXTENT = 256;

    /** Soft upper bound on simultaneous sessions; sized for headroom. */
    public static final int MAX_SLOTS = 1024;

    private final World world;
    private final BitSet allocated = new BitSet();
    private final Deque<Integer> freed = new ArrayDeque<>();

    public ArenaAllocator(World world) {
        this.world = Objects.requireNonNull(world, "world");
    }

    /**
     * Allocates the lowest-index free slot. Returns {@link Optional#empty}
     * when {@link #MAX_SLOTS} are already in use.
     */
    public synchronized Optional<Allocation> allocate() {
        Integer slot = freed.pollFirst();
        if (slot == null) {
            int candidate = allocated.nextClearBit(0);
            if (candidate >= MAX_SLOTS) {
                return Optional.empty();
            }
            slot = candidate;
        }
        allocated.set(slot);
        return Optional.of(buildAllocation(slot));
    }

    /** Releases a previously allocated slot so it can be re-used. */
    public synchronized void release(int slotIndex) {
        if (!allocated.get(slotIndex)) {
            return;
        }
        allocated.clear(slotIndex);
        freed.addFirst(slotIndex);
    }

    /**
     * Computes the geometry for a slot. Origin sits on the +X grid line at
     * {@link #BASE_Y}; the cuboid extends {@link #HALF_EXTENT} blocks
     * horizontally and spans the world's full vertical range.
     */
    private Allocation buildAllocation(int slotIndex) {
        BlockVec3 origin = new BlockVec3(slotIndex * STRIDE, BASE_Y, 0);
        BlockVec3 min = new BlockVec3(origin.x() - HALF_EXTENT, world.getMinHeight(), origin.z() - HALF_EXTENT);
        BlockVec3 max = new BlockVec3(origin.x() + HALF_EXTENT, world.getMaxHeight() - 1, origin.z() + HALF_EXTENT);
        return new Allocation(slotIndex, origin, min, max);
    }

    /**
     * One assignment of a slot to a session: the slot index that must be
     * returned to {@link #release}, the origin to paste the schematic at,
     * and the inclusive cuboid corners that bound the WorldGuard region.
     */
    public record Allocation(int slotIndex, BlockVec3 origin, BlockVec3 min, BlockVec3 max) {}
}
