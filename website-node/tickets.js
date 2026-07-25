const crypto = require('crypto');
const { pool } = require('./db');

/**
 * Calcule l'UUID "offline" d'un pseudo, exactement comme le fait Velocity/Paper
 * sur ce réseau : UUID de type 3, basé sur un hash MD5 de "OfflinePlayer:<pseudo>".
 *
 * IMPORTANT (vérifié en conditions réelles) : sur ce réseau, MEME les comptes
 * premium reçoivent cet UUID offline (Velocity tourne en online-mode=false sans
 * vérification Mojang) — inutile donc d'interroger l'API Mojang, ça ne
 * correspondrait à rien côté serveur. Si un jour un vrai plugin d'auth hybride
 * est mis en place (donnant le vrai UUID Mojang aux comptes premium vérifiés),
 * il faudra réintroduire une résolution Mojang -> repli offline comme avant.
 */
function getUuidFromUsername(username) {
  const hash = crypto.createHash('md5').update(`OfflinePlayer:${username}`, 'utf8').digest();
  hash[6] = (hash[6] & 0x0f) | 0x30; // version 3 (name-based, MD5)
  hash[8] = (hash[8] & 0x3f) | 0x80; // variant RFC 4122
  const hex = hash.toString('hex');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/**
 * Insere une demande de credit de tickets. Le plugin Paper (TicketSyncTask) la lit
 * toutes les boutique.sync-interval-seconds et credite le joueur, meme hors ligne.
 *
 * @param {string} uuid    UUID du joueur (avec tirets)
 * @param {number} tickets Nombre de tickets a crediter
 * @param {"purchase"|"admin"} source
 */
async function grantTickets(uuid, tickets, source = 'purchase') {
  await pool.execute(
    `INSERT INTO pending_ticket_grants (uuid, tickets, source) VALUES (?, ?, ?)`,
    [uuid, tickets, source]
  );
}

/** Liste les packs de tickets en vente (pour affichage boutique), triés par sort_order. */
async function listTicketPacks() {
  const [rows] = await pool.query(
    `SELECT id, display_name, tickets, price FROM ticket_packs ORDER BY sort_order ASC`
  );
  return rows;
}

module.exports = { getUuidFromUsername, grantTickets, listTicketPacks };
