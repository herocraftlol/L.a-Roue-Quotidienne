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
        put(MobRarity.COMMUN, Material.DIRT, Material.COBBLESTONE, Material.ANDESITE, Material.OAK_PLANKS,
                Material.SPRUCE_PLANKS, Material.BIRCH_PLANKS, Material.JUNGLE_PLANKS, Material.ACACIA_PLANKS,
                Material.DARK_OAK_PLANKS, Material.MANGROVE_PLANKS, Material.CHERRY_PLANKS, Material.BAMBOO_PLANKS,
                Material.GRANITE, Material.DIORITE, Material.MOSS_BLOCK, Material.COARSE_DIRT,
                Material.MUD, Material.PODZOL, Material.TUFF, Material.SMOOTH_STONE);
        put(MobRarity.PEU_COMMUN, Material.STONE_BRICKS, Material.MOSSY_COBBLESTONE, Material.SANDSTONE, Material.BRICKS,
                Material.RED_SANDSTONE, Material.SMOOTH_SANDSTONE, Material.CHISELED_STONE_BRICKS, Material.MOSSY_STONE_BRICKS,
                Material.POLISHED_ANDESITE, Material.POLISHED_DIORITE, Material.POLISHED_GRANITE, Material.NETHER_BRICKS,
                Material.RED_NETHER_BRICKS, Material.PRISMARINE, Material.TERRACOTTA, Material.HONEYCOMB_BLOCK);
        put(MobRarity.RARE, Material.IRON_BLOCK, Material.POLISHED_BLACKSTONE, Material.DEEPSLATE_BRICKS,
                Material.COPPER_BLOCK, Material.WEATHERED_COPPER, Material.OXIDIZED_COPPER, Material.BLACKSTONE,
                Material.POLISHED_DEEPSLATE, Material.DEEPSLATE_TILES, Material.CHISELED_DEEPSLATE,
                Material.END_STONE, Material.END_STONE_BRICKS, Material.QUARTZ_BLOCK);
        put(MobRarity.EPIQUE, Material.GOLD_BLOCK, Material.DIAMOND_BLOCK, Material.PURPUR_BLOCK,
                Material.LAPIS_BLOCK, Material.REDSTONE_BLOCK, Material.COAL_BLOCK, Material.RAW_IRON_BLOCK,
                Material.RAW_GOLD_BLOCK, Material.RAW_COPPER_BLOCK, Material.PURPUR_PILLAR, Material.AMETHYST_BLOCK);
        put(MobRarity.LEGENDAIRE, Material.OBSIDIAN, Material.NETHERITE_BLOCK, Material.EMERALD_BLOCK,
                Material.CRYING_OBSIDIAN, Material.ANCIENT_DEBRIS, Material.SCULK, Material.SEA_LANTERN);
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

    /** Nombre total de blocs distincts obtenables, utilisé par le système de défis. */
    public static int getNombreTotal() {
        return RARETE_PAR_BLOC.size();
    }

    /**
     * Tire un bloc au hasard en excluant, tant que c'est possible, les blocs déjà débloqués
     * par le joueur : la roue ne renvoie un doublon que si la collection est déjà complète.
     * La pondération par rareté est recalculée sur les seuls paliers où il reste au moins un
     * bloc non débloqué, pour que le tirage reste cohérent même en fin de collection.
     */
    public static Material tirerBlocAleatoire(java.util.Set<Material> possedes) {
        Map<MobRarity, List<Material>> disponiblesParRarete = new java.util.EnumMap<>(MobRarity.class);
        int poidsTotal = 0;
        for (MobRarity r : MobRarity.values()) {
            List<Material> dispo = RARETE_PAR_BLOC.entrySet().stream()
                    .filter(e -> e.getValue() == r && !possedes.contains(e.getKey()))
                    .map(Map.Entry::getKey)
                    .toList();
            if (!dispo.isEmpty()) {
                disponiblesParRarete.put(r, dispo);
                poidsTotal += r.getPoids();
            }
        }

        if (disponiblesParRarete.isEmpty()) {
            // Collection déjà complète : on retombe sur un tirage classique, doublon inévitable.
            return tirerBlocAleatoire();
        }

        int tirage = RANDOM.nextInt(poidsTotal);
        int cumul = 0;
        for (Map.Entry<MobRarity, List<Material>> entree : disponiblesParRarete.entrySet()) {
            cumul += entree.getKey().getPoids();
            if (tirage < cumul) {
                List<Material> candidats = entree.getValue();
                return candidats.get(RANDOM.nextInt(candidats.size()));
            }
        }

        // Filet de sécurité (ne devrait pas arriver vu le cumul ci-dessus).
        List<Material> tousDispo = disponiblesParRarete.values().stream().flatMap(List::stream).toList();
        return tousDispo.get(RANDOM.nextInt(tousDispo.size()));
    }

    /**
     * Variante de {@link #tirerBlocAleatoire(java.util.Set)} qui ne renvoie jamais un bloc
     * en dessous de {@code minRareteOrdinal}. Utilisé pour garantir au moins une récompense
     * rare parmi les catégories à chaque lancer de roue.
     */
    public static Material tirerBlocAleatoire(java.util.Set<Material> possedes, int minRareteOrdinal) {
        MobRarity[] valeurs = MobRarity.values();
        int min = Math.max(0, Math.min(minRareteOrdinal, valeurs.length - 1));

        Map<MobRarity, List<Material>> disponiblesParRarete = new java.util.EnumMap<>(MobRarity.class);
        int poidsTotal = 0;
        for (int i = min; i < valeurs.length; i++) {
            MobRarity r = valeurs[i];
            List<Material> dispo = RARETE_PAR_BLOC.entrySet().stream()
                    .filter(e -> e.getValue() == r && !possedes.contains(e.getKey()))
                    .map(Map.Entry::getKey)
                    .toList();
            if (!dispo.isEmpty()) {
                disponiblesParRarete.put(r, dispo);
                poidsTotal += r.getPoids();
            }
        }

        if (disponiblesParRarete.isEmpty()) {
            // Plus aucun bloc non-possédé à ce palier de rareté ou au-dessus : on retombe
            // sur un tirage classique (doublon), toujours au moins au palier demandé.
            List<Material> tousAuMinimum = new java.util.ArrayList<>();
            for (int i = min; i < valeurs.length; i++) {
                for (Map.Entry<Material, MobRarity> e : RARETE_PAR_BLOC.entrySet()) {
                    if (e.getValue() == valeurs[i]) tousAuMinimum.add(e.getKey());
                }
            }
            if (tousAuMinimum.isEmpty()) return tirerBlocAleatoire(possedes);
            return tousAuMinimum.get(RANDOM.nextInt(tousAuMinimum.size()));
        }

        int tirage = RANDOM.nextInt(poidsTotal);
        int cumul = 0;
        for (Map.Entry<MobRarity, List<Material>> entree : disponiblesParRarete.entrySet()) {
            cumul += entree.getKey().getPoids();
            if (tirage < cumul) {
                List<Material> candidats = entree.getValue();
                return candidats.get(RANDOM.nextInt(candidats.size()));
            }
        }

        List<Material> tousDispo = disponiblesParRarete.values().stream().flatMap(List::stream).toList();
        return tousDispo.get(RANDOM.nextInt(tousDispo.size()));
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

        List<Material> candidats = RARETE_PAR_BLOC.entrySet().stream()
                .filter(e -> e.getValue() == rareteTiree[0])
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
