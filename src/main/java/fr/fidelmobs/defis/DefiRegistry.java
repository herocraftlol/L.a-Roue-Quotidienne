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
 *
 * Les noms de défis ne sont volontairement PAS thématiques ("Vétéran de l'arène", etc.) :
 * chaque nom énonce littéralement ce qu'il faut accomplir (ex. "Poser 50 blocs"), pour
 * qu'on sache d'un coup d'œil ce qui est demandé sans devoir lire la description.
 */
public final class DefiRegistry {

    private static final int NB_DEFIS_QUOTIDIENS_PAR_JOUR = 5;

    private DefiRegistry() {
    }

    // ---- Barème de récompense lié à la difficulté (rareté) du défi ----
    private static int pointsPour(MobRarity r) {
        // Barème revu à la baisse : les défis étaient devenus une source de points bien
        // plus rapide que le PvP lui-même, ce qui (combiné aux tickets ci-dessous) créait
        // une boucle "je tourne la roue → un défi se débloque → ça me donne de quoi
        // retourner la roue immédiatement". Ici pour ralentir la progression globale.
        return switch (r) {
            case COMMUN -> 6;
            case PEU_COMMUN -> 18;
            case RARE -> 40;
            case EPIQUE -> 90;
            case LEGENDAIRE -> 200;
        };
    }

