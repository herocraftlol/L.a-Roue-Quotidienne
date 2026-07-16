package fr.fidelmobs.managers;

import fr.fidelmobs.Cles;
import fr.fidelmobs.LoyaltyMobsPlugin;
import fr.fidelmobs.arena.GearRegistry;
import fr.fidelmobs.data.PlayerDataManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

/**
 * Applique et retire le kit PvP : armure de cuir + épée en bois par défaut,
 * remplacées par les pièces débloquées/équipées par le joueur. Tout le kit
 * porté en arène est incassable et verrouillé (ne peut pas être drop/déplacé).
 */
public class KitManager {

    private final LoyaltyMobsPlugin plugin;

    public KitManager(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
    }

    private ItemStack objetParDefaut(GearRegistry.TypeEquipement type) {
        Material material = GearRegistry.getMaterialParDefaut(type);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_UNBREAKABLE, org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack verrouiller(ItemStack item) {
        if (item == null) return item;
        ItemStack clone = item.clone();
        ItemMeta meta = clone.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            meta.getPersistentDataContainer().set(Cles.KIT_VERROUILLE, PersistentDataType.INTEGER, 1);
            clone.setItemMeta(meta);
        }
        return clone;
    }

    public void appliquerKit(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerDataManager data = plugin.getPlayerDataManager();
        EntityEquipment eq = player.getEquipment();
        if (eq == null) return;

        for (GearRegistry.TypeEquipement type : GearRegistry.TypeEquipement.values()) {
            int index = data.getIndexEquipe(uuid, type.slot);
            ItemStack objet;
            List<ItemStack> equipements = data.getEquipements(uuid);
            if (index >= 0 && index < equipements.size()) {
                objet = verrouiller(equipements.get(index));
            } else {
                objet = verrouiller(objetParDefaut(type));
            }

            switch (type.slot) {
                case HEAD -> eq.setHelmet(objet);
                case CHEST -> eq.setChestplate(objet);
                case LEGS -> eq.setLeggings(objet);
                case FEET -> eq.setBoots(objet);
                case HAND -> eq.setItemInMainHand(objet);
                default -> {
                }
            }
        }
    }

    public void retirerKit(Player player) {
        EntityEquipment eq = player.getEquipment();
        if (eq == null) return;
        // On ne retire que les objets marqués comme faisant partie du kit verrouillé
        if (estKit(eq.getHelmet())) eq.setHelmet(null);
        if (estKit(eq.getChestplate())) eq.setChestplate(null);
        if (estKit(eq.getLeggings())) eq.setLeggings(null);
        if (estKit(eq.getBoots())) eq.setBoots(null);
        if (estKit(eq.getItemInMainHand())) eq.setItemInMainHand(null);
    }

    public boolean estKit(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Integer v = item.getItemMeta().getPersistentDataContainer().get(Cles.KIT_VERROUILLE, PersistentDataType.INTEGER);
        return v != null && v == 1;
    }
}
