package fr.fidelmobs.arena;

import fr.fidelmobs.mobs.MobRarity;
import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Blocs cubiques (pleins, 1x1x1) utilisables comme blocs de construction en arène,
 * classés par rareté d'obtention à la roue.
 */
public final class BlockRegistry {

    private static final Random RANDOM = new Random();
    private static final Map<Material, MobRarity> RARETE_PAR_BLOC = new LinkedHashMap<>();

    static {
        put(MobRarity.COMMUN, Material.DIRT, Material.COBBLESTONE, Material.ANDESITE, Material.OAK_PLANKS);
        put(MobRarity.PEU_COMMUN, Material.STONE_BRICKS, Material.MOSSY_COBBLESTONE, Material.SANDSTONE, Material.BRICKS);
        put(MobRarity.RARE, Material.IRON_BLOCK, Material.POLISHED_BLACKSTONE, Material.DEEPSLATE_BRICKS);
        put(MobRarity.EPIQUE, Material.GOLD_BLOCK, Material.DIAMOND_BLOCK, Material.PURPUR_BLOCK);
        put(MobRarity.LEGENDAIRE, Material.OBSIDIAN, Material.NETHERITE_BLOCK, Material.EMERALD_BLOCK);
    }

    private static void put(MobRarity rarete, Material... blocs) {
        for (Material m : blocs) {
            RARETE_PAR_BLOC.put(m, rarete);
        }
    }

    private BlockRegistry() {
    }

    public static MobRarity getRarete(Material m) {
        return RARETE_PAR_BLOC.getOrDefault(m, MobRarity.COMMUN);
    }

    public static boolean estAutorise(Material m) {
        return RARETE_PAR_BLOC.containsKey(m);
    }

    public static Material tirerBlocAleatoire() {
        int poidsTotal = 0;
        for (MobRarity r : MobRarity.values()) poidsTotal += r.getPoids();

        int tirage = RANDOM.nextInt(poidsTotal);
        final MobRarity[] rareteTiree = {MobRarity.COMMUN};
        int cumul = 0;
        for (MobRarity r : MobRarity.values()) {
            cumul += r.getPoids();
            if (tirage < cumul) {
                rareteTiree[0] = r;
                break;
            }
        }

        final MobRarity finalRarete = rareteTiree[0];
        List<Material> candidats = RARETE_PAR_BLOC.entrySet().stream()
                .filter(e -> e.getValue() == finalRarete)
                .map(Map.Entry::getKey)
                .toList();

        return candidats.get(RANDOM.nextInt(candidats.size()));
    }

    public static Material getBlocParDefaut() {
        return Material.COBBLESTONE;
    }

    public static Material parseNom(String nom) {
        try {
            Material m = Material.valueOf(nom.trim().toUpperCase().replace(" ", "_"));
            return estAutorise(m) ? m : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
