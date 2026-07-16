package fr.fidelmobs.commands;

import fr.fidelmobs.LoyaltyMobsPlugin;
import fr.fidelmobs.arena.BlockRegistry;
import fr.fidelmobs.arena.GearRegistry;
import fr.fidelmobs.data.PlayerDataManager;
import fr.fidelmobs.mobs.MobRarity;
import fr.fidelmobs.mobs.MobRegistry;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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

        player.sendMessage("§b=== Roue de la fidélité ===");

        // Un lancer de roue donne désormais une récompense de chaque catégorie
        tirerMob(player, data, uuid);
        tirerBloc(player, data, uuid);
        tirerEquipement(player, data, uuid);

        data.save(uuid);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        return true;
    }

    private void tirerMob(Player player, PlayerDataManager data, UUID uuid) {
        EntityType mob = MobRegistry.tirerMobAleatoire();
        MobRarity rarete = MobRegistry.getRarete(mob);
        data.ajouterMob(uuid, mob);

        player.sendMessage("§fTu as obtenu un allié : " + rarete.getCouleur() + nomLisible(mob.name())
                + " §7(" + rarete.getCouleur() + rarete.getLabel() + "§7)");
        player.sendMessage("§7Utilise §f/armee §7pour voir toute ta collection.");
    }

    private void tirerBloc(Player player, PlayerDataManager data, UUID uuid) {
        Material bloc = BlockRegistry.tirerBlocAleatoire();
        MobRarity rarete = BlockRegistry.getRarete(bloc);
        boolean nouveau = !data.getBlocsDebloques(uuid).contains(bloc);
        data.debloquerBloc(uuid, bloc);

        player.sendMessage("§fTu as obtenu un bloc de construction : " + rarete.getCouleur() + nomLisible(bloc.name())
                + " §7(" + rarete.getCouleur() + rarete.getLabel() + "§7)");
        if (nouveau) {
            player.sendMessage("§aNouveau bloc débloqué pour l'arène ! Utilise §f/bloc choisir " + bloc.name() + " §apour l'activer.");
        } else {
            player.sendMessage("§7Tu possédais déjà ce bloc.");
        }
    }

    private void tirerEquipement(Player player, PlayerDataManager data, UUID uuid) {
        ItemStack item = GearRegistry.genererObjetAleatoire();
        GearRegistry.TypeEquipement type = GearRegistry.getType(item);
        int rareteIndex = GearRegistry.getRarete(item);
        MobRarity rarete = MobRarity.values()[rareteIndex];

        int index = data.ajouterEquipement(uuid, item);

        String nom = item.getItemMeta().hasDisplayName() ? item.getItemMeta().getDisplayName() : item.getType().name();
        player.sendMessage("§fTu as obtenu un équipement : " + nom + " §7(" + rarete.getCouleur() + rarete.getLabel() + "§7)");

        if (type != null) {
            int indexActuel = data.getIndexEquipe(uuid, type.slot);
            int rareteActuelle = indexActuel >= 0 && indexActuel < data.getEquipements(uuid).size()
                    ? GearRegistry.getRarete(data.getEquipements(uuid).get(indexActuel)) : -1;

            if (indexActuel < 0 || rareteIndex >= rareteActuelle) {
                data.setIndexEquipe(uuid, type.slot, index);
                player.sendMessage("§aÉquipé automatiquement !");
            } else {
                player.sendMessage("§7Utilise §f/equipement liste §7et §f/equipement equiper §7pour le porter à la place de ton équipement actuel.");
            }
        }
    }

    private String nomLisible(String nomBrut) {
        String s = nomBrut.toLowerCase().replace('_', ' ');
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
