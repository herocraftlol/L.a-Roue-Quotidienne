package fr.fidelmobs.arena;

import fr.fidelmobs.Cles;
import fr.fidelmobs.mobs.MobRarity;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Génère des pièces d'armure et des épées aléatoires pour la roue, classées par rareté
 * selon leur tier (cuir/bois < or < fer < diamant < netherite), avec une chance d'être
 * enchantées : soit une combinaison FAIBLE (un seul enchantement, niveau bas, +1 palier de
 * rareté), soit une combinaison FORTE (plusieurs enchantements à haut niveau, +2 paliers de
 * rareté). Un même matériau existe donc en jusqu'à 3 variantes collectionnables séparément
 * (brute / enchantée faible / enchantée forte), en plus du set de base bois+cuir toujours
 * disponible gratuitement (voir {@link #objetParDefaut}) — d'où une vraie panoplie
 * d'équipements par emplacement plutôt qu'un seul objet figé par matériau.
 */
public final class GearRegistry {

    private static final Random RANDOM = new Random();

    public enum TypeEquipement {
        CASQUE(EquipmentSlot.HEAD),
        PLASTRON(EquipmentSlot.CHEST),
        JAMBIERES(EquipmentSlot.LEGS),
        BOTTES(EquipmentSlot.FEET),
        ARME(EquipmentSlot.HAND);

        public final EquipmentSlot slot;

        TypeEquipement(EquipmentSlot slot) {
            this.slot = slot;
        }
    }

    /** Intensité de l'enchantement obtenu : aucune, une combinaison faible, ou une forte. */
    public enum NiveauEnchant {
        AUCUN, FAIBLE, FORT
    }

    // index correspondant à l'ordinal de MobRarity (COMMUN..LEGENDAIRE)
    private static final Material[] CASQUES = {
            Material.LEATHER_HELMET, Material.GOLDEN_HELMET, Material.IRON_HELMET, Material.DIAMOND_HELMET, Material.NETHERITE_HELMET
    };
    private static final Material[] PLASTRONS = {
            Material.LEATHER_CHESTPLATE, Material.GOLDEN_CHESTPLATE, Material.IRON_CHESTPLATE, Material.DIAMOND_CHESTPLATE, Material.NETHERITE_CHESTPLATE
    };
    private static final Material[] JAMBIERES = {
            Material.LEATHER_LEGGINGS, Material.GOLDEN_LEGGINGS, Material.IRON_LEGGINGS, Material.DIAMOND_LEGGINGS, Material.NETHERITE_LEGGINGS
    };
    private static final Material[] BOTTES = {
            Material.LEATHER_BOOTS, Material.GOLDEN_BOOTS, Material.IRON_BOOTS, Material.DIAMOND_BOOTS, Material.NETHERITE_BOOTS
    };
    private static final Material[] EPEES = {
            Material.WOODEN_SWORD, Material.GOLDEN_SWORD, Material.IRON_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD
    };
    // Dégâts de base (attaque à mains nues + arme, en cœurs) de chaque tier d'épée en vanilla,
    // affiché à titre indicatif dans le menu (voir decrireEffets).
    private static final double[] DEGATS_BASE_EPEE = {4.0, 4.0, 6.0, 7.0, 8.0};

    // Pool élargie : plus d'enchantements possibles = plus de combinaisons différentes à
    // obtenir, en plus de la gradation faible/forte.
    private static final List<Enchantment> ENCHANTS_ARMURE = List.of(
            Enchantment.PROTECTION, Enchantment.UNBREAKING, Enchantment.THORNS,
            Enchantment.BLAST_PROTECTION, Enchantment.PROJECTILE_PROTECTION, Enchantment.FIRE_PROTECTION
    );
    private static final List<Enchantment> ENCHANTS_ARME = List.of(
            Enchantment.SHARPNESS, Enchantment.KNOCKBACK, Enchantment.FIRE_ASPECT,
            Enchantment.UNBREAKING, Enchantment.SWEEPING_EDGE
    );

    // Couleur de teinte du cuir selon la rareté affichée de la pièce, pour un repère visuel
    // immédiat en plus du nom coloré (le cuir est le seul matériau réellement teintable).
    private static final Color[] COULEURS_CUIR = {
            Color.fromRGB(0x9E, 0x9E, 0x9E), // COMMUN : gris
            Color.fromRGB(0x55, 0xD1, 0x55), // PEU_COMMUN : vert
            Color.fromRGB(0x40, 0x80, 0xF0), // RARE : bleu
            Color.fromRGB(0xA5, 0x2B, 0xD6), // EPIQUE : violet
            Color.fromRGB(0xF5, 0xA6, 0x23), // LEGENDAIRE : or
    };

    private static final double CHANCE_FORT = 0.10;   // combinaison forte (plusieurs enchants, haut niveau)
    private static final double CHANCE_FAIBLE = 0.25; // combinaison faible (un seul enchant, bas niveau)

    private GearRegistry() {
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

    private static NiveauEnchant tirerNiveauEnchant() {
        double roll = RANDOM.nextDouble();
        if (roll < CHANCE_FORT) return NiveauEnchant.FORT;
        if (roll < CHANCE_FORT + CHANCE_FAIBLE) return NiveauEnchant.FAIBLE;
        return NiveauEnchant.AUCUN;
    }

    public static ItemStack genererObjetAleatoire() {
        return genererObjetAleatoire(0);
    }

    /**
     * Tire uniquement une rareté (sans construire d'objet), utilisé quand la collection
     * d'équipement est déjà complète à ce tier minimum : sert à dimensionner le bonus de
     * compensation dans la roue, sans jamais pouvoir donner un doublon réel.
     */
    public static MobRarity tirerRareteSeule(int minTierOrdinal) {
        int tier = minTierOrdinal > 0 ? tirerTier(minTierOrdinal) : tirerTier();
        return MobRarity.values()[tier];
    }

    public static ItemStack genererObjetAleatoire(int minTierOrdinal) {
        TypeEquipement[] types = TypeEquipement.values();
        TypeEquipement type = types[RANDOM.nextInt(types.length)];
        int tier = minTierOrdinal > 0 ? tirerTier(minTierOrdinal) : tirerTier();
        return construireItem(type, tier, tirerNiveauEnchant());
    }

    /**
     * Variante anti-doublons : ne tire jamais une combinaison (type, matériau, niveau
     * d'enchantement) déjà présente dans {@code signaturesExclues} — voir {@link #getSignature}.
     * Un même matériau peut donc être retiré jusqu'à 3 fois (brut, enchanté faible, enchanté
     * fort), chaque variante comptant comme un objet distinct dans la collection. Essaie
     * toutes les combinaisons disponibles avant d'abandonner ; retourne {@code null} si TOUT
     * (au tier minimum demandé) est déjà possédé (collection complète).
     */
    public static ItemStack genererObjetAleatoire(int minTierOrdinal, Set<String> signaturesExclues) {
        List<TypeEquipement> typesMelanges = new ArrayList<>(List.of(TypeEquipement.values()));
        Collections.shuffle(typesMelanges, RANDOM);
        int min = Math.max(0, minTierOrdinal);
        MobRarity[] valeurs = MobRarity.values();

        for (TypeEquipement type : typesMelanges) {
            List<Integer> tiersDisponibles = new ArrayList<>();
            for (int t = min; t < valeurs.length; t++) {
                if (!lesTroisNiveauxSontPossedes(type, t, signaturesExclues)) {
                    tiersDisponibles.add(t);
                }
            }
            if (tiersDisponibles.isEmpty()) continue; // ce type n'a plus rien de nouveau à offrir

            int poidsTotal = 0;
            for (int t : tiersDisponibles) poidsTotal += valeurs[t].getPoids();
            int tirage = RANDOM.nextInt(poidsTotal);
            int cumul = 0;
            int tierChoisi = tiersDisponibles.get(tiersDisponibles.size() - 1);
            for (int t : tiersDisponibles) {
                cumul += valeurs[t].getPoids();
                if (tirage < cumul) {
                    tierChoisi = t;
                    break;
                }
            }

            NiveauEnchant niveau = tirerNiveauNonPossede(type, tierChoisi, signaturesExclues);
            return construireItem(type, tierChoisi, niveau);
        }

        return null; // toutes les combinaisons possibles (à ce tier minimum) sont déjà possédées
    }

    private static boolean lesTroisNiveauxSontPossedes(TypeEquipement type, int tier, Set<String> signaturesExclues) {
        for (NiveauEnchant n : NiveauEnchant.values()) {
            if (!signaturesExclues.contains(signature(type, tier, n))) return false;
        }
        return true;
    }

    /** Respecte les probabilités normales, en réessayant si le résultat est déjà possédé. */
    private static NiveauEnchant tirerNiveauNonPossede(TypeEquipement type, int tier, Set<String> signaturesExclues) {
        for (int essai = 0; essai < 6; essai++) {
            NiveauEnchant candidat = tirerNiveauEnchant();
            if (!signaturesExclues.contains(signature(type, tier, candidat))) return candidat;
        }
        for (NiveauEnchant n : NiveauEnchant.values()) {
            if (!signaturesExclues.contains(signature(type, tier, n))) return n;
        }
        return NiveauEnchant.AUCUN; // ne devrait jamais arriver (appelant garantit qu'il en reste un)
    }

    private static String signature(TypeEquipement type, int tier, NiveauEnchant niveau) {
        return type.name() + ":" + tier + ":" + niveau.name();
    }

    private static Material materialPour(TypeEquipement type, int tier) {
        return switch (type) {
            case CASQUE -> CASQUES[tier];
            case PLASTRON -> PLASTRONS[tier];
            case JAMBIERES -> JAMBIERES[tier];
            case BOTTES -> BOTTES[tier];
            case ARME -> EPEES[tier];
        };
    }

    private static ItemStack construireItem(TypeEquipement type, int tier, NiveauEnchant niveau) {
        Material material = materialPour(type, tier);

        // Une combinaison faible monte d'un palier de rareté, une forte de deux (plafonné
        // à LÉGENDAIRE) : ça donne une vraie gradation entre objets peu et très puissants.
        int bumpRarete = switch (niveau) {
            case FORT -> 2;
            case FAIBLE -> 1;
            case AUCUN -> 0;
        };
        int tierAffiche = Math.min(tier + bumpRarete, MobRarity.values().length - 1);
        MobRarity rarete = MobRarity.values()[tierAffiche];

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);

        String nomLisible = material.name().toLowerCase().replace('_', ' ');
        nomLisible = nomLisible.substring(0, 1).toUpperCase() + nomLisible.substring(1);
        String marqueur = switch (niveau) {
            case FORT -> " §6★★";
            case FAIBLE -> " §e★";
            case AUCUN -> "";
        };
        meta.setDisplayName(rarete.getCouleur() + nomLisible + marqueur);

        meta.getPersistentDataContainer().set(Cles.RARETE, PersistentDataType.INTEGER, tierAffiche);
        meta.getPersistentDataContainer().set(Cles.ENCHANTE, PersistentDataType.INTEGER, niveau != NiveauEnchant.AUCUN ? 1 : 0);
        meta.getPersistentDataContainer().set(Cles.GEAR_NIVEAU_ENCHANT, PersistentDataType.INTEGER, niveau.ordinal());

        // Les enchantements sont ajoutés directement sur CE MÊME objet meta, avant l'unique
        // appel à item.setItemMeta() ci-dessous, pour être sûr qu'ils soient bien conservés.
        if (niveau != NiveauEnchant.AUCUN) {
            List<Enchantment> pool = (type == TypeEquipement.ARME) ? ENCHANTS_ARME : ENCHANTS_ARMURE;
            List<Enchantment> dejaAppliques = new ArrayList<>();
            // Faible : un seul enchantement, niveau bas. Fort : deux ou trois, niveau haut.
            int nbEnchants = niveau == NiveauEnchant.FORT ? 2 + RANDOM.nextInt(2) : 1;
            for (int i = 0; i < nbEnchants && dejaAppliques.size() < pool.size(); i++) {
                Enchantment ench;
                do {
                    ench = pool.get(RANDOM.nextInt(pool.size()));
                } while (dejaAppliques.contains(ench));
                dejaAppliques.add(ench);

                int max = ench.getMaxLevel();
                int niveauEnchant = niveau == NiveauEnchant.FORT
                        ? Math.max(1, max - RANDOM.nextInt(Math.max(1, (max + 1) / 2))) // moitié haute : combinaison forte
                        : 1; // combinaison faible : toujours le niveau minimum
                meta.addEnchant(ench, niveauEnchant, true);
            }
        }

        // Les pièces en cuir sont teintables : on leur donne une couleur liée à leur rareté
        // affichée, pour un repère visuel en plus du nom coloré.
        if (meta instanceof LeatherArmorMeta cuirMeta) {
            cuirMeta.setColor(COULEURS_CUIR[tierAffiche]);
        }

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Objet de base TOUJOURS disponible pour un type donné (épée en bois / pièce en cuir,
     * sans le moindre enchantement) : ne fait pas partie de la collection à débloquer, il
     * est utilisable et équipable à tout moment sans condition, comme un point de départ
     * permanent. Reconstruit à la volée (n'est jamais stocké dans la collection du joueur).
     */
    public static ItemStack objetParDefaut(TypeEquipement type) {
        return construireItem(type, 0, NiveauEnchant.AUCUN);
    }

    public static TypeEquipement getType(ItemStack item) {
        Material m = item.getType();
        for (Material c : CASQUES) if (c == m) return TypeEquipement.CASQUE;
        for (Material c : PLASTRONS) if (c == m) return TypeEquipement.PLASTRON;
        for (Material c : JAMBIERES) if (c == m) return TypeEquipement.JAMBIERES;
        for (Material c : BOTTES) if (c == m) return TypeEquipement.BOTTES;
        for (Material c : EPEES) if (c == m) return TypeEquipement.ARME;
        return null;
    }

    /** Rareté AFFICHÉE (matériau + bonus d'enchantement), utilisée pour le tri par puissance. */
    public static int getRarete(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        Integer v = item.getItemMeta().getPersistentDataContainer().get(Cles.RARETE, PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    /**
     * Tier RÉEL du matériau (0=bois/cuir ... 4=netherite), indépendant du bonus de rareté
     * apporté par l'enchantement. Retrouvé directement depuis le Material de l'objet, donc
     * fiable même si {@link #getRarete} a été gonflé par un enchantement fort.
     */
    public static int getTierMateriau(ItemStack item) {
        TypeEquipement type = getType(item);
        if (type == null) return 0;
        Material m = item.getType();
        Material[] tableau = switch (type) {
            case CASQUE -> CASQUES;
            case PLASTRON -> PLASTRONS;
            case JAMBIERES -> JAMBIERES;
            case BOTTES -> BOTTES;
            case ARME -> EPEES;
        };
        for (int i = 0; i < tableau.length; i++) {
            if (tableau[i] == m) return i;
        }
        return 0;
    }

    public static NiveauEnchant getNiveauEnchant(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return NiveauEnchant.AUCUN;
        Integer v = item.getItemMeta().getPersistentDataContainer().get(Cles.GEAR_NIVEAU_ENCHANT, PersistentDataType.INTEGER);
        if (v == null) return NiveauEnchant.AUCUN;
        NiveauEnchant[] valeurs = NiveauEnchant.values();
        return (v >= 0 && v < valeurs.length) ? valeurs[v] : NiveauEnchant.AUCUN;
    }

    /**
     * Identifiant unique (type + matériau + niveau d'enchantement) servant à l'anti-doublon
     * de la roue ET à regrouper l'affichage dans le menu d'équipement.
     */
    public static String getSignature(ItemStack item) {
        TypeEquipement type = getType(item);
        if (type == null) return "?";
        return signature(type, getTierMateriau(item), getNiveauEnchant(item));
    }

    public static Material getMaterialParDefaut(TypeEquipement type) {
        return switch (type) {
            case CASQUE -> Material.LEATHER_HELMET;
            case PLASTRON -> Material.LEATHER_CHESTPLATE;
            case JAMBIERES -> Material.LEATHER_LEGGINGS;
            case BOTTES -> Material.LEATHER_BOOTS;
            case ARME -> Material.WOODEN_SWORD;
        };
    }

    /**
     * Nombre total de combinaisons (type × matériau × niveau d'enchantement) obtenables,
     * utilisé par le système de défis pour les objectifs "collection complète".
     */
    public static int getNombreCombinaisonsTotal() {
        return TypeEquipement.values().length * MobRarity.values().length * NiveauEnchant.values().length;
    }

    /**
     * Formatte les enchantements d'un objet en une liste lisible ("Tranchant III, Solidité II"),
     * pour affichage dans /equipement liste. Retourne null si l'objet n'est pas enchanté.
     */
    public static String formatEnchantements(ItemStack item) {
        if (item == null || !item.hasItemMeta() || item.getItemMeta().getEnchants().isEmpty()) {
            return null;
        }
        return item.getItemMeta().getEnchants().entrySet().stream()
                .map(e -> nomEnchant(e.getKey()) + " " + chiffreRomain(e.getValue()))
                .collect(java.util.stream.Collectors.joining(", "));
    }

    /**
     * Description courte des dégâts (pour une arme) et des effets notables infligés par les
     * enchantements (mise à feu, recul, renvoi de dégâts...), pour affichage dans le menu
     * d'équipement — permet de comparer les pièces d'une même page "à l'œil" au-delà du seul
     * nom et de la rareté.
     */
    public static List<String> decrireEffets(ItemStack item) {
        List<String> lignes = new ArrayList<>();
        TypeEquipement type = getType(item);
        if (type == TypeEquipement.ARME) {
            int tier = getTierMateriau(item);
            double degats = DEGATS_BASE_EPEE[Math.max(0, Math.min(tier, DEGATS_BASE_EPEE.length - 1))];
            Integer tranchant = item.hasItemMeta() ? item.getItemMeta().getEnchants().get(Enchantment.SHARPNESS) : null;
            if (tranchant != null) degats += 1.25 * tranchant;
            lignes.add("§cDégâts de base : §f≈" + String.format(java.util.Locale.ROOT, "%.1f", degats) + " ♥");
        }
        if (item.hasItemMeta()) {
            for (Enchantment ench : item.getItemMeta().getEnchants().keySet()) {
                if (ench.equals(Enchantment.FIRE_ASPECT)) lignes.add("§6✦ Enflamme la cible touchée");
                else if (ench.equals(Enchantment.KNOCKBACK)) lignes.add("§6✦ Recul renforcé");
                else if (ench.equals(Enchantment.THORNS)) lignes.add("§6✦ Renvoie des dégâts à l'attaquant");
                else if (ench.equals(Enchantment.SWEEPING_EDGE)) lignes.add("§6✦ Dégâts de zone au coup balayé");
                else if (ench.equals(Enchantment.PROTECTION) || ench.equals(Enchantment.BLAST_PROTECTION)
                        || ench.equals(Enchantment.PROJECTILE_PROTECTION) || ench.equals(Enchantment.FIRE_PROTECTION)) {
                    lignes.add("§6✦ Réduit les dégâts subis");
                }
            }
        }
        return lignes;
    }

    private static String nomEnchant(Enchantment ench) {
        if (ench.equals(Enchantment.SHARPNESS)) return "Tranchant";
        if (ench.equals(Enchantment.KNOCKBACK)) return "Recul";
        if (ench.equals(Enchantment.FIRE_ASPECT)) return "Aspect du feu";
        if (ench.equals(Enchantment.UNBREAKING)) return "Solidité";
        if (ench.equals(Enchantment.PROTECTION)) return "Protection";
        if (ench.equals(Enchantment.THORNS)) return "Épines";
        if (ench.equals(Enchantment.BLAST_PROTECTION)) return "Protection contre l'explosion";
        if (ench.equals(Enchantment.PROJECTILE_PROTECTION)) return "Protection contre les projectiles";
        if (ench.equals(Enchantment.FIRE_PROTECTION)) return "Protection contre le feu";
        if (ench.equals(Enchantment.SWEEPING_EDGE)) return "Tranchant balayeur";
        String brut = ench.getKey().getKey().toLowerCase().replace('_', ' ');
        return brut.substring(0, 1).toUpperCase() + brut.substring(1);
    }

    private static String chiffreRomain(int niveau) {
        String[] romains = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return (niveau >= 0 && niveau < romains.length) ? romains[niveau] : String.valueOf(niveau);
    }
}
