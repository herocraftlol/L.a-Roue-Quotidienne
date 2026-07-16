package fr.fidelmobs.mobs;

import org.bukkit.entity.EntityType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Classement de tous les mobs "invocables" par rareté (grosso modo liée à leur puissance/dangerosité).
 * Les mobs les plus faibles sont communs, les boss et mobs puissants sont légendaires.
 */
public final class MobRegistry {

    private static final Random RANDOM = new Random();

    private static final Map<EntityType, MobRarity> RARETE_PAR_MOB = new LinkedHashMap<>();

    static {
        // Communs : mobs passifs / faibles
        put(MobRarity.COMMUN, EntityType.CHICKEN, EntityType.COW, EntityType.PIG, EntityType.SHEEP,
                EntityType.RABBIT, EntityType.SQUID, EntityType.BAT, EntityType.VILLAGER,
                EntityType.SPIDER, EntityType.ZOMBIE, EntityType.HUSK, EntityType.SILVERFISH);

        // Peu communs : un peu plus dangereux
        put(MobRarity.PEU_COMMUN, EntityType.SKELETON, EntityType.STRAY, EntityType.CAVE_SPIDER,
                EntityType.DROWNED, EntityType.CREEPER, EntityType.WOLF, EntityType.POLAR_BEAR,
                EntityType.PIGLIN, EntityType.ENDERMITE, EntityType.SLIME, EntityType.MAGMA_CUBE);

        // Rares : mobs plus techniques ou puissants
        put(MobRarity.RARE, EntityType.ENDERMAN, EntityType.WITCH, EntityType.PILLAGER,
                EntityType.VINDICATOR, EntityType.RAVAGER, EntityType.PIGLIN_BRUTE,
                EntityType.HOGLIN, EntityType.ZOGLIN, EntityType.BREEZE, EntityType.BOGGED);

        // Épiques : redoutables en combat
        put(MobRarity.EPIQUE, EntityType.EVOKER, EntityType.VEX, EntityType.PHANTOM,
                EntityType.GUARDIAN, EntityType.ELDER_GUARDIAN, EntityType.SHULKER,
                EntityType.WARDEN);

        // Légendaires : boss / mobs quasi uniques
        put(MobRarity.LEGENDAIRE, EntityType.WITHER_SKELETON, EntityType.IRON_GOLEM,
                EntityType.SNOW_GOLEM, EntityType.WITHER, EntityType.ENDER_DRAGON);
    }

    private static void put(MobRarity rarete, EntityType... types) {
        for (EntityType type : types) {
            RARETE_PAR_MOB.put(type, rarete);
        }
    }

    private MobRegistry() {
    }

    public static Map<EntityType, MobRarity> all() {
        return RARETE_PAR_MOB;
    }

    public static MobRarity getRarete(EntityType type) {
        return RARETE_PAR_MOB.getOrDefault(type, MobRarity.COMMUN);
    }

    /**
     * Tire un mob au hasard en respectant les poids de chaque rareté,
     * puis un mob au hasard parmi ceux de la rareté tirée.
     */
    public static EntityType tirerMobAleatoire() {
        int poidsTotal = 0;
        for (MobRarity r : MobRarity.values()) {
            poidsTotal += r.getPoids();
        }

        int tirage = RANDOM.nextInt(poidsTotal);
        int cumul = 0;
        MobRarity rareteTiree = MobRarity.COMMUN;
        for (MobRarity r : MobRarity.values()) {
            cumul += r.getPoids();
            if (tirage < cumul) {
                rareteTiree = r;
                break;
            }
        }

        final MobRarity finalRareteTiree = rareteTiree;
        List<EntityType> candidats = RARETE_PAR_MOB.entrySet().stream()
                .filter(e -> e.getValue() == finalRareteTiree)
                .map(Map.Entry::getKey)
                .toList();

        return candidats.get(RANDOM.nextInt(candidats.size()));
    }

    /**
     * Retrouve un EntityType à partir d'un nom saisi par le joueur (insensible à la casse,
     * accepte les underscores ou non).
     */
    public static EntityType parseNom(String nom) {
        String cle = nom.trim().toUpperCase().replace(" ", "_");
        try {
            EntityType type = EntityType.valueOf(cle);
            if (RARETE_PAR_MOB.containsKey(type)) {
                return type;
            }
        } catch (IllegalArgumentException ignored) {
            // pas trouvé
        }
        return null;
    }
}
