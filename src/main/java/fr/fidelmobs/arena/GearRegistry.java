package fr.fidelmobs.arena;

import fr.fidelmobs.Cles;
import fr.fidelmobs.mobs.MobRarity;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Random;

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

    public static ItemStack genererObjetAleatoire() {
        TypeEquipement[] types = TypeEquipement.values();
        TypeEquipement type = types[RANDOM.nextInt(types.length)];
        int tier = tirerTier();
        boolean enchante = RANDOM.nextDouble() < CHANCE_ENCHANTE;

        Material material = switch (type) {
            case CASQUE -> CASQUES[tier];
            case PLASTRON -> PLASTRONS[tier];
            case JAMBIERES -> JAMBIERES[tier];
            case BOTTES -> BOTTES[tier];
            case ARME -> EPEES[tier];
        };

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
        item.setItemMeta(meta);

        if (enchante) {
            List<Enchantment> pool = (type == TypeEquipement.ARME) ? ENCHANTS_ARME : ENCHANTS_ARMURE;
            int nbEnchants = 1 + RANDOM.nextInt(2); // 1 ou 2
            for (int i = 0; i < nbEnchants; i++) {
                Enchantment ench = pool.get(RANDOM.nextInt(pool.size()));
                int niveau = 1 + RANDOM.nextInt(ench.getMaxLevel());
                item.addUnsafeEnchantment(ench, niveau);
            }
        }

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
}
