package fr.fidelmobs.niveaux;

import fr.fidelmobs.LoyaltyMobsPlugin;
import fr.fidelmobs.data.PlayerDataManager;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Système de niveaux façon HikaBrain : des points de niveau (distincts des points de
 * fidélité, jamais dépensables) font progresser un niveau par paliers de plus en plus
 * exigeants (chaque niveau demande plus de points que le précédent), gagnés en combat :
 * toucher un adversaire, le tuer (soi-même, via un mob allié, ou via un pouvoir), assister
 * un allié sur un kill, et enchaîner des séries de kills.
 */
public class NiveauManager {

    // ── Barème de points de niveau ──────────────────────────────────────────────
    // Du plus simple au plus dur à obtenir, comme dans HikaBrain (coup < kill < ...).
    public static final int POINTS_PAR_COUP = 1;
    public static final int POINTS_KILL_MOB = 8;
    public static final int POINTS_KILL_POUVOIR = 10;
    public static final int POINTS_KILL_DIRECT = 12;
    public static final int POINTS_ASSIST = 4;
    public static final int POINTS_PAR_PALIER_SERIE = 2; // par kill au-delà du 1er d'une série

    private static final int BASE_POINTS_NIVEAU = 60;
    private static final String CLE_COMPTEUR = "niveau_points";

    private final LoyaltyMobsPlugin plugin;

    public NiveauManager(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Points cumulés nécessaires pour ATTEINDRE le niveau donné (paliers progressifs :
     * l'écart entre deux niveaux consécutifs grandit à chaque niveau). Niveau 0 = 0 point.
     */
    public int pointsRequisPour(int niveau) {
        if (niveau <= 0) return 0;
        return BASE_POINTS_NIVEAU * niveau * (niveau + 1) / 2;
    }

    public int getNiveauPourPoints(int points) {
        int niveau = 0;
        while (pointsRequisPour(niveau + 1) <= points) {
            niveau++;
        }
        return niveau;
    }

    public int getPoints(UUID uuid) {
        return plugin.getPlayerDataManager().getCompteur(uuid, CLE_COMPTEUR);
    }

    public int getNiveau(UUID uuid) {
        return getNiveauPourPoints(getPoints(uuid));
    }

    public int getPointsRestantsProchainNiveau(UUID uuid) {
        int points = getPoints(uuid);
        int prochainNiveau = getNiveauPourPoints(points) + 1;
        return Math.max(0, pointsRequisPour(prochainNiveau) - points);
    }

    /**
     * Ajoute des points de niveau à un joueur et annonce, discrètement mais clairement,
     * toute montée de niveau (les gains de points eux-mêmes ne sont pas annoncés un par un
     * pour ne pas spammer le chat en plein combat).
     *
     * Ne force PAS l'écriture sur disque (pas de data.save ici) : cette méthode est appelée
     * à chaque coup porté en arène, potentiellement plusieurs fois par seconde, et une
     * sauvegarde synchrone à chaque appel provoquerait des à-coups en plein combat. La
     * valeur reste correcte en mémoire immédiatement ; elle est physiquement persistée par
     * les sauvegardes déjà déclenchées ailleurs pour des évènements plus rares (kill, mort,
     * déconnexion...).
     */
    public void ajouterPoints(Player joueur, int montant) {
        if (montant <= 0 || joueur == null) return;
        PlayerDataManager data = plugin.getPlayerDataManager();
        UUID uuid = joueur.getUniqueId();

        int niveauAvant = getNiveau(uuid);
        data.incrementerCompteur(uuid, CLE_COMPTEUR, montant);
        int niveauApres = getNiveau(uuid);

        if (niveauApres > niveauAvant) {
            joueur.sendMessage("§b✦ §lNiveau " + niveauApres + " atteint !");
            joueur.playSound(joueur.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        }
    }
}
