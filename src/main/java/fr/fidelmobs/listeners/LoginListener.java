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

        if (derniere != null && derniere.equals(aujourdHui)) {
            // déjà compté aujourd'hui, on ne redonne rien
            return;
        }

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
        player.sendMessage("§7Utilise §f/roue §7pour tenter d'obtenir un mob, et §f/armee §7pour voir ta collection.");
    }
}
