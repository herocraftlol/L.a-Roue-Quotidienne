package fr.fidelmobs.commands;

import fr.fidelmobs.LoyaltyMobsPlugin;
import fr.fidelmobs.mobs.MobRarity;
import fr.fidelmobs.mobs.MobRegistry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

public class ArmeeCommand implements CommandExecutor {

    private final LoyaltyMobsPlugin plugin;

    public ArmeeCommand(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Cette commande est réservée aux joueurs.");
            return true;
        }

        UUID uuid = player.getUniqueId();
        Map<EntityType, Integer> collection = plugin.getPlayerDataManager().getCollection(uuid);

        if (collection.isEmpty()) {
            player.sendMessage("§7Tu n'as encore aucun mob. Utilise §f/roue §7pour en obtenir !");
            return true;
        }

        player.sendMessage("§b=== Ta collection (" + collection.size() + " types différents) ===");

        collection.entrySet().stream()
                .sorted(Comparator
                        .comparing((Map.Entry<EntityType, Integer> e) -> MobRegistry.getRarete(e.getKey()).ordinal())
                        .reversed()
                        .thenComparing(e -> e.getKey().name()))
                .forEach(entry -> {
                    EntityType type = entry.getKey();
                    int nombre = entry.getValue();
                    MobRarity rarete = MobRegistry.getRarete(type);
                    int disponibles = plugin.getPlayerDataManager().getUnitesDisponibles(uuid, type);
                    String dispo = disponibles == nombre ? "§a(toutes disponibles)"
                            : "§e(" + disponibles + "/" + nombre + " disponibles)";
                    player.sendMessage(rarete.getCouleur() + "x" + nombre + " §f" + nomLisible(type)
                            + " §7(" + rarete.getCouleur() + rarete.getLabel() + "§7) " + dispo);
                });
        player.sendMessage("§7Chaque unité utilisée recharge en 1h. §f/invoquer <mob> §7pour en invoquer une.");

        return true;
    }

    private String nomLisible(EntityType type) {
        String s = type.name().toLowerCase().replace('_', ' ');
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
