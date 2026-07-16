package fr.fidelmobs.commands;

import fr.fidelmobs.LoyaltyMobsPlugin;
import fr.fidelmobs.arena.ArenaManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ArenePvpCommand implements CommandExecutor {

    private final LoyaltyMobsPlugin plugin;

    public ArenePvpCommand(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("loyaltymobs.admin")) {
            sender.sendMessage("§cTu n'as pas la permission d'utiliser cette commande.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Cette commande est réservée aux joueurs.");
            return true;
        }

        ArenaManager arene = plugin.getArenaManager();

        if (args.length < 1) {
            player.sendMessage("§cUsage : /arenepvp <pos1|pos2|info>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "pos1" -> {
                if (arene.definirPos1DepuisRegard(player)) {
                    player.sendMessage("§aCoin 1 de l'arène défini sur le bloc regardé.");
                } else {
                    player.sendMessage("§cAucun bloc regardé dans la portée (100 blocs).");
                }
            }
            case "pos2" -> {
                if (arene.definirPos2DepuisRegard(player)) {
                    player.sendMessage("§aCoin 2 de l'arène défini sur le bloc regardé.");
                    if (arene.estConfiguree()) {
                        player.sendMessage("§aArène complète : " + arene.describeCoins());
                    }
                } else {
                    player.sendMessage("§cAucun bloc regardé dans la portée (100 blocs).");
                }
            }
            case "info" -> player.sendMessage("§b" + arene.describeCoins());
            default -> player.sendMessage("§cUsage : /arenepvp <pos1|pos2|info>");
        }

        return true;
    }
}
