package fr.fidelmobs.listeners;

import fr.fidelmobs.LoyaltyMobsPlugin;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Gère la vie des mobs invoqués en tant qu'alliés :
 * - ils n'attaquent jamais leur propriétaire ni les alliés de celui-ci
 * - ils ciblent activement les autres joueurs ET les mobs invoqués par d'autres joueurs
 *   à proximité (les mobs adverses se combattent entre eux comme des armées rivales)
 * - ils sont nettoyés à la mort, à la déconnexion du propriétaire ou après une durée de vie max
 */
public class AllyListener implements Listener {

    public static final NamespacedKey CLE_PROPRIETAIRE = new NamespacedKey("fidelmobs", "ally_owner");

    // Rayon (en blocs) dans lequel un mob allié recherche activement une cible ennemie
    // (joueur adverse ou mob invoqué par un autre joueur).
    private static final double PORTEE_CIBLAGE_ACTIF = 20.0;
    // Intervalle (en ticks, 20 = 1s) entre deux rafraîchissements du ciblage actif.
    private static final long INTERVALLE_CIBLAGE_TICKS = 20L;

    private final LoyaltyMobsPlugin plugin;
    // propriétaire -> entités alliées actuellement en vie
    private final Map<UUID, Set<UUID>> alliesParProprietaire = new HashMap<>();

    public AllyListener(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
        demarrerCiblageActif();
    }

    /**
     * Tâche répétitive qui force chaque mob allié encore vivant à rechercher activement
     * une cible ennemie (joueur adverse ou mob invoqué par un autre joueur) à proximité.
     * Nécessaire car l'IA vanilla des mobs hostiles ne considère jamais d'autres mobs
     * comme cibles : sans ce forçage, deux armées invoquées s'ignoreraient complètement.
     */
    private void demarrerCiblageActif() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (alliesParProprietaire.isEmpty()) return;

