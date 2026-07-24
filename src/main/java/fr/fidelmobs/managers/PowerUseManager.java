package fr.fidelmobs.managers;

import fr.fidelmobs.Cles;
import fr.fidelmobs.LoyaltyMobsPlugin;
import fr.fidelmobs.arena.PowerRegistry;
import fr.fidelmobs.data.PlayerDataManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pouvoir spécial équipé (5e slot de la hotbar) : exécute l'effet du pouvoir choisi via le
 * sélecteur (6e slot, voir {@link PowerSelectorManager}) au clic droit. Un temps de recharge
 * PARTAGÉ entre tous les pouvoirs (par défaut 5 minutes, configurable) est imposé entre deux
 * activations, quel que soit le pouvoir utilisé — à la manière du cooldown de l'arc du kit.
 */
public class PowerUseManager {

    public static final int SLOT_POUVOIR_ACTIF = 4; // 5e slot de la barre d'accès rapide

    private static final long INTERVALLE_AFFICHAGE_TICKS = 2L;

    private final LoyaltyMobsPlugin plugin;
    private final Map<UUID, Long> prochainUsageAutorise = new HashMap<>();
    private final Set<UUID> enAttenteMessagePret = new HashSet<>();

    public PowerUseManager(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
        demarrerAffichageEnDirect();
    }

    private void demarrerAffichageEnDirect() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (prochainUsageAutorise.isEmpty()) return;
            long maintenant = System.currentTimeMillis();
            Iterator<Map.Entry<UUID, Long>> it = prochainUsageAutorise.entrySet().iterator();
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
                    joueur.sendActionBar(Component.text("⏳ Recharge du pouvoir : "
                                    + String.format("%.0f", resteMs / 1000.0) + "s")
                            .color(NamedTextColor.RED));
                } else if (enAttenteMessagePret.remove(uuid)) {
                    joueur.sendActionBar(Component.text("✔ Pouvoir prêt à être utilisé !").color(NamedTextColor.GREEN));
                }
            }
        }, INTERVALLE_AFFICHAGE_TICKS, INTERVALLE_AFFICHAGE_TICKS);
    }

    private long cooldownMs() {
        return Math.max(1, plugin.getConfig().getInt("arene.pouvoir-cooldown-secondes", 300)) * 1000L;
    }

    public boolean estItemPouvoirActif(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(Cles.POUVOIR_ACTIF, PersistentDataType.BYTE);
    }

    /**
     * Construit l'item représentant le pouvoir actuellement équipé par le joueur (ou un
     * item neutre s'il n'en a équipé aucun) pour le placer au 5e slot.
     */
    private ItemStack construireItemEquipe(UUID uuid) {
        PlayerDataManager data = plugin.getPlayerDataManager();
        int index = data.getIndexPouvoirEquipe(uuid);
        var pouvoirs = data.getPouvoirs(uuid);
        if (index >= 0 && index < pouvoirs.size()) {
            return PowerRegistry.construireItemActif(pouvoirs.get(index));
        }
        return itemAucunPouvoir();
    }

    private ItemStack itemAucunPouvoir() {
        ItemStack item = new ItemStack(Material.GRAY_DYE);
        var meta = item.getItemMeta();
        meta.setDisplayName("§7Aucun pouvoir équipé");
        meta.setLore(java.util.List.of(
                "§7Utilise le sélecteur de pouvoirs",
                "§7(6e slot) pour en choisir un dans",
                "§7ta collection obtenue à la roue."
        ));
        meta.getPersistentDataContainer().set(Cles.POUVOIR_ACTIF, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** Place au 5e slot le pouvoir actuellement équipé (verrouillé comme le reste du kit). */
    public void equiper(Player player) {
        KitManager kit = plugin.getKitManager();
        player.getInventory().setItem(SLOT_POUVOIR_ACTIF, kit.verrouiller(construireItemEquipe(player.getUniqueId())));
    }

    public void retirer(Player player) {
        KitManager kit = plugin.getKitManager();
        ItemStack actuel = player.getInventory().getItem(SLOT_POUVOIR_ACTIF);
        if (kit.estKit(actuel)) player.getInventory().setItem(SLOT_POUVOIR_ACTIF, null);
    }

    /**
     * Traite une tentative d'activation du pouvoir équipé : refuse si en recharge, exécute
     * l'effet et déclenche le temps de recharge partagé sinon.
     */
    public void activer(Player player, ItemStack item) {
        String id = PowerRegistry.getId(item);
        if (id == null) {
            player.sendMessage("§7Utilise d'abord le sélecteur de pouvoirs (6e slot) pour en choisir un.");
            return;
        }

        UUID uuid = player.getUniqueId();
        long maintenant = System.currentTimeMillis();
        long prochain = prochainUsageAutorise.getOrDefault(uuid, 0L);
        if (maintenant < prochain) {
            long resteMs = prochain - maintenant;
            player.sendActionBar(Component.text("Recharge du pouvoir : "
                            + String.format("%.0f", resteMs / 1000.0) + "s")
                    .color(NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
            return;
        }

        PowerRegistry.PowerDefinition def = PowerRegistry.getParId(id);
        if (def == null) return;

        try {
            def.effet().executer(plugin, player);
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Échec de l'exécution du pouvoir " + id + " pour " + player.getName(), e);
        }

        player.sendMessage(def.rarete().getCouleur() + "✪ Pouvoir activé : §l" + def.nom());
        prochainUsageAutorise.put(uuid, maintenant + cooldownMs());
        enAttenteMessagePret.add(uuid);
    }

    public void oublierJoueur(UUID uuid) {
        prochainUsageAutorise.remove(uuid);
        enAttenteMessagePret.remove(uuid);
    }
}
