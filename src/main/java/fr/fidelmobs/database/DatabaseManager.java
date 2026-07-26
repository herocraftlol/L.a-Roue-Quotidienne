package fr.fidelmobs.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Connexion à la base MySQL partagée avec le site web. Utilisée par DEUX fonctionnalités
 * optionnelles indépendantes : la boutique de tickets payants ET l'arène de stratégie web
 * (comptes, liaison, armées). Ce module ne s'active que si {@code boutique.enabled: true}
 * OU {@code strategie-web.enabled: true} dans le config — le reste du plugin fonctionne
 * entièrement sans MySQL (données joueurs en YAML local).
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

        // Ecrite par le plugin (/lier), lue puis supprimee par le site web une fois le code
        // consomme. Permet de lier un compte du site a un compte Minecraft (premium OU crack :
        // on ne verifie jamais rien aupres de Mojang, seulement l'UUID connu du serveur).
        String webLinkCodes = """
            CREATE TABLE IF NOT EXISTS web_link_codes (
                code CHAR(6) PRIMARY KEY,
                uuid VARCHAR(36) NOT NULL,
                pseudo VARCHAR(16) NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                expires_at TIMESTAMP NOT NULL,
                INDEX idx_uuid (uuid)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""";

        // Ecrite PERIODIQUEMENT par le plugin (ArmySyncTask), lue en LECTURE SEULE par le site
        // web pour composer les armees de la bataille de strategie. Le site n'ecrit jamais ici :
        // la collection reelle du joueur reste en YAML local (source de verite).
        String webPlayerArmy = """
            CREATE TABLE IF NOT EXISTS web_player_army (
                uuid VARCHAR(36) PRIMARY KEY,
                pseudo VARCHAR(16) NOT NULL,
                mobs_json TEXT NOT NULL,
                points INT DEFAULT 0,
                kills INT DEFAULT 0,
                morts INT DEFAULT 0,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""";

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(pendingTicketGrants);
            stmt.execute(webLinkCodes);
            stmt.execute(webPlayerArmy);
        } catch (SQLException e) {
            plugin.getLogger().severe("Impossible de créer les tables MySQL partagées : " + e.getMessage());
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
}
