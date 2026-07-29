package fr.fidelmobs.arena;

import fr.fidelmobs.Cles;
import fr.fidelmobs.mobs.MobRarity;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Flèches à effet obtenues à la roue : classées par rareté comme le reste du plugin.
 * Chaque palier de rareté propose PLUSIEURS flèches différentes (des combinaisons plus
 * faibles et d'autres plus fortes), pas une seule à prendre ou à laisser : ça donne
 * beaucoup plus de variété à collectionner que par le passé. Elles s'équipent via le menu
 * d'équipement et se tirent avec l'arc du kit (4e slot de la hotbar).
 */
public final class ArrowRegistry {

    private static final Random RANDOM = new Random();

    /**
     * Un modèle de flèche : identifiant unique (utilisé pour l'anti-doublon, indépendant du
     * palier puisque plusieurs modèles partagent désormais le même palier), palier de
     * rareté, nom affiché et effets de potion appliqués à la cible touchée.
     */
    private record Modele(int id, int tier, String nom, List<PotionEffect> effets) {
    }

    private static final Modele[] MODELES = {
            // ---- COMMUN (tier 0) : aucun effet ou presque ----
            new Modele(0, 0, "Flèche simple", List.of()),
            new Modele(1, 0, "Flèche piquante", List.of(
                    new PotionEffect(PotionEffectType.SLOWNESS, 40, 0))), // 2s, Lenteur I

            // ---- PEU COMMUN (tier 1) : gêne légère ----
            new Modele(2, 1, "Flèche ralentissante", List.of(
                    new PotionEffect(PotionEffectType.SLOWNESS, 100, 0))), // 5s, Lenteur I
            new Modele(3, 1, "Flèche collante", List.of(
                    new PotionEffect(PotionEffectType.SLOWNESS, 80, 1))), // 4s, Lenteur II (plus fort, plus court)
            new Modele(4, 1, "Flèche nauséeuse", List.of(
                    new PotionEffect(PotionEffectType.NAUSEA, 100, 0),
                    new PotionEffect(PotionEffectType.SLOWNESS, 60, 0))),

            // ---- RARE (tier 2) : combos plus marqués ----
            new Modele(5, 2, "Flèche empoisonnée", List.of(
                    new PotionEffect(PotionEffectType.POISON, 100, 1))), // 5s, Poison II
            new Modele(6, 2, "Flèche aveuglante", List.of(
                    new PotionEffect(PotionEffectType.BLINDNESS, 100, 0),
                    new PotionEffect(PotionEffectType.SLOWNESS, 60, 0))),
            new Modele(7, 2, "Flèche engourdissante", List.of(
                    new PotionEffect(PotionEffectType.SLOWNESS, 120, 2))), // 6s, Lenteur III, mono-effet mais fort

            // ---- ÉPIQUE (tier 3) : très handicapant ----
            new Modele(8, 3, "Flèche affaiblissante", List.of(
                    new PotionEffect(PotionEffectType.SLOWNESS, 140, 1),
                    new PotionEffect(PotionEffectType.WEAKNESS, 140, 1))),
            new Modele(9, 3, "Flèche toxique", List.of(
                    new PotionEffect(PotionEffectType.POISON, 120, 2),
                    new PotionEffect(PotionEffectType.NAUSEA, 100, 1))),
            new Modele(10, 3, "Flèche brisante", List.of(
                    new PotionEffect(PotionEffectType.WEAKNESS, 160, 2),
                    new PotionEffect(PotionEffectType.SLOWNESS, 100, 1))),

            // ---- LÉGENDAIRE (tier 4) : les plus dévastatrices ----
            new Modele(11, 4, "Flèche foudroyante", List.of(
                    new PotionEffect(PotionEffectType.INSTANT_DAMAGE, 1, 1),
                    new PotionEffect(PotionEffectType.SLOWNESS, 100, 2))),
            new Modele(12, 4, "Flèche dévastatrice", List.of(
                    new PotionEffect(PotionEffectType.INSTANT_DAMAGE, 1, 0),
                    new PotionEffect(PotionEffectType.POISON, 140, 2),
                    new PotionEffect(PotionEffectType.SLOWNESS, 100, 1))),
            new Modele(13, 4, "Flèche du néant", List.of(
                    new PotionEffect(PotionEffectType.SLOWNESS, 160, 3),
                    new PotionEffect(PotionEffectType.WEAKNESS, 160, 2),
                    new PotionEffect(PotionEffectType.BLINDNESS, 100, 0))),
    };

    private ArrowRegistry() {
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

    /**
     * Tire un tier pondéré, mais jamais en dessous de {@code minTierOrdinal}
     * (utilisé pour garantir au moins une récompense rare à chaque tirage de la roue).
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

    private static List<Modele> modelesDuTier(int tier) {
        List<Modele> resultat = new ArrayList<>();
        for (Modele m : MODELES) {
            if (m.tier() == tier) resultat.add(m);
        }
        return resultat;
    }

    public static ItemStack genererFlecheAleatoire() {
        return genererFlecheAleatoire(0);
    }

    public static ItemStack genererFlecheAleatoire(int minTierOrdinal) {
        int tier = tirerTier(minTierOrdinal);
        List<Modele> candidats = modelesDuTier(tier);
        Modele choisi = candidats.get(RANDOM.nextInt(candidats.size()));
        return construire(choisi);
    }

    /**
     * Tire uniquement une rareté (sans construire de flèche), utilisé quand la collection
     * de flèches est déjà complète à ce tier minimum : sert à dimensionner le bonus de
     * compensation dans la roue, sans jamais pouvoir donner un doublon réel.
     */
    public static MobRarity tirerRareteSeule(int minTierOrdinal) {
        int tier = tirerTier(minTierOrdinal);
        return MobRarity.values()[tier];
    }

    /**
     * Variante anti-doublons : ne tire jamais un modèle de flèche déjà présent dans
     * {@code idsExclus} (les modèles déjà possédés par le joueur, identifiés par leur id
     * unique et non plus par leur seul palier puisque plusieurs flèches partagent
     * désormais le même palier de rareté). Le tirage se fait d'abord par palier (pondéré
     * comme le reste du plugin), puis au hasard parmi les modèles encore inédits de ce
     * palier. Retourne {@code null} si TOUS les modèles possibles (au minimum demandé)
     * sont déjà possédés (collection de flèches complète).
     */
    public static ItemStack genererFlecheAleatoire(int minTierOrdinal, Set<Integer> idsExclus) {
        int min = Math.max(0, minTierOrdinal);
        Map<Integer, List<Modele>> parTierDisponibles = new TreeMap<>();
        for (Modele m : MODELES) {
            if (m.tier() >= min && !idsExclus.contains(m.id())) {
                parTierDisponibles.computeIfAbsent(m.tier(), k -> new ArrayList<>()).add(m);
            }
        }
        if (parTierDisponibles.isEmpty()) return null; // collection déjà complète à ce palier minimum

        MobRarity[] valeurs = MobRarity.values();
        int poidsTotal = 0;
        for (int tier : parTierDisponibles.keySet()) poidsTotal += valeurs[tier].getPoids();
        int tirage = RANDOM.nextInt(poidsTotal);
        int cumul = 0;
        int tierChoisi = -1;
        for (int tier : parTierDisponibles.keySet()) {
            cumul += valeurs[tier].getPoids();
            if (tirage < cumul) {
                tierChoisi = tier;
                break;
            }
        }
        if (tierChoisi == -1) {
            for (int tier : parTierDisponibles.keySet()) tierChoisi = tier;
        }

        List<Modele> candidats = parTierDisponibles.get(tierChoisi);
        Modele choisi = candidats.get(RANDOM.nextInt(candidats.size()));
        return construire(choisi);
    }

    private static ItemStack construire(Modele modele) {
        MobRarity rarete = MobRarity.values()[modele.tier()];
        boolean effet = !modele.effets().isEmpty();

        ItemStack item = new ItemStack(effet ? Material.TIPPED_ARROW : Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(rarete.getCouleur() + modele.nom());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (effet) {
            meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        }
        meta.getPersistentDataContainer().set(Cles.FLECHE_RARETE, PersistentDataType.INTEGER, modele.tier());
        meta.getPersistentDataContainer().set(Cles.FLECHE_MARQUEUR, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(Cles.FLECHE_MODELE_ID, PersistentDataType.INTEGER, modele.id());

        if (effet && meta instanceof PotionMeta potionMeta) {
            for (PotionEffect ef : modele.effets()) {
                potionMeta.addCustomEffect(ef, true);
            }
        }

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Flèche simple par défaut, donnée avec l'arc du kit quand le joueur n'a encore
     * équipé aucune flèche à effet de sa collection.
     */
    public static ItemStack flecheParDefaut() {
        return construire(MODELES[0]);
    }

    public static int getRarete(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        Integer v = item.getItemMeta().getPersistentDataContainer().get(Cles.FLECHE_RARETE, PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    /**
     * Identifiant unique du modèle de flèche (indépendant du palier de rareté, puisque
     * plusieurs modèles peuvent désormais partager le même palier). Utilisé pour l'anti-
     * doublon précis et pour regrouper l'affichage dans les menus.
     */
    public static int getModeleId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return -1;
        Integer v = item.getItemMeta().getPersistentDataContainer().get(Cles.FLECHE_MODELE_ID, PersistentDataType.INTEGER);
        return v == null ? -1 : v;
    }

    public static boolean estFleche(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(Cles.FLECHE_MARQUEUR, PersistentDataType.BYTE);
    }

    /** Nombre total de modèles de flèche distincts obtenables, utilisé par le système de défis. */
    public static int getNombreModelesTotal() {
        return MODELES.length;
    }

    /**
     * Description lisible des effets appliqués par la flèche, pour affichage en lore ou
     * dans les messages/titres de récompense (null pour une flèche sans aucun effet).
     * Générique : construite directement à partir des effets réels de potion de l'objet,
     * donc valable pour n'importe quel modèle présent ou futur sans liste à entretenir.
     */
    public static String decrireEffet(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !(item.getItemMeta() instanceof PotionMeta potionMeta)) {
            return null;
        }
        List<PotionEffect> effets = potionMeta.getCustomEffects();
        if (effets.isEmpty()) {
            return null;
        }
        return effets.stream()
                .map(e -> "§7" + nomEffet(e.getType()) + " " + chiffreRomain(e.getAmplifier() + 1))
                .collect(Collectors.joining("§8, "));
    }

    private static String nomEffet(PotionEffectType type) {
        if (type.equals(PotionEffectType.SLOWNESS)) return "Lenteur";
        if (type.equals(PotionEffectType.POISON)) return "Poison";
        if (type.equals(PotionEffectType.WEAKNESS)) return "Faiblesse";
        if (type.equals(PotionEffectType.NAUSEA)) return "Nausée";
        if (type.equals(PotionEffectType.BLINDNESS)) return "Cécité";
        if (type.equals(PotionEffectType.INSTANT_DAMAGE)) return "Dégâts instantanés";
        String brut = type.getKey().getKey().toLowerCase().replace('_', ' ');
        return brut.substring(0, 1).toUpperCase() + brut.substring(1);
    }

    private static String chiffreRomain(int niveau) {
        String[] romains = {"", "I", "II", "III", "IV", "V", "VI"};
        return (niveau >= 0 && niveau < romains.length) ? romains[niveau] : String.valueOf(niveau);
    }
}
