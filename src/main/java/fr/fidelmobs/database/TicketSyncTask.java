package fr.fidelmobs.database;

import fr.fidelmobs.LoyaltyMobsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Vérifie régulièrement la table {@code pending_ticket_grants} (alimentée par le site web
 * après un paiement Stripe confirmé) et crédite les tickets correspondants aux joueurs,
 * sans nécessiter de redémarrage ni que le joueur soit connecté au moment de l'achat.
 * Tourne de façon asynchrone (accès JDBC bloquant) ; l'écriture des données joueur et les
 * messages en jeu sont renvoyés sur le thread principal.
 */
public class TicketSyncTask implements Runnable {

    private final LoyaltyMobsPlugin plugin;
    private final DatabaseManager databaseManager;

    public TicketSyncTask(LoyaltyMobsPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    private record Grant(int id, UUID uuid, int tickets) {
    }

    @Override
    public void run() {
        List<Grant> aTraiter = new ArrayList<>();

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT id, uuid, tickets FROM pending_ticket_grants WHERE processed = 0")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    try {
                        aTraiter.add(new Grant(
                                rs.getInt("id"),
                                UUID.fromString(rs.getString("uuid")),
                                rs.getInt("tickets")
                        ));
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("UUID invalide dans pending_ticket_grants (id="
                                + rs.getInt("id") + "), ligne ignorée.");
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Impossible de lire les achats de tickets en attente : " + e.getMessage());
            return;
        }

        if (aTraiter.isEmpty()) return;

        for (Grant grant : aTraiter) {
            // "Réclame" la ligne avant de créditer quoi que ce soit : si LoyaltyMobs tourne
            // sur plusieurs serveurs backend de la structure Velocity (multi-serveur.enabled),
            // chaque instance lance ce cycle indépendamment sur la même table. Sans cette
            // étape, deux serveurs pourraient tous les deux lire la même ligne "non traitée"
            // avant que l'un des deux ait eu le temps de la marquer, et créditer le même achat
            // deux fois. L'UPDATE ... WHERE processed = 0 est atomique côté MySQL : seule la
            // toute première instance à l'exécuter obtient 1 ligne affectée, les autres 0.
            if (!reclamer(grant.id())) {
                continue; // déjà pris en charge par une autre instance entre-temps
            }

            // Créditer les tickets et sauvegarder doit se faire sur le thread principal
            // (accès disque/API Bukkit pour prévenir le joueur s'il est en ligne).
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getPlayerDataManager().addTickets(grant.uuid(), grant.tickets());
                plugin.getPlayerDataManager().save(grant.uuid());

                Player joueur = Bukkit.getPlayer(grant.uuid());
                if (joueur != null && joueur.isOnline()) {
                    joueur.sendMessage("§a§lMerci pour ton achat ! §7Tu as reçu §e" + grant.tickets()
                            + " ticket(s) de roue§7. Utilise §f/roue §7pour les lancer !");
                }
            });
        }
    }

    /**
     * Marque atomiquement une ligne comme traitée, uniquement si elle ne l'était pas déjà.
     * @return true si CETTE instance vient de la réclamer (donc doit créditer les tickets),
     *         false si une autre instance l'a déjà réclamée entre-temps.
     */
    private boolean reclamer(int id) {
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE pending_ticket_grants SET processed = 1 WHERE id = ? AND processed = 0")) {
            stmt.setInt(1, id);
            return stmt.executeUpdate() == 1;
        } catch (SQLException e) {
            plugin.getLogger().warning("Impossible de réclamer l'achat de tickets #" + id + " : " + e.getMessage());
            return false;
        }
    }
}
