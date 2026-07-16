package fr.fidelmobs.commands;

import fr.fidelmobs.LoyaltyMobsPlugin;
import fr.fidelmobs.data.PlayerDataManager;
import fr.fidelmobs.listeners.AllyListener;
import fr.fidelmobs.mobs.MobRarity;
import fr.fidelmobs.mobs.MobRegistry;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class InvoquerCommand implements CommandExecutor, TabCompleter {

    private final LoyaltyMobsPlugin plugin;
    private final AllyListener allyListener;

    public InvoquerCommand(LoyaltyMobsPlugin plugin, AllyListener allyListener) {
        this.plugin = plugin;
        this.allyListener = allyListener;
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

        PlayerDataManager data = plugin.getPlayerDataManager();
        UUID uuid = player.getUniqueId();

        if (!data.retirerMob(uuid, type)) {
            player.sendMessage("§cTu ne possèdes aucun " + nomLisible(type) + " dans ta collection.");
            return true;
        }
        data.save(uuid);

        Location spawnLoc = player.getLocation().add(
                player.getLocation().getDirection().normalize().multiply(2)
        );
        Entity entite = player.getWorld().spawnEntity(spawnLoc, type);
        MobRarity rarete = MobRegistry.getRarete(type);
        entite.setCustomName(rarete.getCouleur() + player.getName() + "'s " + nomLisible(type));
        entite.setCustomNameVisible(true);

        if (entite instanceof Mob mob) {
            allyListener.enregistrerAllie(mob, player);
        }

        player.sendMessage("§aTu as invoqué " + rarete.getCouleur() + nomLisible(type) + " §aà tes côtés !");
        return true;
    }

    private String nomLisible(EntityType type) {
        String s = type.name().toLowerCase().replace('_', ' ');
        return s.substring(0, 1).toUpperCase() + s.substring(1);
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
