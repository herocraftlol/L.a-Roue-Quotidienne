package fr.fidelmobs.listeners;

import fr.fidelmobs.LoyaltyMobsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Resynchronise l'armée du joueur vers MySQL dès sa déconnexion (en plus du cycle
 * périodique de {@link fr.fidelmobs.database.ArmySyncTask}), pour que le site web dispose
 * de données à jour même s'il consulte l'armée juste après que le joueur ait quitté le jeu.
 * Ce listener n'est enregistré que si l'arène de stratégie web (MySQL) est active.
 */
public class ArmySyncListener implements Listener {

    private final LoyaltyMobsPlugin plugin;

    public ArmySyncListener(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
                plugin.getArmySyncTask().synchroniser(player.getUniqueId(), player.getName()));
    }
}
