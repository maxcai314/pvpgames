package ax.xz.max.pvpgames.kit;

/**
 * Successful outcome of {@link KitService#create}: the new kit and whether
 * the save replaced an existing kit with the same name.
 */
public record KitCreation(Kit kit, boolean replaced) {}
