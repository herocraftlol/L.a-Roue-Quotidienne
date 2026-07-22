package fr.fidelmobs.commands;

import fr.fidelmobs.LoyaltyMobsPlugin;
import fr.fidelmobs.arena.ArrowRegistry;
import fr.fidelmobs.arena.GearRegistry;
import fr.fidelmobs.data.PlayerDataManager;
import fr.fidelmobs.mobs.MobRarity;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

public class EquipementCommand implements CommandExecutor {

    private final LoyaltyMobsPlugin plugin;

    public EquipementCommand(LoyaltyMobsPlugin plugin) {
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

        if (args.length > 0 && args[0].equalsIgnoreCase("fleches")) {
            return gererFleches(player, data, uuid, args);
        }

        List<ItemStack> equipements = data.getEquipements(uuid);

        if (args.length == 0 || args[0].equalsIgnoreCase("liste")) {
            if (equipements.isEmpty()) {
                player.sendMessage("§7Tu n'as encore aucun équipement. Utilise §f/roue §7pour en obtenir !");
            } else {
                player.sendMessage("§b=== Ton équipement (" + equipements.size() + ") ===");
                for (int i = 0; i < equipements.size(); i++) {
                    ItemStack item = equipements.get(i);
                    GearRegistry.TypeEquipement type = GearRegistry.getType(item);
                    boolean equipe = type != null && data.getIndexEquipe(uuid, type.slot) == i;
                    String nom = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                            ? item.getItemMeta().getDisplayName() : item.getType().name();
                    player.sendMessage("§7#" + i + " " + nom + (equipe ? " §a(équipé)" : ""));
                    String enchants = GearRegistry.formatEnchantements(item);
                    if (enchants != null) {
                        player.sendMessage("     §8✦ §7" + enchants);
                    }
                }
                player.sendMessage("§7Utilise §f/equipement equiper <numéro> §7pour équiper une pièce.");
            }
            player.sendMessage("§7Utilise §f/equipement fleches §7pour gérer tes flèches à effet.");
            player.sendMessage("§7Astuce : en arène, ouvre le menu d'équipement (avant-avant-dernier slot) pour tout gérer visuellement.");
            return true;
        }

        if (args[0].equalsIgnoreCase("equiper")) {
            if (args.length < 2) {
                player.sendMessage("§cUsage : /equipement equiper <numéro>");
                return true;
            }
            int index;
            try {
                index = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage("§cNuméro invalide.");
                return true;
            }
            if (index < 0 || index >= equipements.size()) {
                player.sendMessage("§cCe numéro ne correspond à aucun objet de ta collection.");
                return true;
            }
            ItemStack item = equipements.get(index);
            GearRegistry.TypeEquipement type = GearRegistry.getType(item);
            if (type == null) {
                player.sendMessage("§cObjet invalide.");
                return true;
            }
            data.setIndexEquipe(uuid, type.slot, index);
            data.save(uuid);
            player.sendMessage("§aÉquipement mis à jour.");

            if (plugin.getArenaProtectionListener().estDansArene(player)) {
                plugin.getKitManager().appliquerKit(player);
            }
            return true;
        }

        player.sendMessage("§cUsage : /equipement <liste|equiper|fleches> [numéro]");
        return true;
    }

    private boolean gererFleches(Player player, PlayerDataManager data, UUID uuid, String[] args) {
        List<ItemStack> fleches = data.getFleches(uuid);

        if (args.length < 2 || args[1].equalsIgnoreCase("liste")) {
            if (fleches.isEmpty()) {
                player.sendMessage("§7Tu n'as encore aucune flèche à effet. Utilise §f/roue §7pour en obtenir !");
                return true;
            }
            int equipee = data.getIndexFlecheEquipee(uuid);
            player.sendMessage("§b=== Tes flèches à effet (" + fleches.size() + ") ===");
            for (int i = 0; i < fleches.size(); i++) {
                ItemStack item = fleches.get(i);
                MobRarity rarete = MobRarity.values()[ArrowRegistry.getRarete(item)];
                String nom = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                        ? item.getItemMeta().getDisplayName() : item.getType().name();
                player.sendMessage("§7#" + i + " " + nom + (i == equipee ? " §a(équipée)" : ""));
                String effet = ArrowRegistry.decrireEffet(item);
                if (effet != null) {
                    player.sendMessage("     §8✦ " + effet);
                }
            }
            player.sendMessage("§7Utilise §f/equipement fleches equiper <numéro> §7pour changer de flèche.");
            return true;
        }

        if (args[1].equalsIgnoreCase("equiper")) {
            if (args.length < 3) {
                player.sendMessage("§cUsage : /equipement fleches equiper <numéro>");
                return true;
            }
            int index;
            try {
                index = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                player.sendMessage("§cNuméro invalide.");
                return true;
            }
            if (index < 0 || index >= fleches.size()) {
                player.sendMessage("§cCe numéro ne correspond à aucune flèche de ta collection.");
                return true;
            }
            data.setIndexFlecheEquipee(uuid, index);
            data.save(uuid);
            player.sendMessage("§aFlèche équipée mise à jour.");

            if (plugin.getArenaProtectionListener().estDansArene(player)) {
                plugin.getKitManager().appliquerKit(player);
            }
            return true;
        }

        player.sendMessage("§cUsage : /equipement fleches <liste|equiper> [numéro]");
        return true;
    }
}

