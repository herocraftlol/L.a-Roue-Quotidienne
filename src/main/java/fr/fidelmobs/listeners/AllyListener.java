package fr.fidelmobs.listeners;

import fr.fidelmobs.LoyaltyMobsPlugin;
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
 * - ils ciblent en priorité les autres joueurs (et leurs alliés) à proximité
 * - ils sont nettoyés à la mort, à la déconnexion du propriétaire ou après une durée de vie max
 */
public class AllyListener implements Listener {

    public static final NamespacedKey CLE_PROPRIETAIRE = new NamespacedKey("fidelmobs", "ally_owner");

    private final LoyaltyMobsPlugin plugin;
    // propriétaire -> entités alliées actuellement en vie
    private final Map<UUID, Set<UUID>> alliesParProprietaire = new HashMap<>();

    public AllyListener(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
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

    private UUID getProprietaire(Entity entity) {
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
