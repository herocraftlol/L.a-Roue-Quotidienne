package fr.fidelmobs.arena;

import fr.fidelmobs.Cles;
import fr.fidelmobs.mobs.MobRarity;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Génère des pièces d'armure et des épées aléatoires pour la roue, classées par rareté
 * selon leur tier (cuir < or < fer < diamant < netherite), avec une chance d'être enchantées
 * (auquel cas elles sont considérées un cran plus rares).
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

    private static final List<Enchantment> ENCHANTS_ARMURE = List.of(
            Enchantment.PROTECTION, Enchantment.UNBREAKING, Enchantment.THORNS
    );
    private static final List<Enchantment> ENCHANTS_ARME = List.of(
            Enchantment.SHARPNESS, Enchantment.KNOCKBACK, Enchantment.FIRE_ASPECT, Enchantment.UNBREAKING
    );

    private static final double CHANCE_ENCHANTE = 0.25;

    private GearRegistry() {
    }

    /**
     * Tire un tier (0=COMMUN ... 4=LEGENDAIRE) pondéré comme les autres raretés du plugin.
     */
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

    /**
     * Variante de {@link #tirerTier()} qui ne renvoie jamais un tier en dessous de
     * {@code minTierOrdinal}. Utilisé pour garantir au moins une récompense rare
     * parmi les catégories à chaque lancer de roue.
     */
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
        return construireItem(type, tier);
    }

    /**
     * Variante anti-doublons : ne tire jamais un type+tier de matériau déjà présent dans
     * {@code materiauxExclus} (la collection déjà possédée par le joueur). Essaie toutes les
     * combinaisons type+tier disponibles avant d'abandonner ; retourne {@code null} si TOUTES
     * les pièces possibles (au tier minimum demandé) sont déjà possédées (collection complète).
     */
    public static ItemStack genererObjetAleatoire(int minTierOrdinal, Set<Material> materiauxExclus) {
        List<TypeEquipement> typesMelanges = new ArrayList<>(List.of(TypeEquipement.values()));
        Collections.shuffle(typesMelanges, RANDOM);
        int min = Math.max(0, minTierOrdinal);
        MobRarity[] valeurs = MobRarity.values();

        for (TypeEquipement type : typesMelanges) {
            List<Integer> tiersDisponibles = new ArrayList<>();
            for (int t = min; t < valeurs.length; t++) {
                if (!materiauxExclus.contains(materialPour(type, t))) {
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
            return construireItem(type, tierChoisi);
        }

        return null; // toutes les combinaisons possibles (à ce tier minimum) sont déjà possédées
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

    private static ItemStack construireItem(TypeEquipement type, int tier) {
        boolean enchante = RANDOM.nextDouble() < CHANCE_ENCHANTE;

        // Le stuff de base (cuir/bois, tier COMMUN) ne doit jamais s'obtenir "tel quel" à la
        // roue : s'il est tiré sans enchantement, on force au moins un enchantement dessus.
        if (tier == 0 && !enchante) {
            enchante = true;
        }

        Material material = materialPour(type, tier);

        // Si enchanté, on monte d'un cran de rareté affichée (plafonné à LEGENDAIRE)
        int tierAffiche = enchante ? Math.min(tier + 1, MobRarity.values().length - 1) : tier;
        MobRarity rarete = MobRarity.values()[tierAffiche];

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_UNBREAKABLE, org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);

        String nomLisible = material.name().toLowerCase().replace('_', ' ');
        nomLisible = nomLisible.substring(0, 1).toUpperCase() + nomLisible.substring(1);
        meta.setDisplayName(rarete.getCouleur() + nomLisible + (enchante ? " §e★" : ""));

        meta.getPersistentDataContainer().set(Cles.RARETE, PersistentDataType.INTEGER, tierAffiche);
        meta.getPersistentDataContainer().set(Cles.ENCHANTE, PersistentDataType.INTEGER, enchante ? 1 : 0);

        // Les enchantements sont ajoutés directement sur CE MÊME objet meta, avant l'unique
        // appel à item.setItemMeta() ci-dessous. On évite ainsi de repasser par
        // item.addUnsafeEnchantment(...) après coup, qui refait un aller-retour meta
        // (getItemMeta -> ... -> setItemMeta) redondant et a pu, selon la version de
        // Paper/du serveur, ne pas se répercuter de façon fiable sur l'objet renvoyé.
        if (enchante) {
            List<Enchantment> pool = (type == TypeEquipement.ARME) ? ENCHANTS_ARME : ENCHANTS_ARMURE;
            List<Enchantment> dejaAppliques = new ArrayList<>();
            int nbEnchants = 1 + RANDOM.nextInt(2); // 1 ou 2, sans doublon d'enchantement
            for (int i = 0; i < nbEnchants && dejaAppliques.size() < pool.size(); i++) {
                Enchantment ench;
                do {
                    ench = pool.get(RANDOM.nextInt(pool.size()));
                } while (dejaAppliques.contains(ench));
                dejaAppliques.add(ench);
                int niveau = 1 + RANDOM.nextInt(ench.getMaxLevel());
                meta.addEnchant(ench, niveau, true);
            }
        }

        item.setItemMeta(meta);
        return item;
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

    public static int getRarete(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        Integer v = item.getItemMeta().getPersistentDataContainer().get(Cles.RARETE, PersistentDataType.INTEGER);
        return v == null ? 0 : v;
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

    private static String nomEnchant(Enchantment ench) {
        if (ench.equals(Enchantment.SHARPNESS)) return "Tranchant";
        if (ench.equals(Enchantment.KNOCKBACK)) return "Recul";
        if (ench.equals(Enchantment.FIRE_ASPECT)) return "Aspect du feu";
        if (ench.equals(Enchantment.UNBREAKING)) return "Solidité";
        if (ench.equals(Enchantment.PROTECTION)) return "Protection";
        if (ench.equals(Enchantment.THORNS)) return "Épines";
        String brut = ench.getKey().getKey().toLowerCase().replace('_', ' ');
        return brut.substring(0, 1).toUpperCase() + brut.substring(1);
    }

    private static String chiffreRomain(int niveau) {
        String[] romains = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return (niveau >= 0 && niveau < romains.length) ? romains[niveau] : String.valueOf(niveau);
    }
}
