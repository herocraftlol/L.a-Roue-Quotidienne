package fr.fidelmobs.commands;

import fr.fidelmobs.LoyaltyMobsPlugin;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ClassementCommand implements CommandExecutor {

    private final LoyaltyMobsPlugin plugin;

    public ClassementCommand(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Cette commande est réservée aux joueurs.");
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("retirer")) {
            plugin.getHologramManager().retirer();
            player.sendMessage("§7Hologramme de classement retiré.");
            return true;
        }

        Location emplacement = player.getLocation();
        plugin.getHologramManager().invoquer(emplacement);
        player.sendMessage("§aHologramme de classement invoqué (top kills, top morts, meilleurs K/D) !");
        return true;
    }
}
