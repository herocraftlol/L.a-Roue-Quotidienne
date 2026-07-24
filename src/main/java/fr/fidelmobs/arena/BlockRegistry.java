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
