package fr.fidelmobs.defis;

import fr.fidelmobs.arena.ArrowRegistry;
import fr.fidelmobs.arena.BlockRegistry;
import fr.fidelmobs.arena.GearRegistry;
import fr.fidelmobs.arena.PowerRegistry;
import fr.fidelmobs.data.PlayerDataManager;
import fr.fidelmobs.mobs.MobRarity;
import fr.fidelmobs.mobs.MobRegistry;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.ToIntBiFunction;

/**
 * Catalogue complet des défis (achievements) du plugin : des défis GLOBAUX (progression
 * permanente, jamais réinitialisée) et un pool de défis QUOTIDIENS parmi lesquels un même
 * sous-ensemble est proposé à tout le monde chaque jour (tiré au sort de façon
 * déterministe à partir de la date — voir {@link #defisQuotidiensDuJour()} — donc pas
 * besoin de stocker le tirage du jour quelque part : tout le monde retombe sur les mêmes
 * défis en refaisant le même calcul).
 */
public final class DefiRegistry {

    private static final int NB_DEFIS_QUOTIDIENS_PAR_JOUR = 5;

    private DefiRegistry() {
    }

    // ---- Barème de récompense lié à la difficulté (rareté) du défi ----
    private static int pointsPour(MobRarity r) {
        return switch (r) {
            case COMMUN -> 10;
            case PEU_COMMUN -> 30;
            case RARE -> 75;
            case EPIQUE -> 175;
            case LEGENDAIRE -> 400;
        };
    }

    private static int ticketsPour(MobRarity r) {
        return switch (r) {
            case LEGENDAIRE -> 2;
            case EPIQUE -> 1;
            default -> 0;
        };
    }

    // ---- Petites fabriques pour raccourcir la déclaration des ~110 défis ci-dessous ----

    private static Defi creer(String id, String nom, String description, Material icone, MobRarity rarete,
                               int objectif, ToIntBiFunction<PlayerDataManager, UUID> progression) {
        return new Defi(id, nom, description, icone, rarete, objectif,
                pointsPour(rarete), ticketsPour(rarete), progression, null);
    }

    private static Defi creerTexte(String id, String nom, String description, Material icone, MobRarity rarete,
                                    int objectif, ToIntBiFunction<PlayerDataManager, UUID> progression,
                                    BiFunction<PlayerDataManager, UUID, String> texte) {
        return new Defi(id, nom, description, icone, rarete, objectif,
                pointsPour(rarete), ticketsPour(rarete), progression, texte);
    }

    private static ToIntBiFunction<PlayerDataManager, UUID> compteur(String cle) {
        return (d, u) -> d.getCompteur(u, cle);
    }

    private static ToIntBiFunction<PlayerDataManager, UUID> compteurQuotidien(String cle) {
        return (d, u) -> d.getCompteurQuotidien(u, cle);
    }

    // ============================================================================
    // DÉFIS GLOBAUX
    // ============================================================================

    private static final List<Defi> BASE = new ArrayList<>();

