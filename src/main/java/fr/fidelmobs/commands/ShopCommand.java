package fr.fidelmobs.commands;

import fr.fidelmobs.LoyaltyMobsPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /shop : ouvre la boutique (achat direct de mobs/blocs/pouvoirs/équipement contre des
 * points de fidélité), utilisable n'importe où pour parcourir tout ce qui existe dans le
 * plugin, même hors de l'arène.
 */
public class ShopCommand implements CommandExecutor {

    private final LoyaltyMobsPlugin plugin;

    public ShopCommand(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Cette commande est réservée aux joueurs.");
            return true;
        }
        plugin.getShopManager().ouvrirMenu(player);
        return true;
    }
}
