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

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Pouvoir spécial équipé (5e slot de la hotbar) : exécute l'effet du pouvoir choisi via le
 * sélecteur (6e slot, voir {@link PowerSelectorManager}) au clic droit.
 *
 * Chaque pouvoir a son propre temps de recharge, totalement INDÉPENDANT des autres pouvoirs
 * possédés/équipés : utiliser un pouvoir n'empêche jamais d'en utiliser un autre juste après.
 * De plus, si un pouvoir a été obtenu plusieurs fois à la roue, chaque copie est une charge
 * distincte : on peut l'utiliser autant de fois que de copies possédées d'affilée avant de
 * devoir attendre qu'une charge se recharge (comme les mobs invoqués).
 *
 * L'info de recharge en barre d'action ne s'affiche QUE lorsque :
 *  - le joueur a le pouvoir concerné sélectionné dans sa hotbar (5e slot tenu en main) ;
 *  - il ne l'a pas remplacé entre-temps par un autre pouvoir via le sélecteur ;
 *  - aucune autre charge de ce même pouvoir n'est déjà disponible.
 * Changer de slot, équiper un autre pouvoir ou posséder une seconde copie déjà prête coupe
 * silencieusement l'affichage, sans spammer le joueur.
 */
public class PowerUseManager {

    public static final int SLOT_POUVOIR_ACTIF = 4; // 5e slot de la barre d'accès rapide

    private static final long INTERVALLE_AFFICHAGE_TICKS = 2L;

    private final LoyaltyMobsPlugin plugin;
    // Joueurs pour qui on suit en arrière-plan le temps restant avant qu'une charge du
    // pouvoir équipé redevienne disponible. Le suivi reste actif même hors du 5e slot (pour
    // pouvoir réagir dès qu'il y revient), mais l'affichage en barre d'action, lui, est
    // conditionné au fait d'avoir concrètement ce pouvoir sélectionné en main.
    private final Set<UUID> enAttenteAffichage = new HashSet<>();
    private final Set<UUID> enAttenteMessagePret = new HashSet<>();
    // Horodatage de la dernière utilisation réussie d'un pouvoir par joueur : sert à
    // attribuer un kill à un pouvoir plutôt qu'à un coup direct (voir ArenaProtectionListener).
    private final java.util.Map<UUID, Long> dernierUsagePouvoirMs = new java.util.HashMap<>();

    /** Vrai si ce joueur a utilisé un pouvoir avec succès il y a moins de {@code fenetreMs}. */
    public boolean aUtiliseUnPouvoirRecemment(UUID uuid, long fenetreMs) {
        Long t = dernierUsagePouvoirMs.get(uuid);
        return t != null && System.currentTimeMillis() - t <= fenetreMs;
    }

    public PowerUseManager(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
        demarrerAffichageEnDirect();
    }