    static {
        // ---- Combat JcJ : nombre d'éliminations ----
        int[] seuilsKills = {1, 5, 10, 25, 50, 100, 200, 350, 500, 750, 1000};
        String[] nomsKills = {"Premier sang", "Chasseur débutant", "Chasseur confirmé", "Guerrier redouté",
                "Vétéran de l'arène", "Machine à tuer", "Fléau des joueurs", "Légende vivante",
                "Bourreau de l'arène", "Terreur absolue", "Mille victimes"};
        for (int i = 0; i < seuilsKills.length; i++) {
            MobRarity r = rareteSelonRang(i, seuilsKills.length);
            BASE.add(creer("kills_" + seuilsKills[i], nomsKills[i],
                    "Éliminer " + seuilsKills[i] + " joueur(s) au total dans l'arène PvP.",
                    Material.IRON_SWORD, r, seuilsKills[i], (d, u) -> d.getKills(u)));
        }

        // ---- Encaisser les coups (nombre de morts, sur le ton de l'humour) ----
        int[] seuilsMorts = {10, 50, 100};
        String[] nomsMorts = {"Ça arrive à tout le monde", "Sac de sable humain", "Increvable"};
        for (int i = 0; i < seuilsMorts.length; i++) {
            BASE.add(creer("morts_" + seuilsMorts[i], nomsMorts[i],
                    "Mourir " + seuilsMorts[i] + " fois dans l'arène (ça compte aussi !).",
                    Material.ROTTEN_FLESH, MobRarity.COMMUN, seuilsMorts[i], (d, u) -> d.getMorts(u)));
        }

        // ---- Meilleure série de kills sans mourir ----
        int[] seuilsSerie = {3, 5, 10, 15, 25};
        for (int i = 0; i < seuilsSerie.length; i++) {
            MobRarity r = rareteSelonRang(i, seuilsSerie.length);
            BASE.add(creer("serie_" + seuilsSerie[i], "Série de " + seuilsSerie[i],
                    "Éliminer " + seuilsSerie[i] + " joueurs d'affilée sans mourir entre-temps.",
                    Material.TOTEM_OF_UNDYING, r, seuilsSerie[i], compteur("meilleure_serie_kills")));
        }

        // ---- Ratio kills/morts ----
        double[] seuilsRatio = {1.0, 2.0, 3.0, 5.0};
        String[] nomsRatio = {"Équilibré", "Dominant", "Écrasant", "Intouchable"};
        for (int i = 0; i < seuilsRatio.length; i++) {
            double seuil = seuilsRatio[i];
            MobRarity r = rareteSelonRang(i, seuilsRatio.length + 1);
            BASE.add(creerTexte("ratio_" + (int) (seuil * 10), nomsRatio[i],
                    "Atteindre un ratio kills/morts d'au moins " + fmt(seuil) + ".",
                    Material.DIAMOND_SWORD, r, 1,
                    (d, u) -> d.getRatioKD(u) >= seuil ? 1 : 0,
                    (d, u) -> String.format("Ratio K/D actuel : %.2f (objectif %.2f)", d.getRatioKD(u), seuil)));
        }

        // ---- Connexions consécutives (streak quotidien du plugin) ----
        int[] seuilsStreak = {3, 7, 14, 30, 60, 100};
        for (int i = 0; i < seuilsStreak.length; i++) {
            MobRarity r = rareteSelonRang(i, seuilsStreak.length);
            BASE.add(creer("streak_" + seuilsStreak[i], "Fidèle " + seuilsStreak[i] + " jours",
                    "Se connecter " + seuilsStreak[i] + " jours d'affilée.",
                    Material.CLOCK, r, seuilsStreak[i], (d, u) -> d.getStreak(u)));
        }

        // ---- Utilisation de la roue de la fidélité ----
        int[] seuilsRoue = {1, 10, 25, 50, 100, 250, 500};
        for (int i = 0; i < seuilsRoue.length; i++) {
            MobRarity r = rareteSelonRang(i, seuilsRoue.length);
            BASE.add(creer("roue_" + seuilsRoue[i], "Tourneur de roue " + (i + 1),
                    "Utiliser la roue de la fidélité (/roue) " + seuilsRoue[i] + " fois.",
                    Material.SUNFLOWER, r, seuilsRoue[i], compteur("roue_utilisee")));
        }

        // ---- Points de fidélité gagnés au total (indépendant de ce qui a été dépensé) ----
        int[] seuilsPoints = {100, 500, 1500, 3000, 6000, 10000};
        for (int i = 0; i < seuilsPoints.length; i++) {
            MobRarity r = rareteSelonRang(i, seuilsPoints.length);
            BASE.add(creer("points_" + seuilsPoints[i], "Fortune " + (i + 1),
                    "Gagner " + seuilsPoints[i] + " points de fidélité au total (cumulés depuis toujours).",
                    Material.EMERALD, r, seuilsPoints[i], compteur("points_gagnes_total")));
        }

        // ---- Collection de mobs : diversité ----
        int totalMobs = MobRegistry.all().size();
        int[] seuilsMobsDistincts = {1, 5, 10, 20, 30, totalMobs};
        for (int i = 0; i < seuilsMobsDistincts.length; i++) {
            MobRarity r = (i == seuilsMobsDistincts.length - 1) ? MobRarity.LEGENDAIRE : rareteSelonRang(i, seuilsMobsDistincts.length - 1);
            String nom = (i == seuilsMobsDistincts.length - 1) ? "Ménagerie complète" : "Collectionneur de mobs " + (i + 1);
            BASE.add(creer("mobs_distincts_" + seuilsMobsDistincts[i], nom,
                    "Posséder " + seuilsMobsDistincts[i] + " mob(s) différent(s) dans sa collection (sur " + totalMobs + " au total).",
                    Material.SPAWNER, r, seuilsMobsDistincts[i], (d, u) -> d.getCollection(u).size()));
        }

        // ---- Collection de mobs : quantité totale possédée (toutes copies confondues) ----
        int[] seuilsMobsTotal = {10, 25, 50, 100};
        for (int i = 0; i < seuilsMobsTotal.length; i++) {
            MobRarity r = rareteSelonRang(i, seuilsMobsTotal.length);
            BASE.add(creer("mobs_total_" + seuilsMobsTotal[i], "Éleveur " + (i + 1),
                    "Posséder " + seuilsMobsTotal[i] + " mobs au total dans sa collection (copies incluses).",
                    Material.EGG, r, seuilsMobsTotal[i],
                    (d, u) -> d.getCollection(u).values().stream().mapToInt(Integer::intValue).sum()));
        }

        // ---- Collection d'équipement ----
        int totalGear = GearRegistry.getNombreCombinaisonsTotal();
        int[] seuilsGear = {1, 5, 10, 15, 20, totalGear};
        for (int i = 0; i < seuilsGear.length; i++) {
            MobRarity r = (i == seuilsGear.length - 1) ? MobRarity.LEGENDAIRE : rareteSelonRang(i, seuilsGear.length - 1);
            String nom = (i == seuilsGear.length - 1) ? "Arsenal complet" : "Arsenal grandissant " + (i + 1);
            BASE.add(creer("gear_" + seuilsGear[i], nom,
                    "Posséder " + seuilsGear[i] + " pièce(s) d'équipement/arme différente(s) (sur " + totalGear + " au total).",
                    Material.DIAMOND_CHESTPLATE, r, seuilsGear[i], (d, u) -> d.getEquipements(u).size()));
        }

        // ---- Collection de flèches ----
        int totalFleches = ArrowRegistry.getNombreModelesTotal();
        int[] seuilsFleches = {1, 3, 6, 9, 12, totalFleches};
        for (int i = 0; i < seuilsFleches.length; i++) {
            MobRarity r = (i == seuilsFleches.length - 1) ? MobRarity.LEGENDAIRE : rareteSelonRang(i, seuilsFleches.length - 1);
            String nom = (i == seuilsFleches.length - 1) ? "Carquois complet" : "Archer collectionneur " + (i + 1);
            BASE.add(creer("fleches_collection_" + seuilsFleches[i], nom,
                    "Posséder " + seuilsFleches[i] + " flèche(s) à effet différente(s) (sur " + totalFleches + " au total).",
                    Material.TIPPED_ARROW, r, seuilsFleches[i], (d, u) -> d.getFleches(u).size()));
        }

        // ---- Collection de pouvoirs ----
        int totalPouvoirs = PowerRegistry.getTous().size();
        int[] seuilsPouvoirs = {1, 3, 5, 7, totalPouvoirs};
        for (int i = 0; i < seuilsPouvoirs.length; i++) {
            MobRarity r = (i == seuilsPouvoirs.length - 1) ? MobRarity.LEGENDAIRE : rareteSelonRang(i, seuilsPouvoirs.length - 1);
            String nom = (i == seuilsPouvoirs.length - 1) ? "Maître de tous les pouvoirs" : "Apprenti sorcier " + (i + 1);
            BASE.add(creer("pouvoirs_collection_" + seuilsPouvoirs[i], nom,
                    "Posséder " + seuilsPouvoirs[i] + " pouvoir(s) spécial(aux) différent(s) (sur " + totalPouvoirs + " au total).",
                    Material.BLAZE_ROD, r, seuilsPouvoirs[i], (d, u) -> d.getPouvoirsPossedes(u).size()));
        }

        // ---- Collection de blocs de construction ----
        int totalBlocs = BlockRegistry.getNombreTotal();
        int[] seuilsBlocs = {1, 5, 10, totalBlocs};
        for (int i = 0; i < seuilsBlocs.length; i++) {
            MobRarity r = (i == seuilsBlocs.length - 1) ? MobRarity.LEGENDAIRE : rareteSelonRang(i, seuilsBlocs.length - 1);
            String nom = (i == seuilsBlocs.length - 1) ? "Architecte complet" : "Bâtisseur " + (i + 1);
            BASE.add(creer("blocs_collection_" + seuilsBlocs[i], nom,
                    "Débloquer " + seuilsBlocs[i] + " bloc(s) de construction différent(s) (sur " + totalBlocs + " au total).",
                    Material.BRICKS, r, seuilsBlocs[i], (d, u) -> d.getBlocsDebloques(u).size()));
        }

        // ---- Invocations d'alliés ----
        int[] seuilsInvocTotal = {1, 10, 25, 50, 100, 250};
        for (int i = 0; i < seuilsInvocTotal.length; i++) {
            MobRarity r = rareteSelonRang(i, seuilsInvocTotal.length);
            BASE.add(creer("invocations_" + seuilsInvocTotal[i], "Invocateur " + (i + 1),
                    "Invoquer " + seuilsInvocTotal[i] + " allié(s) au total dans l'arène.",
                    Material.NETHER_STAR, r, seuilsInvocTotal[i], compteur("invocations_totales")));
        }

        int[] seuilsInvocLegend = {1, 5, 10, 25};
        for (int i = 0; i < seuilsInvocLegend.length; i++) {
            MobRarity r = rareteSelonRang(i, seuilsInvocLegend.length, MobRarity.RARE);
            BASE.add(creer("invocations_legendaires_" + seuilsInvocLegend[i], "Dompteur de légendes " + (i + 1),
                    "Invoquer un mob légendaire " + seuilsInvocLegend[i] + " fois au total.",
                    Material.DRAGON_EGG, r, seuilsInvocLegend[i], compteur("invocations_legendaires")));
        }

        // ---- Invoquer des boss précis, au moins une fois ----
        BASE.add(creer("invoquer_dragon", "Cavalier du néant",
                "Invoquer un Ender Dragon comme allié.",
                Material.DRAGON_HEAD, MobRarity.LEGENDAIRE, 1, compteur("invocations_mob_" + EntityType.ENDER_DRAGON.name())));
        BASE.add(creer("invoquer_warden", "Écho des profondeurs",
                "Invoquer un Warden comme allié.",
                Material.SCULK_CATALYST, MobRarity.EPIQUE, 1, compteur("invocations_mob_" + EntityType.WARDEN.name())));
        BASE.add(creer("invoquer_wither", "Trois têtes valent mieux qu'une",
                "Invoquer un Wither comme allié.",
                Material.WITHER_SKELETON_SKULL, MobRarity.LEGENDAIRE, 1, compteur("invocations_mob_" + EntityType.WITHER.name())));
        BASE.add(creer("invoquer_golem_fer", "Garde du corps",
                "Invoquer un Golem de fer comme allié.",
                Material.IRON_BLOCK, MobRarity.RARE, 1, compteur("invocations_mob_" + EntityType.IRON_GOLEM.name())));
        BASE.add(creer("invoquer_golem_neige", "Bonhomme de neige loyal",
                "Invoquer un Golem de neige comme allié.",
                Material.SNOW_BLOCK, MobRarity.RARE, 1, compteur("invocations_mob_" + EntityType.SNOW_GOLEM.name())));

        // ---- Flèches tirées ----
        int[] seuilsTirs = {1, 25, 50, 100, 250, 500, 1000};
        for (int i = 0; i < seuilsTirs.length; i++) {
            MobRarity r = rareteSelonRang(i, seuilsTirs.length);
            BASE.add(creer("tirs_" + seuilsTirs[i], "Archer " + (i + 1),
                    "Tirer " + seuilsTirs[i] + " flèche(s) au total avec l'arc du kit.",
                    Material.BOW, r, seuilsTirs[i], compteur("fleches_tirees")));
        }

        // ---- Pouvoirs utilisés ----
        int[] seuilsPouvoirsUtil = {1, 10, 25, 50, 100};
        for (int i = 0; i < seuilsPouvoirsUtil.length; i++) {
            MobRarity r = rareteSelonRang(i, seuilsPouvoirsUtil.length);
            BASE.add(creer("pouvoirs_util_" + seuilsPouvoirsUtil[i], "Sorcier " + (i + 1),
                    "Activer un pouvoir spécial " + seuilsPouvoirsUtil[i] + " fois au total.",
                    Material.BLAZE_POWDER, r, seuilsPouvoirsUtil[i], compteur("pouvoirs_utilises")));
        }

        // ---- Blocs posés en arène ----
        int[] seuilsBlocsPoses = {1, 10, 50, 100, 250};
        for (int i = 0; i < seuilsBlocsPoses.length; i++) {
            MobRarity r = rareteSelonRang(i, seuilsBlocsPoses.length);
            BASE.add(creer("blocs_poses_" + seuilsBlocsPoses[i], "Bâtisseur d'arène " + (i + 1),
                    "Poser " + seuilsBlocsPoses[i] + " bloc(s) de construction au total en arène.",
                    Material.SCAFFOLDING, r, seuilsBlocsPoses[i], compteur("blocs_poses")));
        }

        // ---- Équipement remarquable ----
        BASE.add(creer("gear_combo_forte", "Forgé pour la guerre",
                "Posséder au moins une pièce d'équipement en combinaison ENCHANTÉE FORTE (★★, plusieurs enchantements à haut niveau).",
                Material.NETHERITE_SWORD, MobRarity.RARE, 1,
                (d, u) -> auMoinsUneCombinaisonForte(d.getEquipements(u)) ? 1 : 0));
        BASE.add(creer("gear_legendaire_enchante", "Relique légendaire",
                "Posséder une pièce d'équipement de rareté Légendaire ET enchantée.",
                Material.NETHERITE_CHESTPLATE, MobRarity.EPIQUE, 1,
                (d, u) -> auMoinsUneLegendaireEnchantee(d.getEquipements(u)) ? 1 : 0));
        BASE.add(creer("fleche_legendaire", "Trait ultime",
                "Posséder au moins une flèche à effet de rareté Légendaire.",
                Material.SPECTRAL_ARROW, MobRarity.RARE, 1,
                (d, u) -> d.getFleches(u).stream().anyMatch(i -> ArrowRegistry.getRarete(i) == MobRarity.LEGENDAIRE.ordinal()) ? 1 : 0));
        BASE.add(creer("set_legendaire_complet", "Champion légendaire",
                "Avoir les 5 emplacements d'équipement (casque, plastron, jambières, bottes, arme) équipés avec du matériel Légendaire en même temps.",
                Material.NETHERITE_HELMET, MobRarity.LEGENDAIRE, 1,
                (d, u) -> setLegendaireComplet(d, u) ? 1 : 0));

        // ---- Défis "méta" (compter les défis déjà accomplis) ----
        // Important : on fige un instantané de BASE ICI, avant d'y ajouter les défis méta
        // eux-mêmes. Si la fonction de progression référençait BASE directement, elle finirait
        // par se compter elle-même (et provoquerait une récursion infinie au moment de vérifier
        // sa propre complétion). L'instantané ne contient donc jamais les défis méta.
        List<Defi> defisNonMeta = List.copyOf(BASE);
        int[] seuilsMeta = {10, 25, 50, 90};
        for (int i = 0; i < seuilsMeta.length; i++) {
            int seuil = seuilsMeta[i];
            MobRarity r = rareteSelonRang(i, seuilsMeta.length);
            BASE.add(creer("meta_" + seuil, "Chasseur de défis " + (i + 1),
                    "Accomplir " + seuil + " autres défis (globaux) au total.",
                    Material.NETHER_STAR, r, seuil,
                    (d, u) -> (int) defisNonMeta.stream().filter(def -> def.estComplete(d, u)).count()));
        }
    }

