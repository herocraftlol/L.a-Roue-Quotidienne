package fr.fidelmobs.commands;

import fr.fidelmobs.LoyaltyMobsPlugin;
import fr.fidelmobs.data.PlayerDataManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class PointsCommand implements CommandExecutor {

    private final LoyaltyMobsPlugin plugin;

    public PointsCommand(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Cette commande est réservée aux joueurs.");
            return true;
        }

        PlayerDataManager data = plugin.getPlayerDataManager();
        UUID uuid = player.getUniqueId();
        int cout = Math.max(1, plugin.getConfig().getInt("arene.cout-ticket-points", 200));

        if (args.length >= 1 && args[0].equalsIgnoreCase("acheter")) {
            if (!data.retirerPoints(uuid, cout)) {
                player.sendMessage("§cIl te manque des points ! Tu as §d" + data.getPoints(uuid)
                        + " §cpoints, il en faut §d" + cout + "§c.");
                return true;
            }
            data.addTickets(uuid, 1);
            data.save(uuid);
            player.sendMessage("§aTu as échangé §d" + cout + " points §acontre §e1 ticket de roue §a! "
                    + "§7(Il te reste §d" + data.getPoints(uuid) + " §7points.)");
            return true;
        }

        int points = data.getPoints(uuid);
        player.sendMessage(" ");
        player.sendMessage("§d§l✦ Points de fidélité PvP");
        player.sendMessage("§7Solde actuel : §d§l" + points);
        player.sendMessage("§7Gagnés à chaque kill en arène.");
        player.sendMessage("§7Un ticket de roue coûte §d" + cout + " §7points — utilise §f/points acheter");
        player.sendMessage(" ");
        return true;
    }
}
