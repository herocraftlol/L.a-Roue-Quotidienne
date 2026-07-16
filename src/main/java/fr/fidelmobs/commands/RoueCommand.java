package fr.fidelmobs.commands;

import fr.fidelmobs.LoyaltyMobsPlugin;
import fr.fidelmobs.data.PlayerDataManager;
import fr.fidelmobs.mobs.MobRarity;
import fr.fidelmobs.mobs.MobRegistry;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.UUID;

public class RoueCommand implements CommandExecutor {

    private final LoyaltyMobsPlugin plugin;

    public RoueCommand(LoyaltyMobsPlugin plugin) {
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

        if (!data.consommerTicket(uuid)) {
            player.sendMessage("§cTu n'as pas de ticket disponible. Connecte-toi plusieurs jours d'affilée pour en gagner !");
            return true;
        }

        EntityType mob = MobRegistry.tirerMobAleatoire();
        MobRarity rarete = MobRegistry.getRarete(mob);
        data.ajouterMob(uuid, mob);
        data.save(uuid);

        player.sendMessage("§b=== Roue de la fidélité ===");
        player.sendMessage("§fTu as obtenu : " + rarete.getCouleur() + nomLisible(mob) + " §7(" + rarete.getCouleur() + rarete.getLabel() + "§7)");
        player.sendMessage("§7Utilise §f/armee §7pour voir toute ta collection.");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        return true;
    }

    private String nomLisible(EntityType type) {
        String s = type.name().toLowerCase().replace('_', ' ');
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
