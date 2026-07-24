package fr.fidelmobs.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Connexion à la base MySQL partagée avec le site web (boutique de tickets payants) et,
 * optionnellement, entre tous les serveurs Paper d'une même structure Velocity (données
 * joueur communes : tickets, collection, points...). Ce module ne s'active que si
 * {@code boutique.enabled: true} et/ou {@code multi-serveur.enabled: true} dans le config —
 * si aucun des deux n'est activé, le plugin fonctionne entièrement sans MySQL (données
 * joueurs en YAML local, boutique désactivée).
 */
public class DatabaseManager {

    private final JavaPlugin plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        FileConfiguration cfg = plugin.getConfig();

        String host = cfg.getString("mysql.host", "127.0.0.1");
        int port = cfg.getInt("mysql.port", 3306);
        String database = cfg.getString("mysql.database", "loyaltymobs_shop");
        String user = cfg.getString("mysql.user", "loyaltymobs_user");
        String password = cfg.getString("mysql.password", "");
        int poolSize = cfg.getInt("mysql.pool-size", 4);
        boolean useSSL = cfg.getBoolean("mysql.useSSL", false);

        String jdbcUrl = String.format(
                "jdbc:mysql://%s:%d/%s?useSSL=%s&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8&useUnicode=true&connectionCollation=utf8mb4_general_ci",
                host, port, database, useSSL
        );

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(user);
        hikariConfig.setPassword(password);
        hikariConfig.setMaximumPoolSize(poolSize);
        hikariConfig.setPoolName("LoyaltyMobs-Shop-Pool");
        hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
        hikariConfig.setConnectionTestQuery("SELECT 1");
        hikariConfig.setMaxLifetime(280000); // un peu moins que le wait_timeout par defaut de MySQL (28 min)

        this.dataSource = new HikariDataSource(hikariConfig);

        creerTables();
    }

    private void creerTables() {
        // Ecrite par le site web (apres paiement Stripe confirme via webhook) : chaque ligne
        // est une quantite de tickets a crediter a un joueur. Le plugin la lit et la marque
        // "processed" une fois appliquee, sans jamais rien ecrire d'autre dans cette base.
        String pendingTicketGrants = """
            CREATE TABLE IF NOT EXISTS pending_ticket_grants (
                id INT AUTO_INCREMENT PRIMARY KEY,
                uuid VARCHAR(36) NOT NULL,
                tickets INT NOT NULL,
                source VARCHAR(32) DEFAULT 'purchase',
                processed TINYINT(1) DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""";

        // Donnees joueur partagees entre TOUS les serveurs Paper de la structure Velocity
        // (si multi-serveur.enabled: true) : on serialise le YAML complet d'un joueur (meme
        // format que l'ancien fichier local .yml) dans une seule colonne texte, cle sur son
        // UUID. Chaque serveur charge cette ligne a la connexion du joueur et la reecrit a sa
        // deconnexion : la derniere ecriture gagne, comme pour n'importe quelle donnee joueur
        // migrant d'un serveur a l'autre sur un reseau Velocity classique.
        String playerData = """
            CREATE TABLE IF NOT EXISTS player_data (
                uuid VARCHAR(36) PRIMARY KEY,
                yaml LONGTEXT,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""";

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(pendingTicketGrants);
            stmt.execute(playerData);
        } catch (SQLException e) {
            plugin.getLogger().severe("Impossible de créer les tables MySQL : " + e.getMessage());
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    // ---- Données joueur partagées (multi-serveur.enabled) ----

    /**
     * Charge le YAML brut d'un joueur depuis la base partagée, ou {@code null} s'il n'a
     * encore aucune donnée enregistrée. Appel bloquant (JDBC) : à faire hors du thread
     * principal (typiquement depuis AsyncPlayerPreLoginEvent, déjà asynchrone).
     */
    public String chargerDonneesJoueur(UUID uuid) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT yaml FROM player_data WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("yaml");
                }
                return null;
            }
        }
    }

    /**
     * Enregistre (insère ou remplace) le YAML complet d'un joueur dans la base partagée.
     * Appel bloquant (JDBC) : à faire hors du thread principal.
     */
    public void sauvegarderDonneesJoueur(UUID uuid, String yaml) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO player_data (uuid, yaml) VALUES (?, ?) "
                             + "ON DUPLICATE KEY UPDATE yaml = VALUES(yaml)")) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, yaml);
            stmt.executeUpdate();
        }
    }

    /**
     * Liste tous les UUID ayant des données enregistrées en base (utilisé pour les
     * classements, y compris les joueurs hors ligne / connectés à un autre serveur).
     */
    public List<UUID> listerUuidConnues() throws SQLException {
        List<UUID> resultat = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT uuid FROM player_data")) {
            while (rs.next()) {
                try {
                    resultat.add(UUID.fromString(rs.getString("uuid")));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return resultat;
    }
}
