const mysql = require('mysql2/promise');
require('dotenv').config();

// Meme base MySQL que celle configuree dans plugins/LoyaltyMobs/config.yml
// (bloc "mysql:", avec boutique.enabled: true). Ce micro-service peut tourner
// sur une machine differente du serveur Minecraft tant qu'il peut joindre
// cet hote MySQL.
const pool = mysql.createPool({
  host: process.env.DB_HOST || '127.0.0.1',
  port: process.env.DB_PORT ? Number(process.env.DB_PORT) : 3306,
  user: process.env.DB_USER || 'loyaltymobs_user',
  password: process.env.DB_PASSWORD || '',
  database: process.env.DB_NAME || 'loyaltymobs_shop',
  waitForConnections: true,
  connectionLimit: 10,
  charset: 'utf8mb4_general_ci',
});

// Cree les tables si ce service demarre avant que le plugin n'ait encore
// tourne (sinon elles existent deja - IF NOT EXISTS ne casse rien).
async function ensureTables() {
  // Ecrite par CE service, lue et marquee "processed" par le plugin Paper.
  await pool.query(`
    CREATE TABLE IF NOT EXISTS pending_ticket_grants (
      id INT AUTO_INCREMENT PRIMARY KEY,
      uuid VARCHAR(36) NOT NULL,
      tickets INT NOT NULL,
      source VARCHAR(32) DEFAULT 'purchase',
      processed TINYINT(1) DEFAULT 0,
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  `);

  // Packs de tickets en vente (modifiable en base sans redeploiement, comme
  // la table "grades" de GradePlugin).
  await pool.query(`
    CREATE TABLE IF NOT EXISTS ticket_packs (
      id VARCHAR(64) PRIMARY KEY,
      display_name VARCHAR(64) NOT NULL,
      tickets INT NOT NULL,
      price DECIMAL(10,2) NOT NULL,
      sort_order INT DEFAULT 0,
      updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  `);

  const [rows] = await pool.query('SELECT COUNT(*) AS n FROM ticket_packs');
  if (rows[0].n === 0) {
    // Packs par defaut au premier demarrage - a adapter/ajouter directement
    // en base ensuite (ex: via phpMyAdmin), pas besoin de redeployer.
    await pool.query(
      `INSERT INTO ticket_packs (id, display_name, tickets, price, sort_order) VALUES
        ('pack_5', '5 tickets', 5, 1.99, 1),
        ('pack_12', '12 tickets', 12, 3.99, 2),
        ('pack_30', '30 tickets', 30, 7.99, 3)`
    );
  }
}

module.exports = { pool, ensureTables };