            for (Map.Entry<UUID, Set<UUID>> entree : alliesParProprietaire.entrySet()) {
                UUID proprietaire = entree.getKey();
                for (UUID idAllie : entree.getValue()) {
                    Entity entite = plugin.getServer().getEntity(idAllie);
                    if (!(entite instanceof Mob mob) || mob.isDead() || !mob.isValid()) continue;

                    LivingEntity cibleActuelle = mob.getTarget();
                    if (cibleActuelle != null && !cibleActuelle.isDead() && cibleActuelle.isValid()
                            && estCibleEnnemieValide(proprietaire, cibleActuelle)) {
                        continue; // garde sa cible actuelle tant qu'elle reste valide
                    }

                    LivingEntity nouvelleCible = trouverCibleEnnemieLaPlusProche(proprietaire, mob);
                    if (nouvelleCible != null) {
                        mob.setTarget(nouvelleCible);
                    }
                }
            }
        }, INTERVALLE_CIBLAGE_TICKS, INTERVALLE_CIBLAGE_TICKS);
    }

    /**
     * Une cible est valide pour un mob allié si ce n'est ni son propriétaire, ni un mob
     * invoqué par ce même propriétaire (jamais de tir ami).
     */
    private boolean estCibleEnnemieValide(UUID proprietaireAllie, LivingEntity cible) {
        if (cible.getUniqueId().equals(proprietaireAllie)) return false;
        UUID proprietaireCible = getProprietaire(cible);
        return proprietaireCible == null || !proprietaireCible.equals(proprietaireAllie);
    }

    /**
     * Cherche, dans un rayon donné autour du mob, le joueur adverse ou le mob invoqué par
     * un autre joueur le plus proche. Ignore la faune/faune neutre (animaux non invoqués).
     */
    private LivingEntity trouverCibleEnnemieLaPlusProche(UUID proprietaire, Mob mob) {
        Location origine = mob.getLocation();
        LivingEntity meilleure = null;
        double meilleureDistanceCarree = PORTEE_CIBLAGE_ACTIF * PORTEE_CIBLAGE_ACTIF;

        for (Entity proche : mob.getNearbyEntities(PORTEE_CIBLAGE_ACTIF, PORTEE_CIBLAGE_ACTIF, PORTEE_CIBLAGE_ACTIF)) {
            if (!(proche instanceof LivingEntity vivant) || vivant.isDead() || !vivant.isValid()) continue;
            if (!estCibleEnnemieValide(proprietaire, vivant)) continue;

            boolean estJoueurEnnemi = vivant instanceof Player;
            boolean estMobAllieEnnemi = getProprietaire(vivant) != null;
            if (!estJoueurEnnemi && !estMobAllieEnnemi) continue; // ignore la faune neutre

            double distanceCarree = origine.distanceSquared(vivant.getLocation());
            if (distanceCarree < meilleureDistanceCarree) {
                meilleureDistanceCarree = distanceCarree;
                meilleure = vivant;
            }
        }
        return meilleure;
    }

    public void enregistrerAllie(Mob mob, Player proprietaire) {
        mob.getPersistentDataContainer().set(CLE_PROPRIETAIRE, PersistentDataType.STRING, proprietaire.getUniqueId().toString());
        alliesParProprietaire.computeIfAbsent(proprietaire.getUniqueId(), k -> new HashSet<>()).add(mob.getUniqueId());

        int dureeVie = plugin.getConfig().getInt("duree-vie-allie-secondes", 0);
        if (dureeVie > 0) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (mob.isValid() && !mob.isDead()) {
                        retirerAllie(proprietaire.getUniqueId(), mob.getUniqueId());
                        mob.remove();
                    }
                }
            }.runTaskLater(plugin, dureeVie * 20L);
        }
    }

    /**
     * Retourne l'UUID du joueur propriétaire de cette entité si c'est un mob allié invoqué,
     * ou {@code null} sinon. Public pour permettre d'attribuer un kill fait par un mob
     * allié à son propriétaire (voir ArenaProtectionListener#onMort).
     */
    public UUID getProprietaire(Entity entity) {
        String s = entity.getPersistentDataContainer().get(CLE_PROPRIETAIRE, PersistentDataType.STRING);
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void retirerAllie(UUID proprietaire, UUID entiteAllie) {
        Set<UUID> set = alliesParProprietaire.get(proprietaire);
        if (set != null) {
            set.remove(entiteAllie);
        }
    }

    @EventHandler
    public void onCiblage(EntityTargetLivingEntityEvent event) {
        UUID proprietaireAllie = getProprietaire(event.getEntity());
        if (proprietaireAllie == null) {
            return; // pas un mob allié, on ne touche à rien
        }

        LivingEntity cible = event.getTarget();
        if (cible == null) {
            return;
        }

        // Ne jamais attaquer son propriétaire
        if (cible.getUniqueId().equals(proprietaireAllie)) {
            event.setCancelled(true);
            return;
        }

        // Ne jamais attaquer un autre allié du même propriétaire
        UUID proprietaireCible = getProprietaire(cible);
        if (proprietaireCible != null && proprietaireCible.equals(proprietaireAllie)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMort(EntityDeathEvent event) {
        UUID proprietaire = getProprietaire(event.getEntity());
        if (proprietaire != null) {
            retirerAllie(proprietaire, event.getEntity().getUniqueId());
        }
    }

    @EventHandler
    public void onDeconnexion(PlayerQuitEvent event) {
        UUID proprietaire = event.getPlayer().getUniqueId();
        Set<UUID> allies = alliesParProprietaire.remove(proprietaire);
        if (allies == null) return;
        for (UUID id : allies) {
            Entity e = plugin.getServer().getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
    }

    public void nettoyerToutesLesAlliees() {
        for (Set<UUID> allies : alliesParProprietaire.values()) {
            for (UUID id : allies) {
                Entity e = plugin.getServer().getEntity(id);
                if (e != null) {
                    e.remove();
                }
            }
        }
        alliesParProprietaire.clear();
    }
}
