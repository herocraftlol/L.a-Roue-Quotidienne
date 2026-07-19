const express = require('express');
const { grantTickets, getUuidFromUsername } = require('../tickets');

const router = express.Router();
router.use(express.json());

/** Protection simple par cle API. Adapte selon ton systeme d'auth existant
 *  (session admin, JWT, etc.) si tu en as deja un sur ton site. */
function requireAdminKey(req, res, next) {
  const key = req.headers['x-admin-key'];
  if (!key || key !== process.env.ADMIN_API_KEY) {
    return res.status(403).json({ error: 'Non autorise' });
  }
  next();
}

/**
 * POST /admin/grant
 * body: { username: "PseudoMinecraft", tickets: 5 }
 * Attribution manuelle (compensation, support, evenement...), independante d'un paiement.
 */
router.post('/grant', requireAdminKey, async (req, res) => {
  const { username, tickets } = req.body;

  if (!username || !tickets) {
    return res.status(400).json({ error: 'username et tickets sont requis' });
  }

  try {
    const uuid = await getUuidFromUsername(username);
    await grantTickets(uuid, Number(tickets), 'admin');
    res.json({ success: true, uuid, tickets: Number(tickets) });
  } catch (err) {
    console.error(err);
    res.status(400).json({ error: err.message });
  }
});

module.exports = router;
