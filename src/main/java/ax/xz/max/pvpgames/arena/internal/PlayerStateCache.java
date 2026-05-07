package ax.xz.max.pvpgames.arena.internal;

import ax.xz.max.pvpgames.player.PlayerStateSnapshot;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Manages per-player snapshots of pre-session state so a player who joins any
 * arena session can later be teleported back to exactly where they came from.
 *
 * <p>Constructed once by the arena manager and shared across every session it
 * creates: a player's snapshot is captured the first time they enter ANY
 * session and kept until they explicitly leave or the manager shuts down.
 * Re-entering a session while still cached is a no-op for capture; a single
 * snapshot covers the whole "in-arena" period, even across multiple sessions.
 */
public final class PlayerStateCache {

    private final ConcurrentMap<UUID, PlayerStateSnapshot> cache = new ConcurrentHashMap<>();
    private final Server server;
    private final Plugin plugin;

    public PlayerStateCache(Server server, Plugin plugin) {
        this.server = Objects.requireNonNull(server, "server");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /**
     * Captures {@code player}'s current state if no snapshot is already
     * cached for them. Idempotent.
     */
    public void captureIfAbsent(Player player) {
        Objects.requireNonNull(player, "player");
        cache.computeIfAbsent(player.getUniqueId(),
                ignored -> PlayerStateSnapshot.captureFrom(player));
    }

    /**
     * Pops {@code player}'s snapshot and applies it. Returns {@code true} if
     * a snapshot was present (and the player was restored), {@code false} if
     * there was nothing to do.
     */
    public boolean restore(Player player) {
        Objects.requireNonNull(player, "player");
        PlayerStateSnapshot saved = cache.remove(player.getUniqueId());
        if (saved == null) {
            return false;
        }
        saved.applyTo(player);
        return true;
    }

    /** Returns a snapshot of UUIDs that currently have a cached state. */
    public Set<UUID> cachedPlayers() {
        return Set.copyOf(cache.keySet());
    }

    /** Drops {@code playerId}'s entry without applying it. */
    public void forget(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        cache.remove(playerId);
    }
}
