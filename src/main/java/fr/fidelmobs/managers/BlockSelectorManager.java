package fr.fidelmobs.managers;

import fr.fidelmobs.Cles;
import fr.fidelmobs.LoyaltyMobsPlugin;
import fr.fidelmobs.arena.BlockRegistry;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Item donné dans l'arène (avant-dernier slot de la hotbar) pour ouvrir un menu et choisir,
 * parmi les blocs débloqués à la roue, celui utilisé comme bloc de construction actif — sans
 * avoir à taper /bloc choisir <type> à la main.
 */
public class BlockSelectorManager {

    public static final int SLOT_SELECTEUR = 7; // avant-dernier slot de la barre d'accès rapide (8e sur 9)

    private final LoyaltyMobsPlugin plugin;

    public BlockSelectorManager(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
    }

    public ItemStack creerItem() {
        ItemStack item = new ItemStack(Material.BRICKS);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§b§l✦ Choisir un bloc");
        meta.setLore(List.of(
                "§7Clic droit pour choisir",
                "§7le bloc de construction",
                "§7que tu poses en arène."
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(Cles.BLOC_SELECTEUR, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean estItemSelecteur(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(Cles.BLOC_SELECTEUR, PersistentDataType.BYTE);
    }

    public void donnerItem(Player player) {
        player.getInventory().setItem(SLOT_SELECTEUR, creerItem());
    }

    public void retirerItem(Player player) {
        ItemStack actuel = player.getInventory().getItem(SLOT_SELECTEUR);
        if (estItemSelecteur(actuel)) {
            player.getInventory().setItem(SLOT_SELECTEUR, null);
        }
    }

    public void ouvrirMenu(Player player) {
        PlayerDataManager data = plugin.getPlayerDataManager();
        UUID uuid = player.getUniqueId();
        Set<Material> debloques = data.getBlocsDebloques(uuid);

        if (debloques.isEmpty()) {
            player.sendMessage("§7Tu n'as encore débloqué aucun bloc. Utilise §f/roue §7pour en obtenir !");
            return;
        }

        List<Material> tries = new ArrayList<>(debloques);
        tries.sort(java.util.Comparator
                .comparing((Material m) -> BlockRegistry.getRarete(m).ordinal())
                .reversed()
                .thenComparing(Enum::name));

        int taille = Math.min(54, Math.max(9, ((tries.size() - 1) / 9 + 1) * 9));
        BlockSelectorInventoryHolder holder = new BlockSelectorInventoryHolder();
        Inventory inv = Bukkit.createInventory(holder, taille, "§b✦ Choisir un bloc");
        holder.setInventory(inv);

        Material actif = data.getBlocActif(uuid);
        for (Material m : tries) {
            inv.addItem(creerIcone(m, m.equals(actif)));
        }

        player.openInventory(inv);
    }

    public void choisir(Player player, Material material) {
        PlayerDataManager data = plugin.getPlayerDataManager();
        UUID uuid = player.getUniqueId();

        if (!data.getBlocsDebloques(uuid).contains(material)) {
            player.sendMessage("§cTu n'as pas encore débloqué ce bloc.");
            return;
        }

        data.setBlocActif(uuid, material);
        data.save(uuid);
        plugin.getBuildBlockManager().resynchroniser(player);
        MobRarity rarete = BlockRegistry.getRarete(material);
        player.sendMessage("§aBloc actif défini sur " + rarete.getCouleur() + material.name() + "§a.");
    }

    private ItemStack creerIcone(Material material, boolean actif) {
        ItemStack icone = new ItemStack(material);
        ItemMeta meta = icone.getItemMeta();
        MobRarity rarete = BlockRegistry.getRarete(material);
        meta.setDisplayName(rarete.getCouleur() + "§l" + material.name());
        List<String> lore = new ArrayList<>();
        lore.add("§7Rareté : " + rarete.getCouleur() + rarete.getLabel());
        lore.add("");
        if (actif) {
            lore.add("§aBloc actuellement actif");
        } else {
            lore.add("§eClique pour l'activer !");
        }
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(Cles.BLOC_CHOIX, PersistentDataType.STRING, material.name());
        icone.setItemMeta(meta);
        return icone;
    }
}
