package fr.fidelmobs.listeners;

import fr.fidelmobs.LoyaltyMobsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Uniquement actif en mode partagé (multi-serveur.enabled: true + MySQL connecté). Précharge
 * les données du joueur AVANT qu'il n'arrive vraiment sur ce serveur (AsyncPlayerPreLoginEvent
 * tourne déjà hors du thread principal, donc l'appel JDBC bloquant ne gèle rien), et les
 * sauvegarde + les décharge du cache mémoire quand il quitte, pour que le prochain serveur sur
 * lequel il se connecte reparte d'une donnée à jour plutôt que d'un cache périmé.
 */
public class CrossServerSyncListener implements Listener {

    private final LoyaltyMobsPlugin plugin;

    public CrossServerSyncListener(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        // Cet évènement tourne déjà de façon asynchrone : on peut appeler directement le
        // chargement bloquant (JDBC) sans passer par le scheduler.
        plugin.getPlayerDataManager().chargerDepuisBase(event.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player joueur = event.getPlayer();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getPlayerDataManager().sauvegarderAsync(joueur.getUniqueId());
            plugin.getPlayerDataManager().decharger(joueur.getUniqueId());
        });
    }
}