    private void demarrerAffichageEnDirect() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (enAttenteAffichage.isEmpty()) return;
            PlayerDataManager data = plugin.getPlayerDataManager();
            Iterator<UUID> it = enAttenteAffichage.iterator();
            while (it.hasNext()) {
                UUID uuid = it.next();
                Player joueur = plugin.getServer().getPlayer(uuid);
                if (joueur == null || !joueur.isOnline()) {
                    it.remove();
                    enAttenteMessagePret.remove(uuid);
                    continue;
                }

                String id = data.getPouvoirEquipe(uuid);
                if (id == null) {
                    it.remove();
                    enAttenteMessagePret.remove(uuid);
                    continue;
                }

                // Le pouvoir suivi n'est affiché que si le joueur l'a effectivement
                // sélectionné dans sa hotbar (5e slot tenu en main) au moment présent.
                boolean pouvoirSelectionne = joueur.getInventory().getHeldItemSlot() == SLOT_POUVOIR_ACTIF;

                int disponibles = data.getUnitesDisponiblesPouvoir(uuid, id);
                if (disponibles > 0) {
                    // Une charge (celle-ci ou une autre copie) est de nouveau disponible :
                    // on arrête le suivi, et on ne prévient que si le pouvoir est encore
                    // sélectionné — pas de message si le joueur a changé de slot entre-temps.
                    if (pouvoirSelectionne && enAttenteMessagePret.remove(uuid)) {
                        joueur.sendActionBar(Component.text("✔ Pouvoir prêt à être utilisé !").color(NamedTextColor.GREEN));
                    } else {
                        enAttenteMessagePret.remove(uuid);
                    }
                    it.remove();
                    continue;
                }

                if (!pouvoirSelectionne) {
                    // Toujours en recharge, mais le joueur regarde ailleurs : on continue de
                    // suivre en silence, sans lui afficher quoi que ce soit pour l'instant.
                    continue;
                }

                long resteMs = data.getProchaineDisponibilitePouvoir(uuid, id) - System.currentTimeMillis();
                if (resteMs > 0) {
                    joueur.sendActionBar(Component.text("⏳ Recharge du pouvoir : "
                                    + String.format("%.0f", resteMs / 1000.0) + "s")
                            .color(NamedTextColor.RED));
                    enAttenteMessagePret.add(uuid);
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
     * item neutre s'il n'en a équipé aucun, ou plus la copie nécessaire) pour le 5e slot.
     */
    private ItemStack construireItemEquipe(UUID uuid) {
        PlayerDataManager data = plugin.getPlayerDataManager();
        String id = data.getPouvoirEquipe(uuid);
        if (id != null && data.getNombrePouvoir(uuid, id) > 0) {
            return PowerRegistry.construireItemActif(id);
        }
        return itemAucunPouvoir();
    }

    private ItemStack itemAucunPouvoir() {
        ItemStack item = new ItemStack(Material.GRAY_DYE);
        var meta = item.getItemMeta();
        meta.setDisplayName("§7Aucun pouvoir équipé");
        meta.setLore(List.of(
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
     * Traite une tentative d'activation du pouvoir équipé : refuse si plus aucune charge
     * n'est disponible pour CE pouvoir précis, exécute l'effet et consomme une charge sinon
     * (charge qui part en recharge indépendamment des autres pouvoirs possédés).
     */
    public void activer(Player player, ItemStack item) {
        String id = PowerRegistry.getId(item);
        if (id == null) {
            player.sendMessage("§7Utilise d'abord le sélecteur de pouvoirs (6e slot) pour en choisir un.");
            return;
        }

        PlayerDataManager data = plugin.getPlayerDataManager();
        UUID uuid = player.getUniqueId();

        if (data.getNombrePouvoir(uuid, id) <= 0) {
            player.sendMessage("§cTu ne possèdes plus ce pouvoir. Utilise le sélecteur (6e slot) pour en choisir un autre.");
            return;
        }

        int disponibles = data.getUnitesDisponiblesPouvoir(uuid, id);
        if (disponibles <= 0) {
            long resteMs = data.getProchaineDisponibilitePouvoir(uuid, id) - System.currentTimeMillis();
            player.sendActionBar(Component.text("Recharge du pouvoir : "
                            + String.format("%.0f", Math.max(0, resteMs) / 1000.0) + "s")
                    .color(NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
            enAttenteAffichage.add(uuid);
            enAttenteMessagePret.add(uuid);
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

        data.utiliserUnitePouvoir(uuid, id, cooldownMs());
        data.save(uuid);
        data.incrementerCompteur(uuid, "pouvoirs_utilises", 1);
        data.incrementerCompteurQuotidien(uuid, "pouvoirs_utilises", 1);
        dernierUsagePouvoirMs.put(uuid, System.currentTimeMillis());

        int restantes = disponibles - 1;
        String suffixe = restantes > 0
                ? " §7(" + restantes + " autre(s) charge(s) dispo tout de suite)"
                : "";
        player.sendMessage(def.rarete().getCouleur() + "✪ Pouvoir activé : §l" + def.nom() + "§r" + suffixe);

        // Si une autre charge du même pouvoir est encore disponible tout de suite, inutile
        // de suivre/afficher quoi que ce soit : le joueur peut le réutiliser immédiatement.
        if (restantes <= 0) {
            enAttenteAffichage.add(uuid);
        }
    }

    public void oublierJoueur(UUID uuid) {
        enAttenteAffichage.remove(uuid);
        enAttenteMessagePret.remove(uuid);
    }
}