    private static int ticketsPour(MobRarity r) {
        // Les tickets de roue ne doivent plus être une récompense courante des défis :
        // seuls les défis LÉGENDAIRES (les plus rares/difficiles) en donnent, et un seul à
        // la fois, pour casser la boucle roue → défi → ticket → roue.
        return r == MobRarity.LEGENDAIRE ? 1 : 0;
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
        for (int i = 0; i < seuilsKills.length; i++) {
            MobRarity r = rareteSelonRang(i, seuilsKills.length);
            int seuil = seuilsKills[i];
            BASE.add(creer("kills_" + seuil, "Éliminer " + seuil + " joueur" + pluriel(seuil),
                    "Éliminer " + seuil + " joueur(s) au total dans l'arène PvP.",
                    Material.IRON_SWORD, r, seuil, (d, u) -> d.getKills(u)));
        }

        // ---- Encaisser les coups (nombre de morts) ----
        int[] seuilsMorts = {10, 50, 100};
        for (int seuil : seuilsMorts) {
            BASE.add(creer("morts_" + seuil, "Mourir " + seuil + " fois",
                    "Mourir " + seuil + " fois dans l'arène (ça compte aussi !).",
                    Material.ROTTEN_FLESH, MobRarity.COMMUN, seuil, (d, u) -> d.getMorts(u)));
        }

        // ---- Meilleure série de kills sans mourir ----
        int[] seuilsSerie = {3, 5, 10, 15, 25};
        for (int i = 0; i < seuilsSerie.length; i++) {
            MobRarity r = rareteSelonRang(i, seuilsSerie.length);
            int seuil = seuilsSerie[i];
            BASE.add(creer("serie_" + seuil, "Éliminer " + seuil + " joueurs d'affilée sans mourir",
                    "Éliminer " + seuil + " joueurs d'affilée sans mourir entre-temps.",
                    Material.TOTEM_OF_UNDYING, r, seuil, compteur("meilleure_serie_kills")));
        }

        // ---- Ratio kills/morts ----
        double[] seuilsRatio = {1.0, 2.0, 3.0, 5.0};
        for (int i = 0; i < seuilsRatio.length; i++) {
            double seuil = seuilsRatio[i];
            MobRarity r = rareteSelonRang(i, seuilsRatio.length + 1);
            BASE.add(creerTexte("ratio_" + (int) (seuil * 10), "Atteindre un ratio K/D de " + fmt(seuil),
                    "Atteindre un ratio kills/morts d'au moins " + fmt(seuil) + ".",
                    Material.DIAMOND_SWORD, r, 1,
                    (d, u) -> d.getRatioKD(u) >= seuil ? 1 : 0,
                    (d, u) -> String.format("Ratio K/D actuel : %.2f (objectif %.2f)", d.getRatioKD(u), seuil)));
        }

        // ---- Connexions consécutives (streak quotidien du plugin) ----
        int[] seuilsStreak = {3, 7, 14, 30, 60, 100};
        for (int i = 0; i < seuilsStreak.length; i++) {
            MobRarity r = rareteSelonRang(i, seuilsStreak.length);
            int seuil = seuilsStreak[i];
            BASE.add(creer("streak_" + seuil, "Se connecter " + seuil + " jours d'affilée",
                    "Se connecter " + seuil + " jours d'affilée.",
                    Material.CLOCK, r, seuil, (d, u) -> d.getStreak(u)));
        }

        // ---- Utilisation de la roue de la fidélité ----
        int[] seuilsRoue = {1, 10, 25, 50, 100, 250, 500};
        for (int i = 0; i < seuilsRoue.length; i++) {
            MobRarity r = rareteSelonRang(i, seuilsRoue.length);
            int seuil = seuilsRoue[i];
            BASE.add(creer("roue_" + seuil, "Tourner la roue " + seuil + " fois",
                    "Utiliser la roue de la fidélité (/roue) " + seuil + " fois.",
                    Material.SUNFLOWER, r, seuil, compteur("roue_utilisee")));
        }

        // ---- Points de fidélité gagnés au total (indépendant de ce qui a été dépensé) ----
        int[] seuilsPoints = {100, 500, 1500, 3000, 6000, 10000};
        for (int i = 0; i < seuilsPoints.length; i++) {
            MobRarity r = rareteSelonRang(i, seuilsPoints.length);
            int seuil = seuilsPoints[i];
            BASE.add(creer("points_" + seuil, "Gagner " + seuil + " points au total",
                    "Gagner " + seuil + " points de fidélité au total (cumulés depuis toujours).",
                    Material.EMERALD, r, seuil, compteur("points_gagnes_total")));
        }

        // ---- Collection de mobs : diversité ----
        int totalMobs = MobRegistry.all().size();
        int[] seuilsMobsDistincts = {1, 5, 10, 20, 30, totalMobs};
        for (int i = 0; i < seuilsMobsDistincts.length; i++) {
            boolean complet = i == seuilsMobsDistincts.length - 1;
            MobRarity r = complet ? MobRarity.LEGENDAIRE : rareteSelonRang(i, seuilsMobsDistincts.length - 1);
            int seuil = seuilsMobsDistincts[i];
            String nom = complet ? "Posséder tous les mobs (" + totalMobs + ")" : "Posséder " + seuil + " mobs différents";
            BASE.add(creer("mobs_distincts_" + seuil, nom,
                    "Posséder " + seuil + " mob(s) différent(s) dans sa collection (sur " + totalMobs + " au total).",
                    Material.SPAWNER, r, seuil, (d, u) -> d.getCollection(u).size()));
        }

        // ---- Collection de mobs : quantité totale possédée (toutes copies confondues) ----
        int[] seuilsMobsTotal = {10, 25, 50, 100};
        for (int i = 0; i < seuilsMobsTotal.length; i++) {
            MobRarity r = rareteSelonRang(i, seuilsMobsTotal.length);
            int seuil = seuilsMobsTotal[i];
            BASE.add(creer("mobs_total_" + seuil, "Posséder " + seuil + " mobs au total",
                    "Posséder " + seuil + " mobs au total dans sa collection (copies incluses).",
                    Material.EGG, r, seuil,
                    (d, u) -> d.getCollection(u).values().stream().mapToInt(Integer::intValue).sum()));
        }

        // ---- Collection d'équipement ----
        int totalGear = GearRegistry.getNombreCombinaisonsTotal();
        int[] seuilsGear = {1, 5, 10, 15, 20, totalGear};
        for (int i = 0; i < seuilsGear.length; i++) {
            boolean complet = i == seuilsGear.length - 1;
            MobRarity r = complet ? MobRarity.LEGENDAIRE : rareteSelonRang(i, seuilsGear.length - 1);
            int seuil = seuilsGear[i];
            String nom = complet ? "Posséder tout l'équipement (" + totalGear + ")" : "Posséder " + seuil + " équipements différents";
            BASE.add(creer("gear_" + seuil, nom,
                    "Posséder " + seuil + " pièce(s) d'équipement/arme différente(s) (sur " + totalGear + " au total).",
                    Material.DIAMOND_CHESTPLATE, r, seuil, (d, u) -> d.getEquipements(u).size()));
        }

        // ---- Collection de flèches ----
        int totalFleches = ArrowRegistry.getNombreModelesTotal();
        int[] seuilsFleches = {1, 3, 6, 9, 12, totalFleches};
        for (int i = 0; i < seuilsFleches.length; i++) {
            boolean complet = i == seuilsFleches.length - 1;
            MobRarity r = complet ? MobRarity.LEGENDAIRE : rareteSelonRang(i, seuilsFleches.length - 1);
            int seuil = seuilsFleches[i];
            String nom = complet ? "Posséder toutes les flèches (" + totalFleches + ")" : "Posséder " + seuil + " flèches différentes";
            BASE.add(creer("fleches_collection_" + seuil, nom,
                    "Posséder " + seuil + " flèche(s) à effet différente(s) (sur " + totalFleches + " au total).",
                    Material.TIPPED_ARROW, r, seuil, (d, u) -> d.getFleches(u).size()));
        }

        // ---- Collection de pouvoirs ----
        int totalPouvoirs = PowerRegistry.getTous().size();
        int[] seuilsPouvoirs = {1, 3, 5, 7, totalPouvoirs};
        for (int i = 0; i < seuilsPouvoirs.length; i++) {
            boolean complet = i == seuilsPouvoirs.length - 1;
            MobRarity r = complet ? MobRarity.LEGENDAIRE : rareteSelonRang(i, seuilsPouvoirs.length - 1);
            int seuil = seuilsPouvoirs[i];
            String nom = complet ? "Posséder tous les pouvoirs (" + totalPouvoirs + ")" : "Posséder " + seuil + " pouvoirs différents";
            BASE.add(creer("pouvoirs_collection_" + seuil, nom,
                    "Posséder " + seuil + " pouvoir(s) spécial(aux) différent(s) (sur " + totalPouvoirs + " au total).",
                    Material.BLAZE_ROD, r, seuil, (d, u) -> d.getPouvoirsPossedes(u).size()));
        }

        // ---- Collection de blocs de construction ----
        int totalBlocs = BlockRegistry.getNombreTotal();
        int[] seuilsBlocs = {1, 5, 10, totalBlocs};
        for (int i = 0; i < seuilsBlocs.length; i++) {
            boolean complet = i == seuilsBlocs.length - 1;
            MobRarity r = complet ? MobRarity.LEGENDAIRE : rareteSelonRang(i, seuilsBlocs.length - 1);
            int seuil = seuilsBlocs[i];
            String nom = complet ? "Débloquer tous les blocs (" + totalBlocs + ")" : "Débloquer " + seuil + " blocs différents";
            BASE.add(creer("blocs_collection_" + seuil, nom,
                    "Débloquer " + seuil + " bloc(s) de construction différent(s) (sur " + totalBlocs + " au total).",
                    Material.BRICKS, r, seuil, (d, u) -> d.getBlocsDebloques(u).size()));
        }

        // ---- Invocations d'alliés ----
        int[] seuilsInvocTotal = {1, 10, 25, 50, 100, 250};
        for (int i = 0; i < seuilsInvocTotal.length; i++) {
            MobRarity r = rareteSelonRang(i, seuilsInvocTotal.length);
            int seuil = seuilsInvocTotal[i];
            BASE.add(creer("invocations_" + seuil, "Invoquer " + seuil + " alliés au total",
                    "Invoquer " + seuil + " allié(s) au total dans l'arène.",
                    Material.NETHER_STAR, r, seuil, compteur("invocations_totales")));
        }

        int[] seuilsInvocLegend = {1, 5, 10, 25};
        for (int i = 0; i < seuilsInvocLegend.length; i++) {
            MobRarity r = rareteSelonRang(i, seuilsInvocLegend.length, MobRarity.RARE);
            int seuil = seuilsInvocLegend[i];
            BASE.add(creer("invocations_legendaires_" + seuil, "Invoquer un mob légendaire " + seuil + " fois",
                    "Invoquer un mob légendaire " + seuil + " fois au total.",
                    Material.DRAGON_EGG, r, seuil, compteur("invocations_legendaires")));
        }

        // ---- Invoquer des boss précis, au moins une fois ----
        BASE.add(creer("invoquer_dragon", "Invoquer un Ender Dragon",
                "Invoquer un Ender Dragon comme allié.",
                Material.DRAGON_HEAD, MobRarity.LEGENDAIRE, 1, compteur("invocations_mob_" + EntityType.ENDER_DRAGON.name())));
        BASE.add(creer("invoquer_warden", "Invoquer un Warden",
                "Invoquer un Warden comme allié.",
                Material.SCULK_CATALYST, MobRarity.EPIQUE, 1, compteur("invocations_mob_" + EntityType.WARDEN.name())));
        BASE.add(creer("invoquer_wither", "Invoquer un Wither",
                "Invoquer un Wither comme allié.",
                Material.WITHER_SKELETON_SKULL, MobRarity.LEGENDAIRE, 1, compteur("invocations_mob_" + EntityType.WITHER.name())));
        BASE.add(creer("invoquer_golem_fer", "Invoquer un Golem de fer",
                "Invoquer un Golem de fer comme allié.",
                Material.IRON_BLOCK, MobRarity.RARE, 1, compteur("invocations_mob_" + EntityType.IRON_GOLEM.name())));
        BASE.add(creer("invoquer_golem_neige", "Invoquer un Golem de neige",
                "Invoquer un Golem de neige comme allié.",
                Material.SNOW_BLOCK, MobRarity.RARE, 1, compteur("invocations_mob_" + EntityType.SNOW_GOLEM.name())));

        // ---- Flèches tirées ----
        int[] seuilsTirs = {1, 25, 50, 100, 250, 500, 1000};
        for (int i = 0; i < seuilsTirs.length; i++) {
            MobRarity r = rareteSelonRang(i, seuilsTirs.length);
            int seuil = seuilsTirs[i];
            BASE.add(creer("tirs_" + seuil, "Tirer " + seuil + " flèches",
                    "Tirer " + seuil + " flèche(s) au total avec l'arc du kit.",
                    Material.BOW, r, seuil, compteur("fleches_tirees")));
        }

        // ---- Pouvoirs utilisés ----
        int[] seuilsPouvoirsUtil = {1, 10, 25, 50, 100};
        for (int i = 0; i < seuilsPouvoirsUtil.length; i++) {
            MobRarity r = rareteSelonRang(i, seuilsPouvoirsUtil.length);
            int seuil = seuilsPouvoirsUtil[i];
            BASE.add(creer("pouvoirs_util_" + seuil, "Utiliser un pouvoir " + seuil + " fois",
                    "Activer un pouvoir spécial " + seuil + " fois au total.",
                    Material.BLAZE_POWDER, r, seuil, compteur("pouvoirs_utilises")));
        }

        // ---- Blocs posés en arène ----
        int[] seuilsBlocsPoses = {1, 10, 50, 100, 250};
        for (int i = 0; i < seuilsBlocsPoses.length; i++) {
            MobRarity r = rareteSelonRang(i, seuilsBlocsPoses.length);
            int seuil = seuilsBlocsPoses[i];
            BASE.add(creer("blocs_poses_" + seuil, "Poser " + seuil + " blocs",
                    "Poser " + seuil + " bloc(s) de construction au total en arène.",
                    Material.SCAFFOLDING, r, seuil, compteur("blocs_poses")));
        }

        // ---- Équipement remarquable ----
        BASE.add(creer("gear_combo_forte", "Posséder un équipement enchanté fort (★★)",
                "Posséder au moins une pièce d'équipement en combinaison ENCHANTÉE FORTE (★★, plusieurs enchantements à haut niveau).",
                Material.NETHERITE_SWORD, MobRarity.RARE, 1,
                (d, u) -> auMoinsUneCombinaisonForte(d.getEquipements(u)) ? 1 : 0));
        BASE.add(creer("gear_legendaire_enchante", "Posséder un équipement légendaire enchanté",
                "Posséder une pièce d'équipement de rareté Légendaire ET enchantée.",
                Material.NETHERITE_CHESTPLATE, MobRarity.EPIQUE, 1,
                (d, u) -> auMoinsUneLegendaireEnchantee(d.getEquipements(u)) ? 1 : 0));
        BASE.add(creer("fleche_legendaire", "Posséder une flèche légendaire",
                "Posséder au moins une flèche à effet de rareté Légendaire.",
                Material.SPECTRAL_ARROW, MobRarity.RARE, 1,
                (d, u) -> d.getFleches(u).stream().anyMatch(i -> ArrowRegistry.getRarete(i) == MobRarity.LEGENDAIRE.ordinal()) ? 1 : 0));
        BASE.add(creer("set_legendaire_complet", "Équiper un set complet légendaire",
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
            BASE.add(creer("meta_" + seuil, "Accomplir " + seuil + " défis",
                    "Accomplir " + seuil + " autres défis (globaux) au total.",
                    Material.NETHER_STAR, r, seuil,
                    (d, u) -> (int) defisNonMeta.stream().filter(def -> def.estComplete(d, u)).count()));
        }
    }

    public static final List<Defi> GLOBAL = Collections.unmodifiableList(BASE);

    private static final java.util.Map<String, Defi> PAR_ID = new java.util.HashMap<>();

    static {
        for (Defi d : GLOBAL) PAR_ID.put(d.id(), d);
    }

    public static Defi parId(String id) {
        return PAR_ID.get(id);
    }

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

    /** "s" au-delà de 1, pour accorder "joueur(s)" dans les noms générés. */
    private static String pluriel(int n) {
        return n > 1 ? "s" : "";
    }

    // ============================================================================
    // DÉFIS QUOTIDIENS
    // ============================================================================
    // Pool de gabarits parmi lesquels un même sous-ensemble est tiré au sort chaque jour,
    // de façon déterministe (voir defisQuotidiensDuJour), donc identique pour tout le
    // monde sans avoir besoin de le stocker côté serveur.

    private static final List<Defi> POOL_QUOTIDIEN = List.of(
            creer("q_kills_3", "Éliminer 3 joueurs aujourd'hui", "Éliminer 3 joueurs aujourd'hui.",
                    Material.STONE_SWORD, MobRarity.COMMUN, 3, compteurQuotidien("kills")),
            creer("q_kills_6", "Éliminer 6 joueurs aujourd'hui", "Éliminer 6 joueurs aujourd'hui.",
                    Material.IRON_SWORD, MobRarity.PEU_COMMUN, 6, compteurQuotidien("kills")),
            creer("q_kills_10", "Éliminer 10 joueurs aujourd'hui", "Éliminer 10 joueurs aujourd'hui.",
                    Material.DIAMOND_SWORD, MobRarity.RARE, 10, compteurQuotidien("kills")),
            creer("q_kills_20", "Éliminer 20 joueurs aujourd'hui", "Éliminer 20 joueurs aujourd'hui.",
                    Material.NETHERITE_SWORD, MobRarity.EPIQUE, 20, compteurQuotidien("kills")),
            creer("q_roue_1", "Utiliser /roue 1 fois aujourd'hui", "Utiliser /roue au moins 1 fois aujourd'hui.",
                    Material.SUNFLOWER, MobRarity.COMMUN, 1, compteurQuotidien("roue_utilisee")),
            creer("q_roue_3", "Utiliser /roue 3 fois aujourd'hui", "Utiliser /roue au moins 3 fois aujourd'hui.",
                    Material.CLOCK, MobRarity.PEU_COMMUN, 3, compteurQuotidien("roue_utilisee")),
            creer("q_roue_5", "Utiliser /roue 5 fois aujourd'hui", "Utiliser /roue au moins 5 fois aujourd'hui.",
                    Material.GOLD_BLOCK, MobRarity.RARE, 5, compteurQuotidien("roue_utilisee")),
            creer("q_invoc_3", "Invoquer 3 alliés aujourd'hui", "Invoquer 3 alliés aujourd'hui.",
                    Material.ZOMBIE_SPAWN_EGG, MobRarity.COMMUN, 3, compteurQuotidien("invocations_totales")),
            creer("q_invoc_8", "Invoquer 8 alliés aujourd'hui", "Invoquer 8 alliés aujourd'hui.",
                    Material.SKELETON_SPAWN_EGG, MobRarity.PEU_COMMUN, 8, compteurQuotidien("invocations_totales")),
            creer("q_invoc_15", "Invoquer 15 alliés aujourd'hui", "Invoquer 15 alliés aujourd'hui.",
                    Material.NETHER_STAR, MobRarity.RARE, 15, compteurQuotidien("invocations_totales")),
            creer("q_tirs_10", "Tirer 10 flèches aujourd'hui", "Tirer 10 flèches aujourd'hui.",
                    Material.ARROW, MobRarity.COMMUN, 10, compteurQuotidien("fleches_tirees")),
            creer("q_tirs_30", "Tirer 30 flèches aujourd'hui", "Tirer 30 flèches aujourd'hui.",
                    Material.BOW, MobRarity.PEU_COMMUN, 30, compteurQuotidien("fleches_tirees")),
            creer("q_tirs_60", "Tirer 60 flèches aujourd'hui", "Tirer 60 flèches aujourd'hui.",
                    Material.TIPPED_ARROW, MobRarity.RARE, 60, compteurQuotidien("fleches_tirees")),
            creer("q_pouvoirs_2", "Utiliser 2 pouvoirs aujourd'hui", "Utiliser 2 pouvoirs spéciaux aujourd'hui.",
                    Material.BLAZE_POWDER, MobRarity.COMMUN, 2, compteurQuotidien("pouvoirs_utilises")),
            creer("q_pouvoirs_5", "Utiliser 5 pouvoirs aujourd'hui", "Utiliser 5 pouvoirs spéciaux aujourd'hui.",
                    Material.BLAZE_ROD, MobRarity.PEU_COMMUN, 5, compteurQuotidien("pouvoirs_utilises")),
            creer("q_pouvoirs_8", "Utiliser 8 pouvoirs aujourd'hui", "Utiliser 8 pouvoirs spéciaux aujourd'hui.",
                    Material.NETHER_STAR, MobRarity.RARE, 8, compteurQuotidien("pouvoirs_utilises")),
            creer("q_blocs_5", "Poser 5 blocs aujourd'hui", "Poser 5 blocs de construction aujourd'hui.",
                    Material.COBBLESTONE, MobRarity.COMMUN, 5, compteurQuotidien("blocs_poses")),
            creer("q_blocs_15", "Poser 15 blocs aujourd'hui", "Poser 15 blocs de construction aujourd'hui.",
                    Material.BRICKS, MobRarity.PEU_COMMUN, 15, compteurQuotidien("blocs_poses")),
            creer("q_blocs_25", "Poser 25 blocs aujourd'hui", "Poser 25 blocs de construction aujourd'hui.",
                    Material.OBSIDIAN, MobRarity.RARE, 25, compteurQuotidien("blocs_poses")),
            creer("q_points_50", "Gagner 50 points aujourd'hui", "Gagner 50 points de fidélité aujourd'hui.",
                    Material.EMERALD, MobRarity.COMMUN, 50, compteurQuotidien("points_gagnes")),
            creer("q_points_150", "Gagner 150 points aujourd'hui", "Gagner 150 points de fidélité aujourd'hui.",
                    Material.EMERALD_BLOCK, MobRarity.PEU_COMMUN, 150, compteurQuotidien("points_gagnes")),
            creer("q_points_300", "Gagner 300 points aujourd'hui", "Gagner 300 points de fidélité aujourd'hui.",
                    Material.GOLD_BLOCK, MobRarity.RARE, 300, compteurQuotidien("points_gagnes")),
            creer("q_morts_1", "Mourir 1 fois aujourd'hui", "Mourir au moins 1 fois aujourd'hui (ça compte aussi !).",
                    Material.ROTTEN_FLESH, MobRarity.COMMUN, 1, compteurQuotidien("morts")),
            creer("q_kills_serie", "Éliminer 15 joueurs aujourd'hui", "Éliminer 15 joueurs aujourd'hui.",
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
