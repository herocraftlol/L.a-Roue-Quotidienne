require('dotenv').config();
const express = require('express');
const cors = require('cors');
const { ensureTables } = require('./db');

const shopRoutes = require('./routes/shop');
const webhookRoutes = require('./routes/webhook');
const adminRoutes = require('./routes/admin');

const app = express();

// Meme domaine via reverse-proxy (ex: monsite.fr/boutique-tickets) => pas de
// CORS cross-origin necessaire en realite, mais on garde ce middleware par
// securite/portabilite (utile si tu changes d'archi plus tard).
app.use(cors({ origin: process.env.ORIGIN || '*' }));

// IMPORTANT : le webhook Stripe doit etre monte AVANT tout express.json()
// global, car il a besoin du body brut pour verifier la signature.
app.use('/webhook', webhookRoutes);

app.use('/shop', shopRoutes);
app.use('/admin', adminRoutes);

// Pages de retour apres paiement Stripe : on renvoie simplement le visiteur
// vers la section boutique de ton site, avec un indicateur dans l'URL que
// le front (index.html) affiche sous forme de toast.
app.get('/shop/success', (req, res) => {
  res.redirect(`${process.env.SITE_URL || 'http://localhost:8080'}/#boutique-tickets?achat=succes`);
});
app.get('/shop/cancel', (req, res) => {
  res.redirect(`${process.env.SITE_URL || 'http://localhost:8080'}/#boutique-tickets?achat=annule`);
});

const port = process.env.PORT || 3001;

ensureTables()
  .then(() => {
    app.listen(port, () => {
      console.log(`Micro-service boutique de tickets LoyaltyMobs démarré sur le port ${port}`);
    });
  })
  .catch((err) => {
    console.error('Impossible de se connecter/initialiser la base MySQL :', err.message);
    process.exit(1);
  });
