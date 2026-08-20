package fr.fidelmobs.managers;

import fr.fidelmobs.Cles;
import fr.fidelmobs.LoyaltyMobsPlugin;
import fr.fidelmobs.arena.ArrowRegistry;
import fr.fidelmobs.arena.GearRegistry;
import fr.fidelmobs.data.PlayerDataManager;
import fr.fidelmobs.mobs.MobRarity;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Item donné dans l'arène (avant-avant-dernier slot de la hotbar) pour ouvrir un menu
 * regroupant toute l'armure/les armes et les flèches à effet obtenues à la roue, et les
 * équiper directement. Une page dédiée par emplacement (Arme, Casque, Plastron, Jambières,
 * Bottes, Flèches) : chacune commence par l'objet de base (bois/cuir, sans enchantement),
 * toujours disponible sans condition, suivi de toute la panoplie débloquée pour cet
 * emplacement — brute et enchantée, classée par puissance décroissante.
 */
public class GearSelectorManager {

    public static final int SLOT_EQUIPEMENT = 6; // avant-avant-dernier slot de la barre d'accès rapide (7e sur 9)

    private static final String CATEGORIE_GEAR = "GEAR";
    private static final String CATEGORIE_FLECHE = "FLECHE";
    private static final int SLOTS_PAR_PAGE = 45; // dernière ligne (9 slots) réservée à la navigation/aux onglets

