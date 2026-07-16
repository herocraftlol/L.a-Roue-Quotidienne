package fr.fidelmobs.listeners;

import fr.fidelmobs.LoyaltyMobsPlugin;
import fr.fidelmobs.data.PlayerDataManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.LocalDate;
import java.util.UUID;

public class LoginListener implements Listener {

    private final LoyaltyMobsPlugin plugin;

    public LoginListener(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        PlayerDataManager data = plugin.getPlayerDataManager();

        LocalDate aujourdHui = LocalDate.now();
        LocalDate derniere = data.getLastLogin(uuid);
        boolean dejaCompteAujourdHui = derniere != null && derniere.equals(aujourdHui);

        if (!dejaCompteAujourdHui) {
            int streak = data.getStreak(uuid);
            if (derniere != null && derniere.plusDays(1).equals(aujourdHui)) {
                streak += 1;
            } else {
                streak = 1;
            }

            data.setStreak(uuid, streak);
            data.setLastLogin(uuid, aujourdHui);

            int ticketsDuJour = plugin.getConfig().getInt("tickets-par-jour", 1);
            data.addTickets(uuid, ticketsDuJour);

            StringBuilder message = new StringBuilder();
            message.append("§b[Fidélité] §fSérie de connexions : §e").append(streak).append(" jour(s)")
                    .append(" §7(+").append(ticketsDuJour).append(" ticket(s))");

            ConfigurationSection paliers = plugin.getConfig().getConfigurationSection("paliers-serie");
            if (paliers != null && paliers.contains(String.valueOf(streak))) {
                int bonus = paliers.getInt(String.valueOf(streak));
                data.addTickets(uuid, bonus);
                message.append("\n§6[Palier atteint !] §fBonus de §e").append(bonus).append(" ticket(s) §fpour ")
                        .append(streak).append(" jours consécutifs !");
            }

            data.save(uuid);
            player.sendMessage(message.toString());
        }

        // Rappel affiché à CHAQUE connexion tant que le joueur a des tickets non utilisés,
        // pas seulement le jour où ils ont été gagnés.
        int tickets = data.getTickets(uuid);
        if (tickets > 0) {
            player.sendMessage("§7Tu as §e" + tickets + " ticket(s) de roue en attente §7! Utilise §f/roue "
                    + "§7pour les lancer et obtenir un mob, un bloc et un équipement.");
        }
    }
}
