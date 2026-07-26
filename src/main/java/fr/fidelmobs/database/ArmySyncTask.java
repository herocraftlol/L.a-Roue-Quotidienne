package fr.fidelmobs.database;

import fr.fidelmobs.LoyaltyMobsPlugin;
import fr.fidelmobs.data.PlayerDataManager;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

/**
 * Pousse régulièrement la collection de mobs (+ stats PvP) de chaque joueur connecté vers
 * la table MySQL partagée {@code web_player_army}, lue par le site web pour composer les
 * armées de la bataille de stratégie en ligne.
 *
 * Sens UNIQUE plugin -> site : le site ne modifie jamais la collection réelle du joueur,
 * qui reste en YAML local (voir {@link PlayerDataManager}), source de vérité absolue.
 */
public class ArmySyncTask implements Runnable {

    private final LoyaltyMobsPlugin plugin;
    private final DatabaseManager databaseManager;

    public ArmySyncTask(LoyaltyMobsPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    @Override
    public void run() {
        for (Player joueur : plugin.getServer().getOnlinePlayers()) {
            synchroniser(joueur.getUniqueId(), joueur.getName());
        }
    }

    /**
     * Synchronise un joueur précis immédiatement (utilisé aussi par /lier et à la
     * déconnexion, pour éviter d'attendre le prochain cycle périodique).
     */
    public void synchroniser(UUID uuid, String pseudo) {
        PlayerDataManager data = plugin.getPlayerDataManager();
        Map<EntityType, Integer> collection = data.getCollection(uuid);

        StringBuilder json = new StringBuilder("{");
        boolean premier = true;
        for (Map.Entry<EntityType, Integer> entree : collection.entrySet()) {
            if (!premier) json.append(',');
            json.append('"').append(entree.getKey().name()).append('"').append(':').append(entree.getValue());
            premier = false;
        }
        json.append('}');

        int points = data.getPoints(uuid);
        int kills = data.getKills(uuid);
        int morts = data.getMorts(uuid);

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO web_player_army (uuid, pseudo, mobs_json, points, kills, morts, updated_at) "
                             + "VALUES (?, ?, ?, ?, ?, ?, NOW()) "
                             + "ON DUPLICATE KEY UPDATE pseudo = VALUES(pseudo), mobs_json = VALUES(mobs_json), "
                             + "points = VALUES(points), kills = VALUES(kills), morts = VALUES(morts), updated_at = NOW()")) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, pseudo);
            stmt.setString(3, json.toString());
            stmt.setInt(4, points);
            stmt.setInt(5, kills);
            stmt.setInt(6, morts);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Impossible de synchroniser l'armée de " + pseudo + " : " + e.getMessage());
        }
    }
}
