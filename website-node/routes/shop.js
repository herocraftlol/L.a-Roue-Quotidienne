const express = require('express');
const Stripe = require('stripe');
const { listTicketPacks, getUuidFromUsername } = require('../tickets');

const router = express.Router();
const stripe = Stripe(process.env.STRIPE_SECRET_KEY);

router.use(express.json());

/** GET /shop/packs - liste des packs de tickets achetables */
router.get('/packs', async (req, res) => {
  try {
    const packs = await listTicketPacks();
    res.json(packs);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erreur serveur' });
  }
});

/**
 * POST /shop/checkout
 * body: { username: "PseudoMinecraft", packId: "pack_5" }
 * Cree une session Stripe et renvoie l'URL de paiement a rediriger cote client.
 */
router.post('/checkout', async (req, res) => {
  const { username, packId } = req.body;
  if (!username || !packId) {
    return res.status(400).json({ error: 'username et packId sont requis' });
  }

  try {
    const uuid = await getUuidFromUsername(username);
    const packs = await listTicketPacks();
    const pack = packs.find((p) => p.id === packId);

    if (!pack) {
      return res.status(404).json({ error: 'Pack introuvable' });
    }

    const session = await stripe.checkout.sessions.create({
      mode: 'payment',
      payment_method_types: ['card'],
      line_items: [
        {
          price_data: {
            currency: 'eur',
            product_data: { name: `${pack.display_name} de roue - ${username}` },
            unit_amount: Math.round(pack.price * 100),
          },
          quantity: 1,
        },
      ],
      metadata: {
        minecraft_uuid: uuid,
        tickets: String(pack.tickets),
      },
      // SHOP_PUBLIC_URL = l'URL PUBLIQUE (via ton reverse-proxy) de ce
      // micro-service, ex: https://monsite.fr/boutique-tickets - PAS l'URL
      // interne (http://127.0.0.1:3001) puisque c'est le navigateur du
      // client, et Stripe, qui doivent pouvoir atteindre cette URL.
      success_url: `${process.env.SHOP_PUBLIC_URL || 'http://localhost:3001'}/shop/success`,
      cancel_url: `${process.env.SHOP_PUBLIC_URL || 'http://localhost:3001'}/shop/cancel`,
    });

    res.json({ url: session.url });
  } catch (err) {
    console.error(err);
    res.status(400).json({ error: err.message });
  }
});

module.exports = router;
