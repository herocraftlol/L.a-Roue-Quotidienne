package fr.fidelmobs.commands;

import fr.fidelmobs.LoyaltyMobsPlugin;
import fr.fidelmobs.mobs.MobRegistry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class InvoquerCommand implements CommandExecutor, TabCompleter {

    private final LoyaltyMobsPlugin plugin;

    public InvoquerCommand(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Cette commande est réservée aux joueurs.");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage("§cUsage : /invoquer <mob>");
            return true;
        }

        if (!plugin.getArenaManager().estDansArene(player.getLocation())) {
            player.sendMessage("§cTu ne peux invoquer un allié que dans l'arène PvP.");
            return true;
        }

        EntityType type = MobRegistry.parseNom(String.join("_", args));
        if (type == null) {
            player.sendMessage("§cMob inconnu : " + String.join(" ", args));
            return true;
        }

        plugin.getInvocationManager().invoquer(player, type);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) {
            return new ArrayList<>();
        }
        Map<EntityType, Integer> collection = plugin.getPlayerDataManager().getCollection(player.getUniqueId());
        String debut = args[0].toLowerCase();
        return collection.keySet().stream()
                .map(t -> t.name().toLowerCase())
                .filter(n -> n.startsWith(debut))
                .collect(Collectors.toList());
    }
}
