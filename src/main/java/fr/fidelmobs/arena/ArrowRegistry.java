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

import java.util.List;
import java.util.Random;

/**
 * Flèches à effet obtenues à la roue : classées par rareté comme le reste du plugin,
 * elles s'équipent via le menu d'équipement et se tirent avec l'arc du kit (4e slot de
 * la hotbar). Plus la flèche est rare, plus son effet est puissant/dangereux.
 */
public final class ArrowRegistry {

    private static final Random RANDOM = new Random();

    /**
     * Un "modèle" de flèche pour un palier de rareté donné : nom affiché et effets de potion
     * appliqués à la flèche tirée (vide pour la flèche commune, qui reste une flèche simple).
     */
    private record Modele(String nom, List<PotionEffect> effets) {
    }

    private static final Modele[] MODELES = {
            new Modele("Flèche simple", List.of()),
            new Modele("Flèche ralentissante", List.of(
                    new PotionEffect(PotionEffectType.SLOWNESS, 100, 0))), // 5s, Lenteur I
            new Modele("Flèche empoisonnée", List.of(
                    new PotionEffect(PotionEffectType.POISON, 100, 1))), // 5s, Poison II
            new Modele("Flèche affaiblissante", List.of(
                    new PotionEffect(PotionEffectType.SLOWNESS, 140, 1),
                    new PotionEffect(PotionEffectType.WEAKNESS, 140, 1))),
            new Modele("Flèche foudroyante", List.of(
                    new PotionEffect(PotionEffectType.INSTANT_DAMAGE, 1, 1),
                    new PotionEffect(PotionEffectType.SLOWNESS, 100, 2)))
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

    public static ItemStack genererFlecheAleatoire() {
        return genererFlecheAleatoire(0);
    }

    public static ItemStack genererFlecheAleatoire(int minTierOrdinal) {
        int tier = tirerTier(minTierOrdinal);
        return construire(tier);
    }

    private static ItemStack construire(int tier) {
        Modele modele = MODELES[tier];
        MobRarity rarete = MobRarity.values()[tier];
        boolean effet = !modele.effets().isEmpty();

        ItemStack item = new ItemStack(effet ? Material.TIPPED_ARROW : Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(rarete.getCouleur() + modele.nom());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(Cles.FLECHE_RARETE, PersistentDataType.INTEGER, tier);
        meta.getPersistentDataContainer().set(Cles.FLECHE_MARQUEUR, PersistentDataType.BYTE, (byte) 1);

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
        return construire(0);
    }

    public static int getRarete(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        Integer v = item.getItemMeta().getPersistentDataContainer().get(Cles.FLECHE_RARETE, PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    public static boolean estFleche(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(Cles.FLECHE_MARQUEUR, PersistentDataType.BYTE);
    }

    /**
     * Description courte de l'effet appliqué par la flèche, pour affichage en lore
     * ou dans les messages de récompense (null pour la flèche simple, sans effet).
     */
    public static String decrireEffet(ItemStack item) {
        int tier = getRarete(item);
        return switch (tier) {
            case 1 -> "§7Ralentit la cible";
            case 2 -> "§7Empoisonne la cible";
            case 3 -> "§7Ralentit et affaiblit la cible";
            case 4 -> "§7Dégâts instantanés + ralentissement";
            default -> null;
        };
    }
}