    // 0-4 : les 5 emplacements d'équipement (même ordre que GearRegistry.TypeEquipement), 5 : flèches
    private static final int ONGLET_FLECHES = GearRegistry.TypeEquipement.values().length;
    private static final Material[] ICONES_ONGLET = {
            Material.IRON_SWORD, Material.IRON_HELMET, Material.IRON_CHESTPLATE,
            Material.IRON_LEGGINGS, Material.IRON_BOOTS, Material.TIPPED_ARROW
    };
    private static final String[] NOMS_ONGLET = {
            "Arme", "Casque", "Plastron", "Jambières", "Bottes", "Flèches"
    };

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
        ouvrirMenu(player, 0, 0);
    }

    public void ouvrirMenu(Player player, int onglet, int page) {
        PlayerDataManager data = plugin.getPlayerDataManager();
        UUID uuid = player.getUniqueId();

        int ongletBorne = Math.max(0, Math.min(onglet, ONGLET_FLECHES));
        List<ItemStack> icones = ongletBorne == ONGLET_FLECHES
                ? construireIconesFleches(data, uuid)
                : construireIconesGear(data, uuid, GearRegistry.TypeEquipement.values()[ongletBorne]);

        int nbPages = Math.max(1, (icones.size() - 1) / SLOTS_PAR_PAGE + 1);
        int pageBornee = Math.max(0, Math.min(page, nbPages - 1));

        GearSelectorInventoryHolder holder = new GearSelectorInventoryHolder();
        holder.setOnglet(ongletBorne);
        holder.setPage(pageBornee);
        String titre = "§d✦ " + NOMS_ONGLET[ongletBorne] + " (" + (pageBornee + 1) + "/" + nbPages + ")";
        Inventory inv = Bukkit.createInventory(holder, 54, titre);
        holder.setInventory(inv);

        int debut = pageBornee * SLOTS_PAR_PAGE;
        int fin = Math.min(icones.size(), debut + SLOTS_PAR_PAGE);
        for (int i = debut; i < fin; i++) {
            inv.setItem(i - debut, icones.get(i));
        }

        if (pageBornee > 0) {
            inv.setItem(45, creerIconeNavPage("prev", "§e« Page précédente"));
        }
        if (pageBornee < nbPages - 1) {
            inv.setItem(53, creerIconeNavPage("next", "§ePage suivante »"));
        }
        for (int i = 0; i < NOMS_ONGLET.length; i++) {
            inv.setItem(46 + i, creerIconeOnglet(i, i == ongletBorne));
        }

        player.openInventory(inv);
    }

    private List<ItemStack> construireIconesGear(PlayerDataManager data, UUID uuid, GearRegistry.TypeEquipement type) {
        List<ItemStack> equipements = data.getEquipements(uuid);

        // Un même matériau+niveau d'enchantement ne doit jamais apparaître plusieurs fois
        // (regroupement par signature), même si d'anciennes données en contiennent plusieurs
        // exemplaires identiques (avant l'anti-doublon fin de la roue).
        Map<String, List<Integer>> groupes = new LinkedHashMap<>();
        for (int i = 0; i < equipements.size(); i++) {
            ItemStack item = equipements.get(i);
            if (GearRegistry.getType(item) != type) continue;
            groupes.computeIfAbsent(GearRegistry.getSignature(item), k -> new ArrayList<>()).add(i);
        }

        List<String> signaturesTriees = new ArrayList<>(groupes.keySet());
        signaturesTriees.sort(Comparator
                .comparing((String s) -> GearRegistry.getRarete(equipements.get(groupes.get(s).get(0))))
                .reversed());

        List<ItemStack> icones = new ArrayList<>();
        icones.add(creerIconeGearParDefaut(data, uuid, type));
        for (String signature : signaturesTriees) {
            List<Integer> indices = groupes.get(signature);
            icones.add(creerIconeGear(data, uuid, equipements.get(indices.get(0)), indices));
        }
        return icones;
    }

    private List<ItemStack> construireIconesFleches(PlayerDataManager data, UUID uuid) {
        List<ItemStack> fleches = data.getFleches(uuid);
        Map<Integer, List<Integer>> groupes = new LinkedHashMap<>();
        for (int i = 0; i < fleches.size(); i++) {
            groupes.computeIfAbsent(ArrowRegistry.getModeleId(fleches.get(i)), k -> new ArrayList<>()).add(i);
        }
        List<Integer> modelesTries = new ArrayList<>(groupes.keySet());
        modelesTries.sort(Comparator.<Integer>naturalOrder().reversed());

        List<ItemStack> icones = new ArrayList<>();
        for (int modele : modelesTries) {
            List<Integer> indices = groupes.get(modele);
            icones.add(creerIconeFleche(data, uuid, fleches.get(indices.get(0)), indices));
        }
        return icones;
    }

    private ItemStack creerIconeNavPage(String action, String nom) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(nom);
        meta.getPersistentDataContainer().set(Cles.EQUIPEMENT_PAGE_ACTION, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack creerIconeOnglet(int onglet, boolean actif) {
        ItemStack item = new ItemStack(ICONES_ONGLET[onglet]);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName((actif ? "§a▶ " : "§7") + NOMS_ONGLET[onglet]);
        if (actif) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        meta.getPersistentDataContainer().set(Cles.EQUIPEMENT_ONGLET_ACTION, PersistentDataType.INTEGER, onglet);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Traite un clic sur une icône du menu : équipe la pièce d'armure/arme ou la flèche
     * correspondante selon la catégorie stockée sur l'item cliqué. index == -1 signifie
     * "revenir à l'objet de base" (bois/cuir sans enchantement), toujours disponible.
     */
    public void choisir(Player player, String categorie, int index) {
        PlayerDataManager data = plugin.getPlayerDataManager();
        UUID uuid = player.getUniqueId();

        if (CATEGORIE_GEAR.equals(categorie)) {
            if (index == -1) {
                // Géré depuis l'appelant (a besoin du type, transmis autrement) : voir
                // choisirGearParDefaut ci-dessous, appelée directement par le listener.
                return;
            }
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

    /** Revient à l'objet de base (bois/cuir sans enchantement) pour cet emplacement. */
    public void choisirGearParDefaut(Player player, GearRegistry.TypeEquipement type) {
        PlayerDataManager data = plugin.getPlayerDataManager();
        UUID uuid = player.getUniqueId();
        data.setIndexEquipe(uuid, type.slot, -1);
        data.save(uuid);
        player.sendMessage("§aÉquipé : §f" + GearRegistry.objetParDefaut(type).getItemMeta().getDisplayName());

        if (plugin.getArenaProtectionListener().estDansArene(player)) {
            plugin.getKitManager().appliquerKit(player);
            player.updateInventory();
        }
    }

    private ItemStack creerIconeGearParDefaut(PlayerDataManager data, UUID uuid, GearRegistry.TypeEquipement type) {
        ItemStack icone = GearRegistry.objetParDefaut(type);
        ItemMeta meta = icone.getItemMeta();
        boolean equipe = data.getIndexEquipe(uuid, type.slot) < 0;

        List<String> lore = new ArrayList<>();
        lore.add("§7Objet de base : §atoujours disponible");
        lore.add("§7sans le moindre enchantement.");
        lore.add("");
        lore.add(equipe ? "§aÉquipé" : "§eClique pour équiper !");
        meta.setLore(lore);

        meta.getPersistentDataContainer().set(Cles.EQUIPEMENT_CHOIX_CATEGORIE, PersistentDataType.STRING, CATEGORIE_GEAR);
        meta.getPersistentDataContainer().set(Cles.EQUIPEMENT_CHOIX_INDEX, PersistentDataType.INTEGER, -1);
        icone.setItemMeta(meta);
        return icone;
    }

    private ItemStack creerIconeGear(PlayerDataManager data, UUID uuid, ItemStack original, List<Integer> indices) {
        ItemStack icone = original.clone();
        icone.setAmount(1);
        ItemMeta meta = icone.getItemMeta();
        GearRegistry.TypeEquipement type = GearRegistry.getType(original);
        MobRarity rarete = MobRarity.values()[GearRegistry.getRarete(original)];
        int indexActuel = type != null ? data.getIndexEquipe(uuid, type.slot) : -1;
        boolean equipe = indices.contains(indexActuel);

        List<String> lore = new ArrayList<>();
        if (meta.hasLore()) lore.addAll(meta.getLore());
        lore.add("");
        lore.add("§7Rareté : " + rarete.getCouleur() + rarete.getLabel());
        String enchants = GearRegistry.formatEnchantements(original);
        if (enchants != null) {
            lore.add("§7Enchantements : §f" + enchants);
        }
        for (String ligne : GearRegistry.decrireEffets(original)) {
            lore.add(ligne);
        }
        if (indices.size() > 1) {
            lore.add("§8×" + indices.size() + " exemplaires en collection");
        }
        lore.add("");
        lore.add(equipe ? "§aÉquipé" : "§eClique pour équiper !");
        meta.setLore(lore);

        // Le clic agit sur le premier exemplaire du groupe : tous les exemplaires d'une même
        // signature étant strictement identiques (type+matériau+niveau d'enchant), peu
        // importe lequel est réellement équipé en interne.
        meta.getPersistentDataContainer().set(Cles.EQUIPEMENT_CHOIX_CATEGORIE, PersistentDataType.STRING, CATEGORIE_GEAR);
        meta.getPersistentDataContainer().set(Cles.EQUIPEMENT_CHOIX_INDEX, PersistentDataType.INTEGER, indices.get(0));
        icone.setItemMeta(meta);
        return icone;
    }

    private ItemStack creerIconeFleche(PlayerDataManager data, UUID uuid, ItemStack original, List<Integer> indices) {
        ItemStack icone = original.clone();
        icone.setAmount(1);
        ItemMeta meta = icone.getItemMeta();
        MobRarity rarete = MobRarity.values()[ArrowRegistry.getRarete(original)];
        boolean equipee = indices.contains(data.getIndexFlecheEquipee(uuid));

        List<String> lore = new ArrayList<>();
        lore.add("§7Rareté : " + rarete.getCouleur() + rarete.getLabel());
        String effet = ArrowRegistry.decrireEffet(original);
        if (effet != null) {
            lore.add("§7Effet : " + effet);
        }
        if (indices.size() > 1) {
            lore.add("§8×" + indices.size() + " exemplaires en collection");
        }
        lore.add("");
        lore.add(equipee ? "§aFlèche actuellement équipée" : "§eClique pour équiper !");
        meta.setLore(lore);

        meta.getPersistentDataContainer().set(Cles.EQUIPEMENT_CHOIX_CATEGORIE, PersistentDataType.STRING, CATEGORIE_FLECHE);
        meta.getPersistentDataContainer().set(Cles.EQUIPEMENT_CHOIX_INDEX, PersistentDataType.INTEGER, indices.get(0));
        icone.setItemMeta(meta);
        return icone;
    }
}
