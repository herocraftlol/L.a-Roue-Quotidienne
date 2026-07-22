package fr.fidelmobs.managers;

import fr.fidelmobs.Cles;
import fr.fidelmobs.LoyaltyMobsPlugin;
import fr.fidelmobs.arena.ArrowRegistry;
import fr.fidelmobs.data.PlayerDataManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Arc du kit PvP (4e slot de la hotbar) : tire la flèche à effet actuellement équipée par
 * le joueur (ou une flèche simple par défaut) sans jamais la consommer — au même titre que
 * l'armure/épée du kit, elle est prêtée pour la durée du combat en arène. Un temps de
 * recharge minimal est imposé entre deux tirs pour éviter le spam.
 */
public class ArrowManager {

    public static final int SLOT_ARC = 3; // 4e slot de la barre d'accès rapide
    public static final int SLOT_FLECHE = 9; // 1re case de l'inventaire principal (sous la hotbar)

    private final LoyaltyMobsPlugin plugin;
    private final Map<UUID, Long> prochainTirAutorise = new HashMap<>();

    public ArrowManager(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
    }

    private long cooldownMs() {
        return Math.max(1, plugin.getConfig().getInt("arene.arc-cooldown-secondes", 3)) * 1000L;
    }

    private ItemStack creerArc() {
        ItemStack item = new ItemStack(Material.BOW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§b§lArc du kit");
        meta.setLore(List.of(
                "§7Tire la flèche équipée.",
                "§7Change de flèche via le menu",
                "§7d'équipement (avant-avant-dernier",
                "§7slot de la hotbar)."
        ));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        // Infinity n'empêche pas nativement la consommation des flèches à pointe (effets),
        // mais permet un affichage cohérent ; la non-consommation réelle est forcée dans
        // onTir() via event.setConsumeItem(false), quel que soit le type de flèche.
        meta.addEnchant(Enchantment.INFINITY, 1, true);
        meta.getPersistentDataContainer().set(Cles.ARC_KIT, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean estArcKit(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(Cles.ARC_KIT, PersistentDataType.BYTE);
    }

    /**
     * Place l'arc au slot 4 et la flèche actuellement équipée (ou une flèche simple par
     * défaut) dans l'inventaire principal. Les deux sont verrouillés comme le reste du kit.
     */
    public void equiper(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerDataManager data = plugin.getPlayerDataManager();
        KitManager kit = plugin.getKitManager();

        player.getInventory().setItem(SLOT_ARC, kit.verrouiller(creerArc()));

        int index = data.getIndexFlecheEquipee(uuid);
        List<ItemStack> fleches = data.getFleches(uuid);
        ItemStack fleche = (index >= 0 && index < fleches.size())
                ? fleches.get(index).clone()
                : ArrowRegistry.flecheParDefaut();
        fleche.setAmount(1);
        player.getInventory().setItem(SLOT_FLECHE, kit.verrouiller(fleche));
    }

    public void retirer(Player player) {
        KitManager kit = plugin.getKitManager();
        ItemStack arc = player.getInventory().getItem(SLOT_ARC);
        if (kit.estKit(arc)) player.getInventory().setItem(SLOT_ARC, null);
        ItemStack fleche = player.getInventory().getItem(SLOT_FLECHE);
        if (kit.estKit(fleche)) player.getInventory().setItem(SLOT_FLECHE, null);
    }

    /**
     * Gère un tir à l'arc : impose le temps de recharge et empêche la consommation de la
     * flèche équipée (prêtée en illimité, comme le reste du kit). N'agit que sur l'arc
     * du kit ; laisse tout autre arc/flèche (hors arène) au comportement vanilla normal.
     */
    public void onTir(EntityShootBowEvent event, Player player) {
        if (!estArcKit(event.getBow())) return;

        long maintenant = System.currentTimeMillis();
        long prochain = prochainTirAutorise.getOrDefault(player.getUniqueId(), 0L);
        if (maintenant < prochain) {
            event.setCancelled(true);
            long resteMs = prochain - maintenant;
            player.sendActionBar(Component.text("Recharge de l'arc : " + String.format("%.1f", resteMs / 1000.0) + "s")
                    .color(NamedTextColor.RED));
            return;
        }

        prochainTirAutorise.put(player.getUniqueId(), maintenant + cooldownMs());
        event.setConsumeItem(false);
        player.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.8f, 1.2f);
    }

    public void oublierJoueur(UUID uuid) {
        prochainTirAutorise.remove(uuid);
    }
}
