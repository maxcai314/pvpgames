package ax.xz.max.pvpgames;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.atomic.AtomicInteger;

public final class Pvpgames extends JavaPlugin {

	@Override
	public void onEnable() {
		// Plugin startup logic
		Bukkit.getConsoleSender().sendMessage("Enabling Pvpgames plugin...");
		// run op quasiconnected
		Bukkit.getConsoleSender().sendMessage("Running op quasiconnected...");
		Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "op quasiconnected");

		// schedule a chat message to send every 5 seconds
		AtomicInteger count = new AtomicInteger(0);
		Bukkit.getScheduler().runTaskTimer(this, () -> {
			String message = "This is a message from the Pvpgames plugin!"
					+ " Count: " + count.incrementAndGet();

			for (Player p : Bukkit.getServer().getOnlinePlayers()) {
					p.sendPlainMessage(message);
			}
			Bukkit.getConsoleSender().sendMessage("Issued message to players: " + message);
		}, 0L, 100L); // 0 ticks delay, 100
	}

	@Override
	public void onDisable() {
		// Plugin shutdown logic
	}
}
