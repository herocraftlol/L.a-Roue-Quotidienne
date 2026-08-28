package fr.fidelmobs.commands;

import fr.fidelmobs.LoyaltyMobsPlugin;
import fr.fidelmobs.sacrifice.SacrificeManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /sacrifier <mobs|equipements|pouvoirs|blocs|tout> [confirmer] : convertit une catégorie
 * (ou toute) sa collection en points de fidélité, en la remettant à zéro. Irréversible,
 * demande donc une confirmation explicite dans les 30 secondes.
 */
public class SacrifierCommand implements CommandExecutor {

    private final LoyaltyMobsPlugin plugin;

    public SacrifierCommand(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Cette commande est réservée aux joueurs.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§7Usage : §f/sacrifier <mobs|equipements|pouvoirs|blocs|tout>");
            player.sendMessage("§7Convertit la catégorie choisie (ou tout) en points de fidélité, en la remettant à zéro.");
            return true;
        }

        String categorie = args[0].toLowerCase();
        if (!SacrificeManager.CATEGORIES.contains(categorie)) {
            player.sendMessage("§cCatégorie invalide. Choix possibles : §f" + String.join(", ", SacrificeManager.CATEGORIES));
            return true;
        }

        if (args.length >= 2 && args[1].equalsIgnoreCase("confirmer")) {
            plugin.getSacrificeManager().confirmer(player, categorie);
        } else {
            plugin.getSacrificeManager().demander(player, categorie);
        }
        return true;
    }
}