    public static final List<Defi> GLOBAL = Collections.unmodifiableList(BASE);

    private static boolean auMoinsUneCombinaisonForte(List<ItemStack> equipements) {
        return equipements.stream().anyMatch(i -> i.hasItemMeta() && i.getItemMeta().getEnchants().size() >= 2);
    }

    private static boolean auMoinsUneLegendaireEnchantee(List<ItemStack> equipements) {
        return equipements.stream().anyMatch(i -> GearRegistry.getRarete(i) == MobRarity.LEGENDAIRE.ordinal()
                && i.hasItemMeta() && !i.getItemMeta().getEnchants().isEmpty());
    }

    private static boolean setLegendaireComplet(PlayerDataManager data, UUID uuid) {
        List<ItemStack> equipements = data.getEquipements(uuid);
        for (GearRegistry.TypeEquipement type : GearRegistry.TypeEquipement.values()) {
            int index = data.getIndexEquipe(uuid, type.slot);
            if (index < 0 || index >= equipements.size()) return false;
            if (GearRegistry.getRarete(equipements.get(index)) != MobRarity.LEGENDAIRE.ordinal()) return false;
        }
        return true;
    }

    /** Répartit une série de {@code nb} paliers sur l'échelle de rareté, du plus facile au plus dur. */
    private static MobRarity rareteSelonRang(int rang, int nb) {
        return rareteSelonRang(rang, nb, MobRarity.COMMUN);
    }

