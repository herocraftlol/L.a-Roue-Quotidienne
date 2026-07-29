package fr.fidelmobs.arena;

import fr.fidelmobs.Cles;
import fr.fidelmobs.LoyaltyMobsPlugin;
import fr.fidelmobs.mobs.MobRarity;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Pouvoirs spéciaux obtenus à la roue (catégorie "Pouvoir") : classés par rareté comme le
 * reste du plugin, ils s'équipent via le sélecteur dédié (avant-avant-avant-dernier slot de
 * la hotbar, 6e sur 9) et s'utilisent depuis le 5e slot. Chaque pouvoir a son propre temps
 * de recharge, indépendant des autres (voir {@link fr.fidelmobs.managers.PowerUseManager}).
 */
public final class PowerRegistry {

    private static final Random RANDOM = new Random();

    /** Effet réellement exécuté quand le joueur active son pouvoir équipé. */
    @FunctionalInterface
    public interface PowerEffect {
        void executer(LoyaltyMobsPlugin plugin, Player joueur);
    }

    public record PowerDefinition(String id, String nom, MobRarity rarete, Material icone,
                                   List<String> lore, PowerEffect effet) {
    }

    private static final List<PowerDefinition> POUVOIRS = new ArrayList<>();
    private static final Map<String, PowerDefinition> PAR_ID = new HashMap<>();
    private static final Map<MobRarity, List<PowerDefinition>> PAR_RARETE = new EnumMap<>(MobRarity.class);

    private static void enregistrer(String id, String nom, MobRarity rarete, Material icone,
                                     List<String> lore, PowerEffect effet) {
        PowerDefinition def = new PowerDefinition(id, nom, rarete, icone, lore, effet);
        POUVOIRS.add(def);
        PAR_ID.put(id, def);
        PAR_RARETE.computeIfAbsent(rarete, r -> new ArrayList<>()).add(def);
    }

