package fr.fidelmobs.managers;

import fr.fidelmobs.Cles;
import fr.fidelmobs.LoyaltyMobsPlugin;
import fr.fidelmobs.arena.SpawnUtils;
import fr.fidelmobs.data.PlayerDataManager;
import fr.fidelmobs.mobs.MobRarity;
import fr.fidelmobs.mobs.MobRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Item donné dans l'arène (2e slot de la hotbar) pour ouvrir un menu et invoquer un allié
 * de sa collection sans avoir à taper /invoquer à la main.
 */
public class InvocationManager {

    public static final int SLOT_INVOCATION = 1; // 2e slot de la barre d'accès rapide

    private final LoyaltyMobsPlugin plugin;

    public InvocationManager(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
    }

    public ItemStack creerItem() {
        ItemStack item = new ItemStack(Material.ZOMBIE_HEAD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§d§l✦ Invoquer un allié");
        meta.setLore(List.of(
                "§7Clic droit pour choisir",
                "§7un allié de ta collection",
                "§7à invoquer à tes côtés."
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(Cles.INVOCATION, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean estItemInvocation(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(Cles.INVOCATION, PersistentDataType.BYTE);
    }

    public void donnerItem(Player player) {
        player.getInventory().setItem(SLOT_INVOCATION, creerItem());
    }

    public void retirerItem(Player player) {
        ItemStack actuel = player.getInventory().getItem(SLOT_INVOCATION);
        if (estItemInvocation(actuel)) {
            player.getInventory().setItem(SLOT_INVOCATION, null);
        }
    }

    public void ouvrirMenu(Player player) {
        Map<EntityType, Integer> collection = plugin.getPlayerDataManager().getCollection(player.getUniqueId());
        if (collection.isEmpty()) {
            player.sendMessage("§7Tu n'as encore aucun allié. Utilise §f/roue §7pour en obtenir !");
            return;
        }

        List<EntityType> tries = collection.keySet().stream()
                .sorted(Comparator.comparing((EntityType t) -> MobRegistry.getRarete(t).ordinal())
                        .reversed()
                        .thenComparing(Enum::name))
                .collect(Collectors.toList());

        int taille = Math.min(54, Math.max(9, ((tries.size() - 1) / 9 + 1) * 9));
        InvocationInventoryHolder holder = new InvocationInventoryHolder();
        Inventory inv = Bukkit.createInventory(holder, taille, "§d✦ Invoquer un allié");
        holder.setInventory(inv);

        for (EntityType type : tries) {
            inv.addItem(creerIcone(type, collection.get(type), MobRegistry.getRarete(type)));
        }

        player.openInventory(inv);
    }

    /**
     * Traite le clic sur une icône du menu : consomme un allié de la collection et l'invoque
     * à côté du joueur. Ne fait rien si la collection a changé entre-temps (message d'échec).
     */
    public void invoquerDepuisMenu(Player player, EntityType type) {
        PlayerDataManager data = plugin.getPlayerDataManager();
        if (!data.retirerMob(player.getUniqueId(), type)) {
            player.sendMessage("§cTu ne possèdes plus de " + nomLisible(type) + ".");
            return;
        }
        data.save(player.getUniqueId());

        Location spawnLoc = SpawnUtils.trouverPositionSpawnValide(player);
        Entity entite = player.getWorld().spawnEntity(spawnLoc, type);
        MobRarity rarete = MobRegistry.getRarete(type);
        entite.setCustomName(rarete.getCouleur() + player.getName() + "'s " + nomLisible(type));
        entite.setCustomNameVisible(true);

        if (entite instanceof Mob mob) {
            plugin.getAllyListener().enregistrerAllie(mob, player);
        }

        player.sendMessage("§aTu as invoqué " + rarete.getCouleur() + nomLisible(type) + " §aà tes côtés !");
    }

    private ItemStack creerIcone(EntityType type, int nombre, MobRarity rarete) {
        ItemStack icone = new ItemStack(spawnEggPour(type));
        ItemMeta meta = icone.getItemMeta();
        meta.setDisplayName(rarete.getCouleur() + "§l" + nomLisible(type));
        List<String> lore = new ArrayList<>();
        lore.add("§7Quantité : " + rarete.getCouleur() + nombre);
        lore.add("§7Rareté : " + rarete.getCouleur() + rarete.getLabel());
        lore.add("");
        lore.add("§eClique pour invoquer !");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(Cles.INVOCATION_TYPE, PersistentDataType.STRING, type.name());
        icone.setItemMeta(meta);
        return icone;
    }

    private Material spawnEggPour(EntityType type) {
        try {
            return Material.valueOf(type.name() + "_SPAWN_EGG");
        } catch (IllegalArgumentException e) {
            return Material.EGG;
        }
    }

    private String nomLisible(EntityType type) {
        String s = type.name().toLowerCase().replace('_', ' ');
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
