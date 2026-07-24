package fr.fidelmobs.managers;

import fr.fidelmobs.Cles;
import fr.fidelmobs.LoyaltyMobsPlugin;
import fr.fidelmobs.arena.PowerRegistry;
import fr.fidelmobs.data.PlayerDataManager;
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
 * Item donné dans l'arène (avant-avant-avant-dernier slot de la hotbar, 6e sur 9) pour
 * ouvrir un menu regroupant tous les pouvoirs spéciaux obtenus à la roue et en équiper un,
 * qui apparaît alors utilisable au 5e slot (voir {@link PowerUseManager}).
 */
public class PowerSelectorManager {

    public static final int SLOT_SELECTEUR_POUVOIR = 5; // 6e slot de la barre d'accès rapide

    private final LoyaltyMobsPlugin plugin;

    public PowerSelectorManager(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
    }

    public ItemStack creerItem() {
        ItemStack item = new ItemStack(Material.BEACON);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§b§l✦ Pouvoirs spéciaux");
        meta.setLore(List.of(
                "§7Clic droit pour choisir le pouvoir",
                "§7à utiliser parmi ta collection.",
                "§7Le pouvoir choisi s'active ensuite",
                "§7avec le 5e slot de ta hotbar."
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(Cles.POUVOIR_SELECTEUR, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean estItemSelecteur(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(Cles.POUVOIR_SELECTEUR, PersistentDataType.BYTE);
    }

    public void donnerItem(Player player) {
        player.getInventory().setItem(SLOT_SELECTEUR_POUVOIR, creerItem());
    }

    public void retirerItem(Player player) {
        ItemStack actuel = player.getInventory().getItem(SLOT_SELECTEUR_POUVOIR);
        if (estItemSelecteur(actuel)) {
            player.getInventory().setItem(SLOT_SELECTEUR_POUVOIR, null);
        }
    }

    public void ouvrirMenu(Player player) {
        PlayerDataManager data = plugin.getPlayerDataManager();
        UUID uuid = player.getUniqueId();
        List<String> pouvoirs = data.getPouvoirs(uuid);

        if (pouvoirs.isEmpty()) {
            player.sendMessage("§7Tu n'as encore aucun pouvoir spécial. Utilise §f/roue §7pour en obtenir !");
            return;
        }

        int nbLignes = Math.min(6, Math.max(1, (pouvoirs.size() - 1) / 9 + 1));
        PowerSelectorInventoryHolder holder = new PowerSelectorInventoryHolder();
        Inventory inv = Bukkit.createInventory(holder, nbLignes * 9, "§b✦ Pouvoirs spéciaux");
        holder.setInventory(inv);

        // Triés par rareté décroissante pour que les plus puissants sautent aux yeux
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < pouvoirs.size(); i++) indices.add(i);
        indices.sort(Comparator
                .comparing((Integer i) -> PowerRegistry.getRarete(pouvoirs.get(i)).ordinal())
                .reversed());

        int equipe = data.getIndexPouvoirEquipe(uuid);
        for (int i : indices) {
            inv.addItem(creerIcone(pouvoirs.get(i), i, i == equipe));
        }

        player.openInventory(inv);
    }

    /**
     * Traite un clic sur une icône du menu : équipe le pouvoir correspondant, qui apparaît
     * ensuite au 5e slot de la hotbar (dans l'arène) prêt à être activé.
     */
    public void choisir(Player player, int index) {
        PlayerDataManager data = plugin.getPlayerDataManager();
        UUID uuid = player.getUniqueId();
        List<String> pouvoirs = data.getPouvoirs(uuid);
        if (index < 0 || index >= pouvoirs.size()) return;

        data.setIndexPouvoirEquipe(uuid, index);
        data.save(uuid);
        player.sendMessage("§aPouvoir équipé : " + PowerRegistry.getRarete(pouvoirs.get(index)).getCouleur()
                + PowerRegistry.getNom(pouvoirs.get(index)));

        if (plugin.getArenaProtectionListener().estDansArene(player)) {
            plugin.getPowerUseManager().equiper(player);
            player.updateInventory();
        }
    }

    private ItemStack creerIcone(String id, int index, boolean equipe) {
        ItemStack icone = PowerRegistry.construireIcone(id);
        ItemMeta meta = icone.getItemMeta();
        List<String> lore = new ArrayList<>(meta.getLore() != null ? meta.getLore() : List.of());
        lore.add("");
        lore.add(equipe ? "§aPouvoir actuellement équipé" : "§eClique pour équiper !");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(Cles.POUVOIR_CHOIX_INDEX, PersistentDataType.INTEGER, index);
        icone.setItemMeta(meta);
        return icone;
    }
}
