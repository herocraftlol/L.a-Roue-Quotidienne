const express = require('express');
const Stripe = require('stripe');
const { grantTickets } = require('../tickets');

const router = express.Router();
const stripe = Stripe(process.env.STRIPE_SECRET_KEY);

/**
 * IMPORTANT : cette route doit recevoir le BODY BRUT (pas du JSON parse),
 * c'est pourquoi on utilise express.raw() ici et pas express.json() global.
 *
 * Cote Stripe Dashboard / Stripe CLI, configure le webhook pour ecouter
 * l'evenement "checkout.session.completed" et pointe-le vers :
 *   https://tonsite.com/boutique-tickets/webhook/stripe
 */
router.post('/stripe', express.raw({ type: 'application/json' }), async (req, res) => {
  let event;

  try {
    const signature = req.headers['stripe-signature'];
    event = stripe.webhooks.constructEvent(req.body, signature, process.env.STRIPE_WEBHOOK_SECRET);
  } catch (err) {
    console.error('Signature Stripe invalide:', err.message);
    return res.status(400).send(`Webhook Error: ${err.message}`);
  }

  if (event.type === 'checkout.session.completed') {
    const session = event.data.object;
    const { minecraft_uuid, tickets } = session.metadata || {};

    if (!minecraft_uuid || !tickets) {
      console.error('Metadata manquante sur la session Stripe:', session.id);
      return res.status(200).send('OK (metadata manquante, ignore)');
    }

    try {
      await grantTickets(minecraft_uuid, Number(tickets), 'purchase');
      console.log(`${tickets} ticket(s) attribue(s) a ${minecraft_uuid} suite a un achat.`);
    } catch (err) {
      console.error('Erreur attribution des tickets:', err);
      return res.status(500).send('Erreur interne');
    }
  }

  res.status(200).send('OK');
});

module.exports = router;
