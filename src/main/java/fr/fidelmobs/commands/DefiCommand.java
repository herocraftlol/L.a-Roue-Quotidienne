package fr.fidelmobs.commands;

import fr.fidelmobs.LoyaltyMobsPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /defi : ouvre le menu des défis (achievements globaux et défis quotidiens), avec
 * progression, description au survol et récompenses en points de fidélité / tickets.
 */
public class DefiCommand implements CommandExecutor {

    private final LoyaltyMobsPlugin plugin;

    public DefiCommand(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande ne peut être utilisée que par un joueur.");
            return true;
        }

        boolean quotidien = args.length > 0 && (args[0].equalsIgnoreCase("quotidien") || args[0].equalsIgnoreCase("jour"));
        plugin.getDefiManager().ouvrirMenu(player, quotidien, 0);
        return true;
    }
}
