package fr.fidelmobs.sacrifice;

import fr.fidelmobs.LoyaltyMobsPlugin;
import fr.fidelmobs.arena.BlockRegistry;
import fr.fidelmobs.arena.EconomieValeurs;
import fr.fidelmobs.arena.GearRegistry;
import fr.fidelmobs.arena.PowerRegistry;
import fr.fidelmobs.data.PlayerDataManager;
import fr.fidelmobs.mobs.MobRarity;
import fr.fidelmobs.mobs.MobRegistry;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "Sublimation" : sacrifie une catégorie (ou tout) de sa collection contre des points de
 * fidélité, à hauteur de la rareté/puissance de ce qui est rendu (voir EconomieValeurs).
 * Permet de repartir à zéro sur une catégorie, en échange de points à réinvestir comme on
 * veut (roue, boutique...) — mais fait bien perdre, de façon définitive, tout ce que les
 * défis accomplis et les séries de connexion avaient permis d'obtenir via les tickets.
 * Confirmation à deux temps obligatoire (irréversible) : /sacrifier <categorie> affiche un
 * avertissement, /sacrifier <categorie> confirmer dans les 30s qui suivent l'exécute.
 */
public class SacrificeManager {

    public static final List<String> CATEGORIES = List.of("mobs", "equipements", "pouvoirs", "blocs", "tout");
    private static final long DELAI_CONFIRMATION_MS = 30_000L;

    private record PendingConfirmation(String categorie, long expireMs) {
    }

    private final LoyaltyMobsPlugin plugin;
    private final Map<UUID, PendingConfirmation> enAttente = new HashMap<>();

    public SacrificeManager(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
    }

    public int valeurMobs(UUID uuid) {
        int total = 0;
        for (Map.Entry<EntityType, Integer> e : plugin.getPlayerDataManager().getCollection(uuid).entrySet()) {
            total += EconomieValeurs.valeurBase(MobRegistry.getRarete(e.getKey())) * e.getValue();
        }
        return total;
    }

    public int valeurEquipements(UUID uuid) {
        int total = 0;
        for (ItemStack item : plugin.getPlayerDataManager().getEquipements(uuid)) {
            total += EconomieValeurs.valeurBase(MobRarity.values()[GearRegistry.getRarete(item)]);
        }
        return total;
    }

    public int valeurPouvoirs(UUID uuid) {
        int total = 0;
        for (Map.Entry<String, Integer> e : plugin.getPlayerDataManager().getPouvoirsPossedes(uuid).entrySet()) {
            total += EconomieValeurs.valeurBase(PowerRegistry.getRarete(e.getKey())) * e.getValue();
        }
        return total;
    }

    public int valeurBlocs(UUID uuid) {
        int total = 0;
        for (Material m : plugin.getPlayerDataManager().getBlocsDebloques(uuid)) {
            total += EconomieValeurs.valeurBase(BlockRegistry.getRarete(m));
        }
        return total;
    }

    public int valeurTotale(UUID uuid) {
        return valeurMobs(uuid) + valeurEquipements(uuid) + valeurPouvoirs(uuid) + valeurBlocs(uuid);
    }

    private int valeurPour(String categorie, UUID uuid) {
        return switch (categorie) {
            case "mobs" -> valeurMobs(uuid);
            case "equipements" -> valeurEquipements(uuid);
            case "pouvoirs" -> valeurPouvoirs(uuid);
            case "blocs" -> valeurBlocs(uuid);
            case "tout" -> valeurTotale(uuid);
            default -> 0;
        };
    }

    /** Première étape : affiche un avertissement et démarre la fenêtre de confirmation. */
    public void demander(Player player, String categorie) {
        UUID uuid = player.getUniqueId();
        int points = valeurPour(categorie, uuid);
        if (points <= 0) {
            player.sendMessage("§7Tu n'as rien à sacrifier dans cette catégorie pour le moment.");
            return;
        }

        enAttente.put(uuid, new PendingConfirmation(categorie, System.currentTimeMillis() + DELAI_CONFIRMATION_MS));

        player.sendMessage("§c§l⚠ Sacrifice " + nomCategorie(categorie) + " ⚠");
        player.sendMessage("§7Tu vas perdre §c" + descriptionPerte(categorie) + " §7définitivement, contre §a+" + points + " points de fidélité§7.");
        player.sendMessage("§7(y compris ce que tes défis accomplis et tes séries de connexion t'avaient permis d'obtenir)");
        player.sendMessage("§eRetape §f/sacrifier " + categorie + " confirmer §edans les 30 secondes pour valider.");
    }

    /** Seconde étape : exécute réellement le sacrifice si une demande valide est en attente. */
    public void confirmer(Player player, String categorie) {
        UUID uuid = player.getUniqueId();
        PendingConfirmation attente = enAttente.get(uuid);
        if (attente == null || !attente.categorie().equals(categorie)) {
            player.sendMessage("§cAucune demande de sacrifice en attente pour cette catégorie. Tape d'abord §f/sacrifier " + categorie + "§c.");
            return;
        }
        if (System.currentTimeMillis() > attente.expireMs()) {
            enAttente.remove(uuid);
            player.sendMessage("§cLa fenêtre de confirmation a expiré. Retape §f/sacrifier " + categorie + " §cpour recommencer.");
            return;
        }
        enAttente.remove(uuid);
        executer(player, categorie);
    }

    private void executer(Player player, String categorie) {
        PlayerDataManager data = plugin.getPlayerDataManager();
        UUID uuid = player.getUniqueId();
        int points = valeurPour(categorie, uuid);

        switch (categorie) {
            case "mobs" -> data.reinitialiserMobs(uuid);
            case "equipements" -> data.reinitialiserEquipements(uuid);
            case "pouvoirs" -> data.reinitialiserPouvoirs(uuid);
            case "blocs" -> data.reinitialiserBlocs(uuid);
            case "tout" -> {
                data.reinitialiserMobs(uuid);
                data.reinitialiserEquipements(uuid);
                data.reinitialiserPouvoirs(uuid);
                data.reinitialiserBlocs(uuid);
            }
            default -> {
                return;
            }
        }

        data.ajouterPoints(uuid, points);
        data.save(uuid);

        player.sendMessage("§a✦ Sacrifice effectué : §7+" + points + " points de fidélité§a.");
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.5f, 1.4f);

        if (plugin.getArenaProtectionListener().estDansArene(player)) {
            plugin.getKitManager().appliquerKit(player);
            player.updateInventory();
        }
    }

    private String nomCategorie(String c) {
        return switch (c) {
            case "mobs" -> "— MOBS";
            case "equipements" -> "— ÉQUIPEMENT";
            case "pouvoirs" -> "— POUVOIRS";
            case "blocs" -> "— BLOCS";
            case "tout" -> "— TOUT";
            default -> c;
        };
    }

    private String descriptionPerte(String c) {
        return switch (c) {
            case "mobs" -> "toute ta collection de mobs";
            case "equipements" -> "toute ta collection d'armes/armures";
            case "pouvoirs" -> "tous tes pouvoirs";
            case "blocs" -> "tous tes blocs débloqués";
            case "tout" -> "TOUT (mobs, équipement, pouvoirs et blocs)";
            default -> "ta collection";
        };
    }
}
