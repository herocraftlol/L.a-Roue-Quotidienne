-- Schéma de référence pour la boutique de tickets LoyaltyMobs.
-- Les tables sont créées automatiquement au premier démarrage du plugin
-- (DatabaseManager) ou du micro-service (db.js) si elles n'existent pas déjà
-- — ce fichier sert surtout de documentation / pour créer la base à la main.

CREATE DATABASE IF NOT EXISTS loyaltymobs_shop
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE loyaltymobs_shop;

-- Écrite par le site web (après paiement Stripe confirmé via webhook, ou
-- attribution manuelle admin). Lue et marquée "processed" par le plugin
-- Paper (TicketSyncTask) — il ne touche jamais à rien d'autre dans cette
-- base, et le site web ne touche jamais aux données joueur (YAML local).
CREATE TABLE IF NOT EXISTS pending_ticket_grants (
    id INT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL,
    tickets INT NOT NULL,
    source VARCHAR(32) DEFAULT 'purchase',
    processed TINYINT(1) DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_processed (processed)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Packs de tickets en vente sur le site — modifiable directement en base
-- (prix, quantité, packs ajoutés/retirés) sans avoir à redéployer le site
-- ni le plugin.
CREATE TABLE IF NOT EXISTS ticket_packs (
    id VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(64) NOT NULL,
    tickets INT NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    sort_order INT DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO ticket_packs (id, display_name, tickets, price, sort_order) VALUES
    ('pack_5', '5 tickets', 5, 1.99, 1),
    ('pack_12', '12 tickets', 12, 3.99, 2),
    ('pack_30', '30 tickets', 30, 7.99, 3)
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name);
