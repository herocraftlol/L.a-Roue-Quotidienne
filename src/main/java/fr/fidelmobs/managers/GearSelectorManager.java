package fr.fidelmobs.managers;

import fr.fidelmobs.Cles;
import fr.fidelmobs.LoyaltyMobsPlugin;
import fr.fidelmobs.arena.ArrowRegistry;
import fr.fidelmobs.arena.GearRegistry;
import fr.fidelmobs.data.PlayerDataManager;
import fr.fidelmobs.mobs.MobRarity;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Item donné dans l'arène (avant-avant-dernier slot de la hotbar) pour ouvrir un menu
 * regroupant toute l'armure/les armes et les flèches à effet obtenues à la roue, et les
 * équiper directement sans passer par /equipement en tapant à la main.
 */
public class GearSelectorManager {

    public static final int SLOT_EQUIPEMENT = 6; // avant-avant-dernier slot de la barre d'accès rapide (7e sur 9)

    private static final String CATEGORIE_GEAR = "GEAR";
    private static final String CATEGORIE_FLECHE = "FLECHE";

    private final LoyaltyMobsPlugin plugin;

    public GearSelectorManager(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
    }

    public ItemStack creerItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§d§l✦ Équipement & Armes");
        meta.setLore(List.of(
                "§7Clic droit pour choisir ton",
                "§7armure, ton arme et ta flèche",
                "§7à effet parmi ta collection."
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(Cles.EQUIPEMENT_SELECTEUR, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean estItemSelecteur(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(Cles.EQUIPEMENT_SELECTEUR, PersistentDataType.BYTE);
    }

    public void donnerItem(Player player) {
        player.getInventory().setItem(SLOT_EQUIPEMENT, creerItem());
    }

    public void retirerItem(Player player) {
        ItemStack actuel = player.getInventory().getItem(SLOT_EQUIPEMENT);
        if (estItemSelecteur(actuel)) {
            player.getInventory().setItem(SLOT_EQUIPEMENT, null);
        }
    }

    public void ouvrirMenu(Player player) {
        PlayerDataManager data = plugin.getPlayerDataManager();
        UUID uuid = player.getUniqueId();
        List<ItemStack> equipements = data.getEquipements(uuid);
        List<ItemStack> fleches = data.getFleches(uuid);

        if (equipements.isEmpty() && fleches.isEmpty()) {
            player.sendMessage("§7Tu n'as encore aucun équipement ni flèche. Utilise §f/roue §7pour en obtenir !");
            return;
        }

        int nbLignes = Math.min(6, Math.max(1, ((equipements.size() + fleches.size() - 1) / 9 + 1)));
        GearSelectorInventoryHolder holder = new GearSelectorInventoryHolder();
        Inventory inv = Bukkit.createInventory(holder, nbLignes * 9, "§d✦ Équipement & Armes");
        holder.setInventory(inv);

        // Armure/armes, triées par rareté décroissante puis par emplacement
        List<Integer> indicesGear = new ArrayList<>();
        for (int i = 0; i < equipements.size(); i++) indicesGear.add(i);
        indicesGear.sort(Comparator
                .comparing((Integer i) -> GearRegistry.getRarete(equipements.get(i)))
                .reversed());
        for (int i : indicesGear) {
            inv.addItem(creerIconeGear(data, uuid, equipements.get(i), i));
        }

        // Flèches à effet, triées par rareté décroissante
        List<Integer> indicesFleches = new ArrayList<>();
        for (int i = 0; i < fleches.size(); i++) indicesFleches.add(i);
        indicesFleches.sort(Comparator
                .comparing((Integer i) -> ArrowRegistry.getRarete(fleches.get(i)))
                .reversed());
        for (int i : indicesFleches) {
            inv.addItem(creerIconeFleche(data, uuid, fleches.get(i), i));
        }

        player.openInventory(inv);
    }

    /**
     * Traite un clic sur une icône du menu : équipe la pièce d'armure/arme ou la flèche
     * correspondante selon la catégorie stockée sur l'item cliqué.
     */
    public void choisir(Player player, String categorie, int index) {
        PlayerDataManager data = plugin.getPlayerDataManager();
        UUID uuid = player.getUniqueId();

        if (CATEGORIE_GEAR.equals(categorie)) {
            List<ItemStack> equipements = data.getEquipements(uuid);
            if (index < 0 || index >= equipements.size()) return;
            ItemStack item = equipements.get(index);
            GearRegistry.TypeEquipement type = GearRegistry.getType(item);
            if (type == null) return;
            data.setIndexEquipe(uuid, type.slot, index);
            data.save(uuid);
            String nom = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                    ? item.getItemMeta().getDisplayName() : item.getType().name();
            player.sendMessage("§aÉquipé : " + nom);
        } else if (CATEGORIE_FLECHE.equals(categorie)) {
            List<ItemStack> fleches = data.getFleches(uuid);
            if (index < 0 || index >= fleches.size()) return;
            ItemStack item = fleches.get(index);
            data.setIndexFlecheEquipee(uuid, index);
            data.save(uuid);
            String nom = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                    ? item.getItemMeta().getDisplayName() : item.getType().name();
            player.sendMessage("§aFlèche équipée : " + nom);
        } else {
            return;
        }

        if (plugin.getArenaProtectionListener().estDansArene(player)) {
            plugin.getKitManager().appliquerKit(player);
            player.updateInventory();
        }
    }

    private ItemStack creerIconeGear(PlayerDataManager data, UUID uuid, ItemStack original, int index) {
        ItemStack icone = original.clone();
        icone.setAmount(1);
        ItemMeta meta = icone.getItemMeta();
        GearRegistry.TypeEquipement type = GearRegistry.getType(original);
        MobRarity rarete = MobRarity.values()[GearRegistry.getRarete(original)];
        boolean equipe = type != null && data.getIndexEquipe(uuid, type.slot) == index;

        List<String> lore = new ArrayList<>();
        if (meta.hasLore()) lore.addAll(meta.getLore());
        lore.add("");
        lore.add("§7Rareté : " + rarete.getCouleur() + rarete.getLabel());
        String enchants = GearRegistry.formatEnchantements(original);
        if (enchants != null) {
            lore.add("§7Enchantements : §f" + enchants);
        }
        lore.add("");
        lore.add(equipe ? "§aÉquipé" : "§eClique pour équiper !");
        meta.setLore(lore);

        meta.getPersistentDataContainer().set(Cles.EQUIPEMENT_CHOIX_CATEGORIE, PersistentDataType.STRING, CATEGORIE_GEAR);
        meta.getPersistentDataContainer().set(Cles.EQUIPEMENT_CHOIX_INDEX, PersistentDataType.INTEGER, index);
        icone.setItemMeta(meta);
        return icone;
    }

    private ItemStack creerIconeFleche(PlayerDataManager data, UUID uuid, ItemStack original, int index) {
        ItemStack icone = original.clone();
        icone.setAmount(1);
        ItemMeta meta = icone.getItemMeta();
        MobRarity rarete = MobRarity.values()[ArrowRegistry.getRarete(original)];
        boolean equipee = data.getIndexFlecheEquipee(uuid) == index;

        List<String> lore = new ArrayList<>();
        lore.add("§7Rareté : " + rarete.getCouleur() + rarete.getLabel());
        String effet = ArrowRegistry.decrireEffet(original);
        if (effet != null) {
            lore.add("§7Effet : " + effet);
        }
        lore.add("");
        lore.add(equipee ? "§aFlèche actuellement équipée" : "§eClique pour équiper !");
        meta.setLore(lore);

        meta.getPersistentDataContainer().set(Cles.EQUIPEMENT_CHOIX_CATEGORIE, PersistentDataType.STRING, CATEGORIE_FLECHE);
        meta.getPersistentDataContainer().set(Cles.EQUIPEMENT_CHOIX_INDEX, PersistentDataType.INTEGER, index);
        icone.setItemMeta(meta);
        return icone;
    }
}
