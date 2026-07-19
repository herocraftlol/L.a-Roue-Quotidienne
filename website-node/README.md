# Boutique de tickets LoyaltyMobs (Node.js)

Module autonome (Express) à brancher sur ton site, pour vendre des tickets
de roue avec de l'argent réel (Stripe). Le plugin Paper les crédite
automatiquement, même si le joueur n'est pas connecté au moment du paiement.

## Architecture

```
[ Site web (index.html) ]  --achat-->  [ ce micro-service ]  --Stripe-->  [ paiement ]
                                               |
                                          écrit dans
                                               v
                                   [ MySQL partagé : pending_ticket_grants ]
                                               ^
                                          lit toutes les
                                          boutique.sync-interval-seconds
                                               |
                                   [ Plugin Paper LoyaltyMobs ]
```

Aucune donnée de carte bancaire ne transite par ce service ni par le
plugin : Stripe héberge lui-même la page de paiement (Stripe Checkout).
Ce service ne fait que créer la session de paiement et, une fois Stripe
confirmé via webhook, écrire "crédite X tickets à ce joueur" en base.

## Installation

```bash
cd website-node
npm install
cp .env.example .env
# Renseigne DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD : la MEME base
# MySQL que celle configurée dans plugins/LoyaltyMobs/config.yml
# (section "mysql:", avec "boutique.enabled: true"), et ta clé Stripe.
npm start
```

MySQL étant un vrai serveur réseau, ce micro-service et ton serveur Paper
n'ont pas besoin d'être sur la même machine : il suffit qu'ils puissent
tous les deux joindre le même hôte MySQL avec les mêmes identifiants.

## Fichiers

- **db.js** — pool de connexions MySQL (mysql2). Crée automatiquement les
  tables `pending_ticket_grants` et `ticket_packs` (avec 3 packs par défaut :
  5 tickets à 1,99 €, 12 à 3,99 €, 30 à 7,99 €) si elles n'existent pas.
- **tickets.js** — logique métier réutilisable, indépendante d'Express :
  - `getUuidFromUsername(pseudo)` : résout un pseudo Minecraft en UUID via
    l'API Mojang.
  - `grantTickets(uuid, tickets, source)` : insère une demande de crédit,
    lue par le plugin (`TicketSyncTask`) en quelques secondes.
  - `listTicketPacks()` : liste les packs en vente (pour affichage boutique).
- **routes/shop.js** — `GET /shop/packs` (liste des packs) et
  `POST /shop/checkout` (crée une session de paiement Stripe).
- **routes/webhook.js** — `POST /webhook/stripe` : reçoit la confirmation
  de paiement de Stripe et crédite les tickets automatiquement.
- **routes/admin.js** — `POST /admin/grant` : attribution manuelle protégée
  par clé API (`x-admin-key`), pour offrir des tickets (support, événement...)
  sans passer par un paiement.

## Paiement : Stripe par défaut

Pour tester en local avec le CLI Stripe :
```bash
stripe listen --forward-to localhost:3001/webhook/stripe
```

Sur le Dashboard Stripe (mode Live une fois prêt), configure un webhook
pointant vers `https://tonsite.fr/boutique-tickets/webhook/stripe`, écoutant
l'événement `checkout.session.completed`.

Si tu utilises un autre prestataire de paiement, seuls `routes/shop.js` et
`routes/webhook.js` changent : `grantTickets()` dans `tickets.js` reste
identique, c'est la partie réutilisable.

## Attribution manuelle (support, événement...)

```js
await fetch('https://tonsite.fr/boutique-tickets/admin/grant', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'x-admin-key': 'TA_CLE_ADMIN',
  },
  body: JSON.stringify({ username: 'PseudoDuJoueur', tickets: 5 }),
});
```

## Déploiement derrière un reverse-proxy (exemple nginx)

```nginx
location /boutique-tickets/ {
    proxy_pass http://127.0.0.1:3001/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
}
```

Adapte `SHOP_PUBLIC_URL` dans `.env` en conséquence
(`https://tonsite.fr/boutique-tickets`).
