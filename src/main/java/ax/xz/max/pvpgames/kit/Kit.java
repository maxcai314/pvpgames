package ax.xz.max.pvpgames.kit;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A persisted inventory loadout, identified by {@link KitName}.
 *
 * <p>{@code createdBy} is the {@link UUID} of the player whose inventory
 * produced the kit; it may be {@code null} if a kit was crafted by the console
 * or another non-player source. {@code createdAt} is captured at save time so
 * the list command can sort or display ages later without rereading file
 * timestamps.
 */
public record Kit(KitName name, InventorySnapshot contents, Instant createdAt, UUID createdBy) {

    public Kit {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(contents, "contents");
        Objects.requireNonNull(createdAt, "createdAt");
        // createdBy is intentionally nullable
    }
}
