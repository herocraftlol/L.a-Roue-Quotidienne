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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Arc du kit PvP (4e slot de la hotbar) : tire la flèche à effet actuellement équipée par
 * le joueur (ou une flèche simple par défaut) sans jamais la consommer — au même titre que
 * l'armure/épée du kit, elle est prêtée pour la durée du combat en arène. Un temps de
 * recharge minimal est imposé entre deux tirs pour éviter le spam, et ce temps de recharge
 * restant est affiché en direct (barre d'action, mise à jour plusieurs fois par seconde)
 * jusqu'à ce que l'arc soit de nouveau prêt.
 */
public class ArrowManager {

    public static final int SLOT_ARC = 3; // 4e slot de la barre d'accès rapide
    public static final int SLOT_FLECHE = 9; // 1re case de l'inventaire principal (sous la hotbar)

    // Fréquence (en ticks) de rafraîchissement de l'affichage en direct de la recharge.
    // 2 ticks (0,1s) donne un décompte fluide sans surcharger le serveur.
    private static final long INTERVALLE_AFFICHAGE_TICKS = 2L;

    private final LoyaltyMobsPlugin plugin;
    private final Map<UUID, Long> prochainTirAutorise = new HashMap<>();
    // Joueurs pour qui il reste à afficher le message "Arc prêt !" une fois la recharge terminée
    private final Set<UUID> enAttenteMessagePret = new HashSet<>();

    public ArrowManager(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
        demarrerAffichageEnDirect();
    }

    /**
     * Tâche répétitive qui met à jour la barre d'action de chaque joueur ayant tiré
     * récemment, tant que son arc est en recharge, pour que le temps d'attente restant
     * s'affiche en direct (et non plus seulement au moment d'un tir refusé).
     */
    private void demarrerAffichageEnDirect() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (prochainTirAutorise.isEmpty()) return;
            long maintenant = System.currentTimeMillis();
            Iterator<Map.Entry<UUID, Long>> it = prochainTirAutorise.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Long> entree = it.next();
                UUID uuid = entree.getKey();
                Player joueur = plugin.getServer().getPlayer(uuid);
                if (joueur == null || !joueur.isOnline()) {
                    it.remove();
                    enAttenteMessagePret.remove(uuid);
                    continue;
                }

                long resteMs = entree.getValue() - maintenant;
                if (resteMs > 0) {
                    joueur.sendActionBar(Component.text("⏳ Recharge de l'arc : "
                                    + String.format("%.1f", resteMs / 1000.0) + "s")
                            .color(NamedTextColor.RED));
                } else if (enAttenteMessagePret.remove(uuid)) {
                    joueur.sendActionBar(Component.text("✔ Arc prêt à tirer !").color(NamedTextColor.GREEN));
                }
            }
        }, INTERVALLE_AFFICHAGE_TICKS, INTERVALLE_AFFICHAGE_TICKS);
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
     * Construit un exemplaire (amount=1) de la flèche actuellement équipée par le joueur,
     * ou une flèche simple par défaut s'il n'en a aucune. Utilisé à la fois pour équiper
     * le kit et pour la remettre en place après chaque tir (voir onTir).
     */
    private ItemStack construireFlecheEquipee(UUID uuid) {
        PlayerDataManager data = plugin.getPlayerDataManager();
        int index = data.getIndexFlecheEquipee(uuid);
        List<ItemStack> fleches = data.getFleches(uuid);
        ItemStack fleche = (index >= 0 && index < fleches.size())
                ? fleches.get(index).clone()
                : ArrowRegistry.flecheParDefaut();
        fleche.setAmount(1);
        return fleche;
    }

    /**
     * Place l'arc au slot 4 et la flèche actuellement équipée (ou une flèche simple par
     * défaut) dans l'inventaire principal. Les deux sont verrouillés comme le reste du kit.
     */
    public void equiper(Player player) {
        KitManager kit = plugin.getKitManager();
        player.getInventory().setItem(SLOT_ARC, kit.verrouiller(creerArc()));
        player.getInventory().setItem(SLOT_FLECHE, kit.verrouiller(construireFlecheEquipee(player.getUniqueId())));
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
        enAttenteMessagePret.add(player.getUniqueId());
        event.setConsumeItem(false);
        player.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.8f, 1.2f);

        // Filet de sécurité : l'enchantement Infinity ne fonctionne nativement qu'avec des
        // flèches simples sans NBT, pas avec nos flèches à effet (nom, lore, données
        // persistantes). Même avec setConsumeItem(false), certaines versions du jeu
        // consomment quand même la flèche du slot dédié. On force donc sa réapparition,
        // verrouillée, au tick suivant (après que le jeu ait fini de traiter le tir),
        // pour qu'elle soit toujours tirable en permanence.
        UUID uuid = player.getUniqueId();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            player.getInventory().setItem(SLOT_FLECHE, plugin.getKitManager().verrouiller(construireFlecheEquipee(uuid)));
            player.updateInventory();
        });
    }

    public void oublierJoueur(UUID uuid) {
        prochainTirAutorise.remove(uuid);
        enAttenteMessagePret.remove(uuid);
    }
}
