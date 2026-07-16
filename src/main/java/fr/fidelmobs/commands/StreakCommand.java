package fr.fidelmobs.commands;

import fr.fidelmobs.LoyaltyMobsPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StreakCommand implements CommandExecutor {

    private final LoyaltyMobsPlugin plugin;

    public StreakCommand(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Cette commande est réservée aux joueurs.");
            return true;
        }

        int streak = plugin.getPlayerDataManager().getStreak(player.getUniqueId());
        int tickets = plugin.getPlayerDataManager().getTickets(player.getUniqueId());

        player.sendMessage("§b=== Fidélité ===");
        player.sendMessage("§fSérie de connexions actuelle : §e" + streak + " jour(s)");
        player.sendMessage("§fTickets disponibles : §e" + tickets + " §7(utilisables avec /roue)");
        return true;
    }
}