    private static MobRarity rareteSelonRang(int rang, int nb, MobRarity depart) {
        MobRarity[] valeurs = MobRarity.values();
        int min = depart.ordinal();
        int etendue = valeurs.length - min;
        int index = min + Math.min(etendue - 1, (rang * etendue) / Math.max(1, nb));
        return valeurs[index];
    }

    private static String fmt(double v) {
        return (v == Math.floor(v)) ? String.valueOf((int) v) : String.valueOf(v);
    }

    // ============================================================================
    // DÉFIS QUOTIDIENS
    // ============================================================================
    // Pool de gabarits parmi lesquels un même sous-ensemble est tiré au sort chaque jour,
    // de façon déterministe (voir defisQuotidiensDuJour), donc identique pour tout le
    // monde sans avoir besoin de le stocker côté serveur.

    private static final List<Defi> POOL_QUOTIDIEN = List.of(
            creer("q_kills_3", "Trois duels", "Éliminer 3 joueurs aujourd'hui.",
                    Material.STONE_SWORD, MobRarity.COMMUN, 3, compteurQuotidien("kills")),
            creer("q_kills_6", "Six duels", "Éliminer 6 joueurs aujourd'hui.",
                    Material.IRON_SWORD, MobRarity.PEU_COMMUN, 6, compteurQuotidien("kills")),
            creer("q_kills_10", "Dix duels", "Éliminer 10 joueurs aujourd'hui.",
                    Material.DIAMOND_SWORD, MobRarity.RARE, 10, compteurQuotidien("kills")),
            creer("q_kills_20", "Rampage du jour", "Éliminer 20 joueurs aujourd'hui.",
                    Material.NETHERITE_SWORD, MobRarity.EPIQUE, 20, compteurQuotidien("kills")),
            creer("q_roue_1", "Un tour de roue", "Utiliser /roue au moins 1 fois aujourd'hui.",
                    Material.SUNFLOWER, MobRarity.COMMUN, 1, compteurQuotidien("roue_utilisee")),
            creer("q_roue_3", "Trois tours de roue", "Utiliser /roue au moins 3 fois aujourd'hui.",
                    Material.CLOCK, MobRarity.PEU_COMMUN, 3, compteurQuotidien("roue_utilisee")),
            creer("q_roue_5", "Cinq tours de roue", "Utiliser /roue au moins 5 fois aujourd'hui.",
                    Material.GOLD_BLOCK, MobRarity.RARE, 5, compteurQuotidien("roue_utilisee")),
            creer("q_invoc_3", "Petite armée", "Invoquer 3 alliés aujourd'hui.",
                    Material.ZOMBIE_SPAWN_EGG, MobRarity.COMMUN, 3, compteurQuotidien("invocations_totales")),
            creer("q_invoc_8", "Armée du jour", "Invoquer 8 alliés aujourd'hui.",
                    Material.SKELETON_SPAWN_EGG, MobRarity.PEU_COMMUN, 8, compteurQuotidien("invocations_totales")),
            creer("q_invoc_15", "Horde quotidienne", "Invoquer 15 alliés aujourd'hui.",
                    Material.NETHER_STAR, MobRarity.RARE, 15, compteurQuotidien("invocations_totales")),
            creer("q_tirs_10", "Quelques flèches", "Tirer 10 flèches aujourd'hui.",
                    Material.ARROW, MobRarity.COMMUN, 10, compteurQuotidien("fleches_tirees")),
            creer("q_tirs_30", "Pluie de flèches", "Tirer 30 flèches aujourd'hui.",
                    Material.BOW, MobRarity.PEU_COMMUN, 30, compteurQuotidien("fleches_tirees")),
            creer("q_tirs_60", "Déluge de flèches", "Tirer 60 flèches aujourd'hui.",
                    Material.TIPPED_ARROW, MobRarity.RARE, 60, compteurQuotidien("fleches_tirees")),
            creer("q_pouvoirs_2", "Un peu de magie", "Utiliser 2 pouvoirs spéciaux aujourd'hui.",
                    Material.BLAZE_POWDER, MobRarity.COMMUN, 2, compteurQuotidien("pouvoirs_utilises")),
            creer("q_pouvoirs_5", "Sorcellerie du jour", "Utiliser 5 pouvoirs spéciaux aujourd'hui.",
                    Material.BLAZE_ROD, MobRarity.PEU_COMMUN, 5, compteurQuotidien("pouvoirs_utilises")),
            creer("q_pouvoirs_8", "Débordement de magie", "Utiliser 8 pouvoirs spéciaux aujourd'hui.",
                    Material.NETHER_STAR, MobRarity.RARE, 8, compteurQuotidien("pouvoirs_utilises")),
            creer("q_blocs_5", "Petit chantier", "Poser 5 blocs de construction aujourd'hui.",
                    Material.COBBLESTONE, MobRarity.COMMUN, 5, compteurQuotidien("blocs_poses")),
            creer("q_blocs_15", "Grand chantier", "Poser 15 blocs de construction aujourd'hui.",
                    Material.BRICKS, MobRarity.PEU_COMMUN, 15, compteurQuotidien("blocs_poses")),
            creer("q_blocs_25", "Chantier du siècle", "Poser 25 blocs de construction aujourd'hui.",
                    Material.OBSIDIAN, MobRarity.RARE, 25, compteurQuotidien("blocs_poses")),
            creer("q_points_50", "Petit bonus", "Gagner 50 points de fidélité aujourd'hui.",
                    Material.EMERALD, MobRarity.COMMUN, 50, compteurQuotidien("points_gagnes")),
            creer("q_points_150", "Bon bonus", "Gagner 150 points de fidélité aujourd'hui.",
                    Material.EMERALD_BLOCK, MobRarity.PEU_COMMUN, 150, compteurQuotidien("points_gagnes")),
            creer("q_points_300", "Jackpot du jour", "Gagner 300 points de fidélité aujourd'hui.",
                    Material.GOLD_BLOCK, MobRarity.RARE, 300, compteurQuotidien("points_gagnes")),
            creer("q_morts_1", "Ça arrive", "Mourir au moins 1 fois aujourd'hui (ça compte aussi !).",
                    Material.ROTTEN_FLESH, MobRarity.COMMUN, 1, compteurQuotidien("morts")),
            creer("q_kills_serie", "Sans faille aujourd'hui", "Éliminer 15 joueurs aujourd'hui.",
                    Material.NETHERITE_SWORD, MobRarity.EPIQUE, 15, compteurQuotidien("kills"))
    );

    /**
     * Sélectionne les défis quotidiens du jour, de façon déterministe : même date = même
     * sélection pour tout le monde, sans rien avoir à stocker côté serveur. Change
     * automatiquement à minuit (heure du serveur) puisque {@link LocalDate#now()} change.
     */
    public static List<Defi> defisQuotidiensDuJour() {
        long graine = LocalDate.now().toEpochDay();
        List<Defi> melange = new ArrayList<>(POOL_QUOTIDIEN);
        Collections.shuffle(melange, new Random(graine));
        int n = Math.min(NB_DEFIS_QUOTIDIENS_PAR_JOUR, melange.size());
        return Collections.unmodifiableList(melange.subList(0, n));
    }
}