    static {
        // ---- COMMUN : petits bonus utilitaires, sans impact direct sur un adversaire ----
        enregistrer("sprint_fulgurant", "Sprint fulgurant", MobRarity.COMMUN, Material.SUGAR,
                List.of("§7Vitesse III pendant 8 secondes."),
                (plugin, joueur) -> {
                    joueur.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 160, 2));
                    joueur.getWorld().playSound(joueur.getLocation(), Sound.ENTITY_HORSE_GALLOP, 0.7f, 1.4f);
                    joueur.getWorld().spawnParticle(Particle.CLOUD, joueur.getLocation(), 20, 0.4, 0.1, 0.4, 0.02);
                });

        enregistrer("carapace_ephemere", "Carapace éphémère", MobRarity.COMMUN, Material.SHIELD,
                List.of("§7Résistance II pendant 5 secondes."),
                (plugin, joueur) -> {
                    joueur.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 1));
                    joueur.getWorld().playSound(joueur.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 1.2f);
                    joueur.getWorld().spawnParticle(Particle.CRIT, joueur.getLocation().add(0, 1, 0), 25, 0.4, 0.6, 0.4, 0.1);
                });

        enregistrer("bond_agile", "Bond agile", MobRarity.COMMUN, Material.RABBIT_FOOT,
                List.of("§7Propulse vers l'avant et Saut III pendant 8 secondes."),
                (plugin, joueur) -> {
                    joueur.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 160, 2));
                    Vector direction = joueur.getLocation().getDirection().normalize().multiply(1.1).setY(0.6);
                    joueur.setVelocity(direction);
                    joueur.getWorld().playSound(joueur.getLocation(), Sound.ENTITY_RABBIT_JUMP, 1f, 0.8f);
                });

        // ---- PEU_COMMUN : petits effets de zone ou de soin ----
        enregistrer("cercle_de_flammes", "Cercle de flammes", MobRarity.PEU_COMMUN, Material.BLAZE_POWDER,
                List.of("§7Enflamme tous les ennemis dans un rayon de 5 blocs."),
                (plugin, joueur) -> {
                    joueur.getWorld().playSound(joueur.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1f, 0.8f);
                    joueur.getWorld().spawnParticle(Particle.FLAME, joueur.getLocation(), 80, 2.5, 0.3, 2.5, 0.02);
                    for (Entity proche : joueur.getNearbyEntities(5, 3, 5)) {
                        if (proche instanceof Player cible && !cible.equals(joueur) && estCiblable(cible)) {
                            cible.setFireTicks(100); // 5s
                        }
                    }
                });

        enregistrer("onde_de_recul", "Onde de recul", MobRarity.PEU_COMMUN, Material.PISTON,
                List.of("§7Repousse violemment tous les joueurs dans un rayon de 4 blocs."),
                (plugin, joueur) -> {
                    joueur.getWorld().playSound(joueur.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.5f);
                    joueur.getWorld().spawnParticle(Particle.SWEEP_ATTACK, joueur.getLocation().add(0, 1, 0), 6, 1, 0.2, 1, 0);
                    for (Entity proche : joueur.getNearbyEntities(4, 3, 4)) {
                        if (proche instanceof Player cible && !cible.equals(joueur) && estCiblable(cible)) {
                            Vector direction = cible.getLocation().toVector()
                                    .subtract(joueur.getLocation().toVector());
                            if (direction.lengthSquared() < 0.01) direction = new Vector(1, 0, 0);
                            direction = direction.normalize().multiply(1.6).setY(0.5);
                            cible.setVelocity(direction);
                        }
                    }
                });

        enregistrer("regeneration_vive", "Régénération vive", MobRarity.PEU_COMMUN, Material.GLISTERING_MELON_SLICE,
                List.of("§7Régénération II et Absorption I pendant 6 secondes."),
                (plugin, joueur) -> {
                    joueur.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 1));
                    joueur.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 120, 0));
                    joueur.getWorld().playSound(joueur.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
                    joueur.getWorld().spawnParticle(Particle.HEART, joueur.getLocation().add(0, 2, 0), 8, 0.4, 0.3, 0.4, 0);
                });

        // ---- RARE : nécessitent de viser un adversaire ----
        enregistrer("echange_spectral", "Échange spectral", MobRarity.RARE, Material.ENDER_PEARL,
                List.of("§7Échange ta position avec le joueur visé."),
                (plugin, joueur) -> {
                    Player cible = ciblerJoueur(joueur, 25);
                    if (cible == null) {
                        joueur.sendMessage("§cAucun joueur visé.");
                        return;
                    }
                    Location posJoueur = joueur.getLocation().clone();
                    Location posCible = cible.getLocation().clone();
                    joueur.teleport(posCible);
                    cible.teleport(posJoueur);
                    for (Player p : List.of(joueur, cible)) {
                        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                        p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0.3);
                    }
                });

        enregistrer("grappin_tenebreux", "Grappin ténébreux", MobRarity.RARE, Material.LEAD,
                List.of("§7Attire violemment le joueur visé vers toi."),
                (plugin, joueur) -> {
                    Player cible = ciblerJoueur(joueur, 20);
                    if (cible == null) {
                        joueur.sendMessage("§cAucun joueur visé.");
                        return;
                    }
                    Vector direction = joueur.getLocation().toVector().subtract(cible.getLocation().toVector());
                    direction = direction.normalize().multiply(1.8).setY(0.4);
                    cible.setVelocity(direction);
                    cible.getWorld().playSound(cible.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.6f, 1.4f);
                    cible.getWorld().spawnParticle(Particle.CRIT, cible.getLocation(), 30, 0.3, 0.3, 0.3, 0.1);
                });

        enregistrer("voile_dombre", "Voile d'ombre", MobRarity.RARE, Material.BLACK_DYE,
                List.of("§7Invisibilité et Vitesse I pendant 6 secondes."),
                (plugin, joueur) -> {
                    joueur.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 120, 0));
                    joueur.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 0));
                    joueur.getWorld().playSound(joueur.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 0.6f);
                    joueur.getWorld().spawnParticle(Particle.SMOKE, joueur.getLocation().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0.02);
                });

        // ---- EPIQUE : forte gêne/dégâts infligés à la cible visée ----
        enregistrer("levitation_forcee", "Lévitation forcée", MobRarity.EPIQUE, Material.PHANTOM_MEMBRANE,
                List.of("§7Fait léviter le joueur visé pendant 4 secondes."),
                (plugin, joueur) -> {
                    Player cible = ciblerJoueur(joueur, 20);
                    if (cible == null) {
                        joueur.sendMessage("§cAucun joueur visé.");
                        return;
                    }
                    cible.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 80, 1));
                    cible.getWorld().playSound(cible.getLocation(), Sound.ITEM_ELYTRA_FLYING, 0.8f, 1.6f);
                    cible.getWorld().spawnParticle(Particle.END_ROD, cible.getLocation(), 40, 0.3, 0.8, 0.3, 0.05);
                });

        enregistrer("foudre_ciblee", "Foudre ciblée", MobRarity.EPIQUE, Material.TRIDENT,
                List.of("§7Frappe la foudre sur le joueur visé (dégâts, pas de feu)."),
                (plugin, joueur) -> {
                    Player cible = ciblerJoueur(joueur, 25);
                    if (cible == null) {
                        joueur.sendMessage("§cAucun joueur visé.");
                        return;
                    }
                    World monde = cible.getWorld();
                    LightningStrike foudre = monde.strikeLightningEffect(cible.getLocation());
                    cible.damage(6.0, foudre);
                });

        enregistrer("entrave_glaciale", "Entrave glaciale", MobRarity.EPIQUE, Material.PACKED_ICE,
                List.of("§7Lenteur IV, Faiblesse II et Cécité pendant 4 secondes à la cible."),
                (plugin, joueur) -> {
                    Player cible = ciblerJoueur(joueur, 20);
                    if (cible == null) {
                        joueur.sendMessage("§cAucun joueur visé.");
                        return;
                    }
                    cible.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 3));
                    cible.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 1));
                    cible.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                    cible.getWorld().playSound(cible.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 0.6f);
                    cible.getWorld().spawnParticle(Particle.SNOWFLAKE, cible.getLocation().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.02);
                });

        // ---- LEGENDAIRE : effets dévastateurs, rares et spectaculaires ----
        enregistrer("pluie_de_meteores", "Pluie de météores", MobRarity.LEGENDAIRE, Material.FIRE_CHARGE,
                List.of("§7Fait pleuvoir des météores autour du joueur visé."),
                (plugin, joueur) -> {
                    Player cible = ciblerJoueur(joueur, 25);
                    Location centre = cible != null ? cible.getLocation() : joueur.getLocation();
                    World monde = centre.getWorld();
                    for (int i = 0; i < 6; i++) {
                        int delai = i * 4;
                        double dx = (RANDOM.nextDouble() - 0.5) * 6;
                        double dz = (RANDOM.nextDouble() - 0.5) * 6;
                        Location impact = centre.clone().add(dx, 0, dz);
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            monde.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
                            monde.spawnParticle(Particle.EXPLOSION, impact.clone().add(0, 1, 0), 1);
                            monde.spawnParticle(Particle.FLAME, impact.clone().add(0, 1, 0), 40, 1, 1, 1, 0.05);
                            for (Entity proche : monde.getNearbyEntities(impact, 2, 3, 2)) {
                                if (proche instanceof Player touche && estCiblable(touche)) {
                                    touche.damage(4.0);
                                    touche.setFireTicks(60);
                                }
                            }
                        }, delai);
                    }
                });

        enregistrer("frappe_spectrale", "Frappe spectrale", MobRarity.LEGENDAIRE, Material.NETHERITE_SWORD,
                List.of("§7Téléportation instantanée sur le joueur visé avec dégâts et recul."),
                (plugin, joueur) -> {
                    Player cible = ciblerJoueur(joueur, 30);
                    if (cible == null) {
                        joueur.sendMessage("§cAucun joueur visé.");
                        return;
                    }
                    Location destination = cible.getLocation().clone()
                            .subtract(cible.getLocation().getDirection().normalize().multiply(1.5));
                    destination.setDirection(cible.getLocation().subtract(destination).toVector());
                    joueur.teleport(destination);
                    cible.damage(7.0, joueur);
                    Vector recul = cible.getLocation().toVector().subtract(joueur.getLocation().toVector())
                            .normalize().multiply(1.4).setY(0.4);
                    cible.setVelocity(recul);
                    joueur.getWorld().playSound(joueur.getLocation(), Sound.ENTITY_ENDER_DRAGON_HURT, 0.8f, 1.3f);
                    joueur.getWorld().spawnParticle(Particle.SWEEP_ATTACK, cible.getLocation().add(0, 1, 0), 4, 0.3, 0.3, 0.3, 0);
                });

        enregistrer("singularite", "Singularité", MobRarity.LEGENDAIRE, Material.ENDER_EYE,
                List.of("§7Attire tous les ennemis proches (8 blocs) puis les propulse en l'air."),
                (plugin, joueur) -> {
                    Location centre = joueur.getLocation();
                    World monde = centre.getWorld();
                    monde.playSound(centre, Sound.ENTITY_WITHER_SHOOT, 1f, 0.6f);
                    monde.spawnParticle(Particle.PORTAL, centre.add(0, 1, 0), 100, 3, 2, 3, 0.5);
                    List<Player> attires = new ArrayList<>();
                    for (Entity proche : joueur.getNearbyEntities(8, 5, 8)) {
                        if (proche instanceof Player cible && !cible.equals(joueur) && estCiblable(cible)) {
                            Vector direction = joueur.getLocation().toVector().subtract(cible.getLocation().toVector());
                            direction = direction.normalize().multiply(1.3).setY(0.2);
                            cible.setVelocity(direction);
                            attires.add(cible);
                        }
                    }
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        monde.playSound(centre, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.8f);
                        monde.spawnParticle(Particle.EXPLOSION_EMITTER, centre, 1);
                        for (Player cible : attires) {
                            if (!cible.isOnline() || !estCiblable(cible)) continue;
                            cible.setVelocity(new Vector(0, 1.4, 0));
                            cible.damage(3.0);
                        }
                    }, 20L);
                });

        // ============================================================================
        // Pouvoirs supplémentaires (lot 2) : davantage de choix par rareté, et plusieurs
        // pouvoirs capables d'affecter aussi les mobs invoqués par un ADVERSAIRE (jamais
        // les alliés du lanceur lui-même) via ciblerVivant/mobAdverseLePlusProche.
        // ============================================================================

        // ---- COMMUN ----
        enregistrer("regard_predateur", "Regard prédateur", MobRarity.COMMUN, Material.SPYGLASS,
                List.of("§7Vision nocturne et Hâte II pendant 12 secondes."),
                (plugin, joueur) -> {
                    joueur.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 240, 0));
                    joueur.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 240, 1));
                    joueur.getWorld().playSound(joueur.getLocation(), Sound.ENTITY_CAT_PURR, 0.8f, 0.7f);
                });

        enregistrer("plume_de_chute", "Plume de chute", MobRarity.COMMUN, Material.FEATHER,
                List.of("§7Chute lente pendant 8 secondes."),
                (plugin, joueur) -> {
                    joueur.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 160, 0));
                    joueur.getWorld().playSound(joueur.getLocation(), Sound.ITEM_ELYTRA_FLYING, 0.5f, 1.6f);
                    joueur.getWorld().spawnParticle(Particle.CLOUD, joueur.getLocation(), 15, 0.3, 0.2, 0.3, 0.01);
                });

        enregistrer("second_souffle", "Second souffle", MobRarity.COMMUN, Material.GOLDEN_APPLE,
                List.of("§7Absorption I et Régénération I instantanées."),
                (plugin, joueur) -> {
                    joueur.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 0));
                    joueur.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 0));
                    joueur.getWorld().playSound(joueur.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.8f);
                });

        // ---- PEU_COMMUN ----
        enregistrer("toile_engluante", "Toile engluante", MobRarity.PEU_COMMUN, Material.COBWEB,
                List.of("§7Lenteur III (6s) sur le joueur ou mob adverse visé."),
                (plugin, joueur) -> {
                    LivingEntity cible = ciblerVivant(plugin, joueur, 15);
                    if (cible == null) {
                        joueur.sendMessage("§cAucune cible visée.");
                        return;
                    }
                    cible.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 120, 2));
                    cible.getWorld().playSound(cible.getLocation(), Sound.BLOCK_BAMBOO_BREAK, 1f, 0.7f);
                    cible.getWorld().spawnParticle(Particle.ITEM_SNOWBALL, cible.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0);
                });

        enregistrer("brasier_local", "Brasier local", MobRarity.PEU_COMMUN, Material.MAGMA_CREAM,
                List.of("§7Enflamme le joueur ou mob adverse visé pendant 4 secondes."),
                (plugin, joueur) -> {
                    LivingEntity cible = ciblerVivant(plugin, joueur, 15);
                    if (cible == null) {
                        joueur.sendMessage("§cAucune cible visée.");
                        return;
                    }
                    cible.setFireTicks(80);
                    cible.getWorld().playSound(cible.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1f, 1f);
                });

        enregistrer("bouclier_absorbant", "Bouclier absorbant", MobRarity.PEU_COMMUN, Material.TURTLE_HELMET,
                List.of("§7Résistance II et Absorption II pendant 6 secondes."),
                (plugin, joueur) -> {
                    joueur.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 120, 1));
                    joueur.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 120, 1));
                    joueur.getWorld().playSound(joueur.getLocation(), Sound.ITEM_ARMOR_EQUIP_TURTLE, 1f, 1f);
                });

        enregistrer("cri_de_guerre", "Cri de guerre", MobRarity.PEU_COMMUN, Material.GOAT_HORN,
                List.of("§7Force I et Vitesse I pendant 10 secondes."),
                (plugin, joueur) -> {
                    joueur.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 0));
                    joueur.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 0));
                    joueur.getWorld().playSound(joueur.getLocation(), Sound.EVENT_RAID_HORN, 1f, 1f);
                });

        // ---- RARE ----
        enregistrer("malediction", "Malédiction", MobRarity.RARE, Material.WITHER_ROSE,
                List.of("§7Faiblesse III et Lenteur II (6s) sur le joueur ou mob adverse visé."),
                (plugin, joueur) -> {
                    LivingEntity cible = ciblerVivant(plugin, joueur, 18);
                    if (cible == null) {
                        joueur.sendMessage("§cAucune cible visée.");
                        return;
                    }
                    cible.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 120, 2));
                    cible.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 120, 1));
                    cible.getWorld().playSound(cible.getLocation(), Sound.ENTITY_WITCH_CELEBRATE, 1f, 0.6f);
                    cible.getWorld().spawnParticle(Particle.WITCH, cible.getLocation().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0);
                });

        enregistrer("poison_virulent", "Poison virulent", MobRarity.RARE, Material.SPIDER_EYE,
                List.of("§7Poison III (8s) sur le joueur ou mob adverse visé."),
                (plugin, joueur) -> {
                    LivingEntity cible = ciblerVivant(plugin, joueur, 18);
                    if (cible == null) {
                        joueur.sendMessage("§cAucune cible visée.");
                        return;
                    }
                    cible.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 160, 2));
                    cible.getWorld().playSound(cible.getLocation(), Sound.ENTITY_WITCH_DRINK, 1f, 0.8f);
                    cible.getWorld().spawnParticle(Particle.SNEEZE, cible.getLocation().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0);
                });

        enregistrer("banissement", "Bannissement", MobRarity.RARE, Material.CHORUS_FRUIT,
                List.of("§7Téléporte au loin le joueur ou mob adverse visé."),
                (plugin, joueur) -> {
                    LivingEntity cible = ciblerVivant(plugin, joueur, 15);
                    if (cible == null) {
                        joueur.sendMessage("§cAucune cible visée.");
                        return;
                    }
                    Location base = cible.getLocation();
                    double angle = RANDOM.nextDouble() * Math.PI * 2;
                    Location destination = base.clone().add(Math.cos(angle) * 12, 0, Math.sin(angle) * 12);
                    destination.setY(base.getWorld().getHighestBlockYAt(destination) + 1);
                    cible.teleport(destination);
                    cible.getWorld().playSound(base, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.8f);
                    cible.getWorld().spawnParticle(Particle.PORTAL, base.add(0, 1, 0), 50, 0.4, 0.6, 0.4, 0.4);
                });

        enregistrer("chaine_electrique", "Chaîne électrique", MobRarity.RARE, Material.COPPER_INGOT,
                List.of("§7Foudre sur la cible visée puis sur tout adversaire (joueur ou mob) proche d'elle."),
                (plugin, joueur) -> {
                    LivingEntity cible = ciblerVivant(plugin, joueur, 20);
                    if (cible == null) {
                        joueur.sendMessage("§cAucune cible visée.");
                        return;
                    }
                    World monde = cible.getWorld();
                    monde.strikeLightningEffect(cible.getLocation());
                    cible.damage(4.0);
                    UUID lanceur = joueur.getUniqueId();
                    for (Entity proche : cible.getNearbyEntities(4, 3, 4)) {
                        if (proche instanceof LivingEntity autre && !autre.equals(joueur)
                                && estCibleValide(plugin, lanceur, autre)) {
                            monde.strikeLightningEffect(autre.getLocation());
                            autre.damage(3.0);
                        }
                    }
                });

        // ---- EPIQUE ----
        enregistrer("exorcisme", "Exorcisme", MobRarity.EPIQUE, Material.GOLDEN_SWORD,
                List.of("§7Dégâts lourds (10) au mob adverse le plus proche (jamais un joueur)."),
                (plugin, joueur) -> {
                    LivingEntity cible = mobAdverseLePlusProche(plugin, joueur, 12);
                    if (cible == null) {
                        joueur.sendMessage("§cAucun mob adverse à proximité.");
                        return;
                    }
                    cible.damage(10.0, joueur);
                    cible.getWorld().playSound(cible.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 0.8f);
                    cible.getWorld().spawnParticle(Particle.SOUL, cible.getLocation().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0.05);
                });

        enregistrer("peste", "Peste", MobRarity.EPIQUE, Material.FERMENTED_SPIDER_EYE,
                List.of("§7Nuage de Poison II (6s) touchant tous les adversaires (joueurs et mobs) à 5 blocs."),
                (plugin, joueur) -> {
                    UUID lanceur = joueur.getUniqueId();
                    joueur.getWorld().playSound(joueur.getLocation(), Sound.ENTITY_WITCH_THROW, 1f, 0.8f);
                    joueur.getWorld().spawnParticle(Particle.SNEEZE, joueur.getLocation(), 100, 3, 1, 3, 0.02);
                    for (Entity proche : joueur.getNearbyEntities(5, 3, 5)) {
                        if (proche instanceof LivingEntity cible && estCibleValide(plugin, lanceur, cible)) {
                            cible.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 120, 1));
                        }
                    }
                });

        enregistrer("gel_absolu", "Gel absolu", MobRarity.EPIQUE, Material.BLUE_ICE,
                List.of("§7Lenteur IV et Fatigue minière III (5s) sur le joueur ou mob adverse visé."),
                (plugin, joueur) -> {
                    LivingEntity cible = ciblerVivant(plugin, joueur, 20);
                    if (cible == null) {
                        joueur.sendMessage("§cAucune cible visée.");
                        return;
                    }
                    cible.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 3));
                    cible.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 100, 2));
                    cible.getWorld().playSound(cible.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 0.5f);
                    cible.getWorld().spawnParticle(Particle.SNOWFLAKE, cible.getLocation().add(0, 1, 0), 60, 0.4, 0.7, 0.4, 0.02);
                });

        enregistrer("vol_de_vie", "Vol de vie", MobRarity.EPIQUE, Material.NETHER_WART,
                List.of("§7Draine 5 PV au joueur ou mob adverse visé et te soigne d'autant."),
                (plugin, joueur) -> {
                    LivingEntity cible = ciblerVivant(plugin, joueur, 15);
                    if (cible == null) {
                        joueur.sendMessage("§cAucune cible visée.");
                        return;
                    }
                    cible.damage(5.0, joueur);
                    double plafondVie = joueur.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH) != null
                            ? joueur.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue() : 20.0;
                    joueur.setHealth(Math.min(plafondVie, joueur.getHealth() + 5.0));
                    joueur.getWorld().playSound(joueur.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1f, 0.7f);
                    cible.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, cible.getLocation().add(0, 1, 0), 15, 0.3, 0.4, 0.3, 0);
                });

        // ---- LEGENDAIRE ----
        enregistrer("chasse_aux_betes", "Chasse aux bêtes", MobRarity.LEGENDAIRE, Material.TRIDENT,
                List.of("§7Dégâts dévastateurs (20) au mob adverse le plus proche (jamais un joueur)."),
                (plugin, joueur) -> {
                    LivingEntity cible = mobAdverseLePlusProche(plugin, joueur, 16);
                    if (cible == null) {
                        joueur.sendMessage("§cAucun mob adverse à proximité.");
                        return;
                    }
                    cible.damage(20.0, joueur);
                    cible.getWorld().playSound(cible.getLocation(), Sound.ENTITY_WOLF_GROWL, 1.5f, 0.6f);
                    cible.getWorld().spawnParticle(Particle.SWEEP_ATTACK, cible.getLocation().add(0, 1, 0), 8, 0.4, 0.4, 0.4, 0);
                });

        enregistrer("cataclysme", "Cataclysme", MobRarity.LEGENDAIRE, Material.NETHER_STAR,
                List.of("§7Explosion massive : dégâts à tous les adversaires (joueurs et mobs) dans 7 blocs."),
                (plugin, joueur) -> {
                    UUID lanceur = joueur.getUniqueId();
                    Location centre = joueur.getLocation();
                    World monde = centre.getWorld();
                    monde.playSound(centre, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.6f);
                    monde.spawnParticle(Particle.EXPLOSION_EMITTER, centre, 2);
                    monde.spawnParticle(Particle.LAVA, centre, 60, 3, 1, 3, 0);
                    for (Entity proche : joueur.getNearbyEntities(7, 4, 7)) {
                        if (proche instanceof LivingEntity cible && estCibleValide(plugin, lanceur, cible)) {
                            cible.damage(9.0, joueur);
                            Vector recul = cible.getLocation().toVector().subtract(centre.toVector())
                                    .normalize().multiply(1.2).setY(0.5);
                            cible.setVelocity(recul);
                        }
                    }
                });

        enregistrer("jugement_dernier", "Jugement dernier", MobRarity.LEGENDAIRE, Material.END_CRYSTAL,
                List.of("§7Foudre sur chaque adversaire (joueur ou mob) visible dans un rayon de 20 blocs."),
                (plugin, joueur) -> {
                    UUID lanceur = joueur.getUniqueId();
                    World monde = joueur.getWorld();
                    monde.playSound(joueur.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1f, 0.7f);
                    int delai = 0;
                    for (Entity proche : joueur.getNearbyEntities(20, 10, 20)) {
                        if (!(proche instanceof LivingEntity cible) || !estCibleValide(plugin, lanceur, cible)) continue;
                        int d = delai;
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            if (cible.isDead()) return;
                            monde.strikeLightningEffect(cible.getLocation());
                            cible.damage(6.0, joueur);
                        }, d);
                        delai += 3;
                    }
                });

        enregistrer("ame_dechainee", "Âme déchaînée", MobRarity.LEGENDAIRE, Material.TOTEM_OF_UNDYING,
                List.of("§7Force III, Vitesse II, Résistance II et Régénération II pendant 10 secondes."),
                (plugin, joueur) -> {
                    joueur.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 2));
                    joueur.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1));
                    joueur.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 1));
                    joueur.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 1));
                    joueur.getWorld().playSound(joueur.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1f);
                    joueur.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, joueur.getLocation().add(0, 1, 0), 80, 0.4, 0.8, 0.4, 0.3);
                });
    }

    private PowerRegistry() {
    }

    private static boolean estCiblable(Player p) {
        return p.isOnline() && !p.isDead() && p.getHealth() > 0;
    }

    /**
     * Cherche le joueur visé en ligne de mire (portée donnée), en ignorant le lanceur
     * lui-même. Utilise une projection de rayon plutôt que l'ancienne API dépréciée
     * getTargetEntity.
     */
    private static Player ciblerJoueur(Player joueur, double distance) {
        RayTraceResult resultat = joueur.getWorld().rayTraceEntities(
                joueur.getEyeLocation(), joueur.getEyeLocation().getDirection(), distance, 0.4,
                entite -> entite instanceof Player p && !p.equals(joueur) && estCiblable(p));
        if (resultat == null || !(resultat.getHitEntity() instanceof Player cible)) {
            return null;
        }
        return cible;
    }

    /**
     * Comme {@link #ciblerJoueur}, mais peut aussi renvoyer un mob adverse (un mob invoqué
     * par un AUTRE joueur, jamais celui du lanceur lui-même) : utilisé par les pouvoirs
     * pertinents pour affecter aussi bien un adversaire que ses alliés invoqués.
     */
    private static LivingEntity ciblerVivant(LoyaltyMobsPlugin plugin, Player joueur, double distance) {
        UUID lanceur = joueur.getUniqueId();
        RayTraceResult resultat = joueur.getWorld().rayTraceEntities(
                joueur.getEyeLocation(), joueur.getEyeLocation().getDirection(), distance, 0.4,
                entite -> entite instanceof LivingEntity v && !v.equals(joueur) && estCibleValide(plugin, lanceur, v));
        if (resultat == null || !(resultat.getHitEntity() instanceof LivingEntity cible)) {
            return null;
        }
        return cible;
    }

    /** Un mob adverse le plus proche (jamais un allié du lanceur), dans un rayon donné. */
    private static LivingEntity mobAdverseLePlusProche(LoyaltyMobsPlugin plugin, Player joueur, double rayon) {
        UUID lanceur = joueur.getUniqueId();
        LivingEntity meilleur = null;
        double meilleureDistance = Double.MAX_VALUE;
        for (Entity proche : joueur.getNearbyEntities(rayon, rayon, rayon)) {
            if (!(proche instanceof LivingEntity vivant) || !(vivant instanceof Mob)) continue;
            UUID proprietaire = plugin.getAllyListener().getProprietaire(vivant);
            if (proprietaire == null || proprietaire.equals(lanceur)) continue; // ignore les mobs sauvages et ses propres alliés
            double distance = vivant.getLocation().distanceSquared(joueur.getLocation());
            if (distance < meilleureDistance) {
                meilleureDistance = distance;
                meilleur = vivant;
            }
        }
        return meilleur;
    }

    /**
     * Une cible est valide pour un pouvoir "offensif" si c'est un autre joueur en vie, OU un
     * mob dont le propriétaire n'est PAS le lanceur (mob adverse ou sauvage) : jamais un
     * allié invoqué par le lanceur lui-même.
     */
    private static boolean estCibleValide(LoyaltyMobsPlugin plugin, UUID lanceur, LivingEntity cible) {
        if (cible instanceof Player p) {
            return !p.getUniqueId().equals(lanceur) && estCiblable(p);
        }
        if (cible instanceof Mob) {
            UUID proprietaire = plugin.getAllyListener().getProprietaire(cible);
            return proprietaire == null || !proprietaire.equals(lanceur);
        }
        return false;
    }

    private static int tirerTier() {
        int poidsTotal = 0;
        for (MobRarity r : MobRarity.values()) poidsTotal += r.getPoids();
        int tirage = RANDOM.nextInt(poidsTotal);
        int cumul = 0;
        MobRarity[] valeurs = MobRarity.values();
        for (int i = 0; i < valeurs.length; i++) {
            cumul += valeurs[i].getPoids();
            if (tirage < cumul) return i;
        }
        return 0;
    }

    private static int tirerTier(int minTierOrdinal) {
        MobRarity[] valeurs = MobRarity.values();
        int min = Math.max(0, Math.min(minTierOrdinal, valeurs.length - 1));
        int poidsTotal = 0;
        for (int i = min; i < valeurs.length; i++) poidsTotal += valeurs[i].getPoids();
        if (poidsTotal <= 0) return min;
        int tirage = RANDOM.nextInt(poidsTotal);
        int cumul = 0;
        for (int i = min; i < valeurs.length; i++) {
            cumul += valeurs[i].getPoids();
            if (tirage < cumul) return i;
        }
        return min;
    }

    /**
     * Tire un tier pondéré (comme les autres catégories), puis choisit aléatoirement un
     * pouvoir parmi ceux de cette rareté. Si une rareté ne contient aucun pouvoir défini,
     * on retombe sur la rareté juste en dessous (ne devrait pas arriver avec la liste
     * actuelle, mais évite un crash si elle venait à être réduite).
     */
    public static PowerDefinition tirerPouvoirAleatoire() {
        return tirerPouvoirAleatoire(0);
    }

    public static PowerDefinition tirerPouvoirAleatoire(int minTierOrdinal) {
        MobRarity[] valeurs = MobRarity.values();
        int tier = minTierOrdinal > 0 ? tirerTier(minTierOrdinal) : tirerTier();
        for (int i = tier; i >= 0; i--) {
            List<PowerDefinition> liste = PAR_RARETE.get(valeurs[i]);
            if (liste != null && !liste.isEmpty()) {
                return liste.get(RANDOM.nextInt(liste.size()));
            }
        }
        return POUVOIRS.get(RANDOM.nextInt(POUVOIRS.size()));
    }

    public static PowerDefinition getParId(String id) {
        return PAR_ID.get(id);
    }

    public static List<PowerDefinition> getTous() {
        return List.copyOf(POUVOIRS);
    }

    /**
     * Icône affichée dans le menu de sélection (avec lore descriptif) et dans le
     * message de récompense de la roue.
     */
    public static ItemStack construireIcone(String id) {
        PowerDefinition def = PAR_ID.get(id);
        if (def == null) return new ItemStack(Material.BARRIER);

        ItemStack item = new ItemStack(def.icone());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(def.rarete().getCouleur() + "§l" + def.nom());
        List<String> lore = new ArrayList<>(def.lore());
        lore.add("");
        lore.add("§7Rareté : " + def.rarete().getCouleur() + def.rarete().getLabel());
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(Cles.POUVOIR_ID, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Item actif placé au 5e slot de la hotbar, représentant le pouvoir actuellement
     * équipé (celui réellement utilisé au clic droit).
     */
    public static ItemStack construireItemActif(String id) {
        PowerDefinition def = PAR_ID.get(id);
        ItemStack item = new ItemStack(def != null ? def.icone() : Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        String nom = def != null ? def.nom() : "Aucun pouvoir";
        String couleur = def != null ? def.rarete().getCouleur() : "§7";
        meta.setDisplayName(couleur + "§l✪ " + nom);
        List<String> lore = new ArrayList<>();
        if (def != null) lore.addAll(def.lore());
        lore.add("");
        lore.add("§7Clic droit pour activer.");
        lore.add("§7Recharge propre à ce pouvoir : 5 minutes par charge.");
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(Cles.POUVOIR_ACTIF, PersistentDataType.BYTE, (byte) 1);
        if (def != null) {
            meta.getPersistentDataContainer().set(Cles.POUVOIR_ID, PersistentDataType.STRING, id);
        }
        item.setItemMeta(meta);
        return item;
    }

    public static String getId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(Cles.POUVOIR_ID, PersistentDataType.STRING);
    }

    public static MobRarity getRarete(String id) {
        PowerDefinition def = PAR_ID.get(id);
        return def != null ? def.rarete() : MobRarity.COMMUN;
    }

    public static String getNom(String id) {
        PowerDefinition def = PAR_ID.get(id);
        return def != null ? def.nom() : "Pouvoir inconnu";
    }

    /** Description courte (première ligne de lore) utilisée pour les messages de récompense. */
    public static String decrireEffet(String id) {
        PowerDefinition def = PAR_ID.get(id);
        if (def == null || def.lore().isEmpty()) return null;
        return def.lore().get(0);
    }
}
