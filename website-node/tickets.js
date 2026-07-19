const fetch = require('node-fetch');
const { pool } = require('./db');

/**
 * Recupere l'UUID (avec tirets) d'un joueur a partir de son pseudo.
 * Le joueur doit s'etre connecte au moins une fois sur un compte Mojang valide.
 */
async function getUuidFromUsername(username) {
  const res = await fetch(`https://api.mojang.com/users/profiles/minecraft/${encodeURIComponent(username)}`);
  if (!res.ok) {
    throw new Error(`Joueur introuvable: ${username}`);
  }
  const data = await res.json();
  const raw = data.id;
  return `${raw.slice(0, 8)}-${raw.slice(8, 12)}-${raw.slice(12, 16)}-${raw.slice(16, 20)}-${raw.slice(20)}`;
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
