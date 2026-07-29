package fr.fidelmobs.listeners;

import fr.fidelmobs.LoyaltyMobsPlugin;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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

    // ---- Aura de soutien des mobs passifs (vache, mouton, cochon, lapin, poule, poulpe) ----
    // Ces mobs ne combattent jamais, mais boostent les AUTRES mobs invoqués du MÊME joueur
    // à proximité : plus on en possède d'un type donné (en vie dans l'arène), plus le bonus
    // qu'il procure est puissant (jusqu'à un plafond). N'affecte jamais les mobs adverses.
    private static final Set<EntityType> MOBS_SOUTIEN = EnumSet.of(
            EntityType.COW, EntityType.SHEEP, EntityType.PIG,
            EntityType.RABBIT, EntityType.CHICKEN, EntityType.SQUID
    );
    private static final double PORTEE_SOUTIEN = 16.0;
    private static final long INTERVALLE_SOUTIEN_TICKS = 40L; // toutes les 2s
    private static final int NIVEAU_SOUTIEN_MAX = 2; // amplificateur max (niveau III inclus)

    // ---- Pilotage custom du Ender Dragon ----
    // Hors de The End, le contrôleur vanilla du dragon dépend d'un EnderDragonBattle
    // (cristaux, portail de sortie...) qui n'existe pas ici : sans lui, le dragon reste
    // figé ou part en phase de "fuite" vers un portail inexistant et finit par disparaître
    // quasi aussitôt. On le maintient donc en phase HOVER (neutre, ne dépend pas du combat
    // d'End) et on pilote nous-mêmes sa position et ses attaques à chaque rafraîchissement.
    private static final double PORTEE_ATTAQUE_DRAGON = 6.0;
    private static final double VITESSE_DEPLACEMENT_DRAGON = 1.6; // blocs parcourus par rafraîchissement
    private static final long INTERVALLE_PILOTAGE_DRAGON_TICKS = 4L; // 0.2s : fluide sans spammer les téléportations
    private static final long COOLDOWN_ATTAQUE_DRAGON_MS = 1500L;
    private static final double DEGATS_MORSURE_DRAGON = 12.0;
    private final Map<UUID, Long> derniereAttaqueDragon = new HashMap<>();

    private final LoyaltyMobsPlugin plugin;
    // propriétaire -> entités alliées actuellement en vie
    private final Map<UUID, Set<UUID>> alliesParProprietaire = new HashMap<>();

    public AllyListener(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
        demarrerCiblageActif();
        demarrerSoutienPassif();
        demarrerPilotageDragon();
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
     * Tâche répétitive qui fait bénéficier chaque mob "de combat" d'un joueur des bonus
     * apportés par SES mobs de soutien (vache, mouton, cochon, lapin, poule, poulpe) à
     * proximité. Le bonus dépend du type de mob de soutien et s'intensifie avec le nombre
     * de mobs de ce type actuellement en vie dans l'armée du joueur (peu importe leur
     * position exacte pour le calcul du niveau, mais il faut être à portée pour en profiter).
     * Ne touche jamais aux mobs d'un autre joueur.
     */
    private void demarrerSoutienPassif() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (alliesParProprietaire.isEmpty()) return;
            for (Set<UUID> idsAllies : alliesParProprietaire.values()) {
                appliquerSoutienPour(idsAllies);
            }
        }, INTERVALLE_SOUTIEN_TICKS, INTERVALLE_SOUTIEN_TICKS);
    }

    private void appliquerSoutienPour(Set<UUID> idsAllies) {
        Map<EntityType, Integer> comptageSoutien = new EnumMap<>(EntityType.class);
        List<Mob> mobsSoutien = new ArrayList<>();
        List<Mob> mobsCombat = new ArrayList<>();

        for (UUID id : idsAllies) {
            Entity entite = plugin.getServer().getEntity(id);
            if (!(entite instanceof Mob mob) || mob.isDead() || !mob.isValid()) continue;

            if (MOBS_SOUTIEN.contains(mob.getType())) {
                mobsSoutien.add(mob);
                comptageSoutien.merge(mob.getType(), 1, Integer::sum);
            } else {
                mobsCombat.add(mob);
            }
        }

        if (mobsSoutien.isEmpty() || mobsCombat.isEmpty()) return;

        for (Mob soutien : mobsSoutien) {
            PotionEffect effet = effetSoutienPour(soutien.getType(), comptageSoutien.get(soutien.getType()));
            if (effet == null) continue;

            for (Mob combat : mobsCombat) {
                if (!combat.getWorld().equals(soutien.getWorld())) continue;
                if (combat.getLocation().distanceSquared(soutien.getLocation()) <= PORTEE_SOUTIEN * PORTEE_SOUTIEN) {
                    combat.addPotionEffect(effet);
                }
            }
        }
    }

    /**
     * Bonus apporté par UN type de mob de soutien, dont l'intensité (amplificateur) monte
     * avec le nombre de mobs de ce même type actuellement en vie (plafonné pour éviter les
     * dérives). Durée volontairement un peu plus longue que l'intervalle de rafraîchissement
     * pour ne jamais retomber à zéro entre deux passages de la tâche.
     */
    private PotionEffect effetSoutienPour(EntityType typeSoutien, int nombre) {
        int amplificateur = Math.min(nombre - 1, NIVEAU_SOUTIEN_MAX);
        int dureeTicks = (int) INTERVALLE_SOUTIEN_TICKS + 20;

        PotionEffectType type = switch (typeSoutien) {
            case COW -> PotionEffectType.REGENERATION;      // vache : vie
            case CHICKEN -> PotionEffectType.ABSORPTION;    // poule : cœurs bonus
            case SHEEP -> PotionEffectType.RESISTANCE;      // mouton : défense (laine)
            case PIG -> PotionEffectType.SPEED;             // cochon : vitesse
            case RABBIT -> PotionEffectType.JUMP_BOOST;     // lapin : agilité/saut
            case SQUID -> PotionEffectType.STRENGTH;        // poulpe : force
            default -> null;
        };
        if (type == null) return null;

        // ambiant + sans particules/icône : évite de polluer l'écran vu que ça se
        // réapplique en continu tant que le mob de soutien reste à portée.
        return new PotionEffect(type, dureeTicks, amplificateur, true, false, false);
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
     * Tâche répétitive qui pilote "à la main" chaque Ender Dragon allié : le contrôleur
     * vanilla du dragon (vol en cercle, charge, atterrissage sur le portail...) suppose un
     * combat d'End complet (cristaux + portail de sortie) qui n'existe pas quand il est
     * invoqué ailleurs, ce qui le laisse figé puis le fait disparaître très vite. On le
     * fige donc en phase HOVER (la seule qui ne dépend pas de ce combat) et on gère nous-
     * mêmes son déplacement (vers sa cible, ou en patrouille au-dessus de son propriétaire
     * s'il n'y a personne à combattre) ainsi que ses dégâts de morsure au contact.
     */
    private void demarrerPilotageDragon() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (alliesParProprietaire.isEmpty()) return;

            for (Map.Entry<UUID, Set<UUID>> entree : alliesParProprietaire.entrySet()) {
                UUID proprietaireId = entree.getKey();
                Player proprietaire = plugin.getServer().getPlayer(proprietaireId);

                for (UUID id : entree.getValue()) {
                    Entity entite = plugin.getServer().getEntity(id);
                    if (!(entite instanceof EnderDragon dragon) || dragon.isDead() || !dragon.isValid()) continue;
                    piloterDragon(dragon, proprietaireId, proprietaire);
                }
            }
        }, INTERVALLE_PILOTAGE_DRAGON_TICKS, INTERVALLE_PILOTAGE_DRAGON_TICKS);
    }

    private void piloterDragon(EnderDragon dragon, UUID proprietaireId, Player proprietaire) {
        if (dragon.getPhase() != EnderDragon.Phase.HOVER) {
            dragon.setPhase(EnderDragon.Phase.HOVER);
        }

        LivingEntity cible = trouverCibleEnnemieLaPlusProche(proprietaireId, dragon);
        Location origine = dragon.getLocation();
        Location destination;

        if (cible != null) {
            destination = cible.getLocation().clone().add(0, 2.0, 0);
        } else if (proprietaire != null && proprietaire.isOnline()
                && proprietaire.getWorld().equals(dragon.getWorld())) {
            // Pas d'adversaire en vue : patrouille en cercle au-dessus de son invocateur
            // plutôt que de rester planté sur place.
            double angle = (System.currentTimeMillis() / 1000.0) % (2 * Math.PI);
            Location autour = proprietaire.getLocation();
            destination = autour.clone().add(Math.cos(angle) * 8.0, 6.0, Math.sin(angle) * 8.0);
        } else {
            return;
        }

        Vector direction = destination.toVector().subtract(origine.toVector());
        double distance = direction.length();

        if (cible != null && distance <= PORTEE_ATTAQUE_DRAGON) {
            long maintenant = System.currentTimeMillis();
            long derniere = derniereAttaqueDragon.getOrDefault(dragon.getUniqueId(), 0L);
            if (maintenant - derniere >= COOLDOWN_ATTAQUE_DRAGON_MS) {
                derniereAttaqueDragon.put(dragon.getUniqueId(), maintenant);
                cible.damage(DEGATS_MORSURE_DRAGON, dragon);
                Vector recul = cible.getLocation().toVector().subtract(origine.toVector());
                if (recul.lengthSquared() > 0.0001) {
                    recul.normalize().multiply(0.8);
                }
                recul.setY(0.35);
                cible.setVelocity(cible.getVelocity().add(recul));
                dragon.getWorld().playSound(dragon.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 1f);
            }
            return; // reste à portée de sa cible sans foncer dedans
        }

        if (distance > 0.3) {
            Vector pas = direction.normalize().multiply(Math.min(VITESSE_DEPLACEMENT_DRAGON, distance));
            Location nouvelle = origine.clone().add(pas);
            nouvelle.setDirection(direction);
            dragon.teleport(nouvelle);
        }
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

    /**
     * Filet de sécurité en plus de {@link #onCiblage} : même si un mob allié parvenait
     * à obtenir/garder une cible interdite par un chemin qui ne passe pas par le ciblage
     * (dégâts de zone, riposte instantanée, projectile déjà en vol, etc.), on annule ici
     * tout dégât qu'il infligerait à son propriétaire ou à un autre allié du même
     * propriétaire. "En aucun cas" veut dire deux barrières indépendantes, pas une seule.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDegatsAllie(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victime)) {
            return;
        }

        Entity attaquantDirect = event.getDamager();
        Entity attaquant = attaquantDirect;
        if (attaquantDirect instanceof Projectile projectile) {
            ProjectileSource tireur = projectile.getShooter();
            if (tireur instanceof Entity tireurEntite) {
                attaquant = tireurEntite;
            }
        }

        UUID proprietaireAttaquant = getProprietaire(attaquant);
        if (proprietaireAttaquant == null) {
            return; // l'attaquant n'est pas (ou plus) un mob allié invoqué
        }

        if (!estCibleEnnemieValide(proprietaireAttaquant, victime)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMort(EntityDeathEvent event) {
        UUID proprietaire = getProprietaire(event.getEntity());
        if (proprietaire != null) {
            retirerAllie(proprietaire, event.getEntity().getUniqueId());
        }
        derniereAttaqueDragon.remove(event.getEntity().getUniqueId());
    }

    /**
     * Le Warden inflige normalement la Cécité ("Darkness") aux joueurs proches dès qu'il
     * détecte une cible — y compris son propre invocateur, ce qui rend l'écran illisible
     * pendant un combat censé être un soutien. On annule spécifiquement cet effet quand il
     * provient bien d'un Warden ; les autres sources de Cécité (potions, etc.) restent
     * inchangées.
     */
    @EventHandler(ignoreCancelled = true)
    public void onEffetWarden(EntityPotionEffectEvent event) {
        if (event.getCause() == EntityPotionEffectEvent.Cause.WARDEN
                && event.getNewEffect() != null
                && event.getNewEffect().getType() == PotionEffectType.DARKNESS) {
            event.setCancelled(true);
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
