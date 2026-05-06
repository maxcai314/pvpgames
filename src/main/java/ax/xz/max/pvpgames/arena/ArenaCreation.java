package ax.xz.max.pvpgames.arena;

/**
 * Successful outcome of {@link ArenaManager#create}: the new arena and
 * whether the save replaced an existing arena with the same name.
 */
public record ArenaCreation(Arena arena, boolean replaced) {}
