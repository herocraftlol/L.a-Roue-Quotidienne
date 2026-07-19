# LoyaltyMobs

Plugin Paper 1.21 de fidélisation par connexions journalières, avec collection permanente de
mobs/blocs/équipements à la "roue" façon cartes à collectionner, et une arène PvP configurable
avec ses propres règles. Boutique en argent réel optionnelle pour acheter des tickets de roue.

## Fonctionnalités

### Fidélisation
- **Connexion quotidienne** : série de jours consécutifs suivie automatiquement, avec distribution de
  tickets (`tickets-par-jour` + bonus de palier définis dans `config.yml`). Un rappel s'affiche à
  chaque connexion tant qu'il reste des tickets non utilisés.
- **`/roue`** : consomme un ticket et donne une récompense de **chaque** catégorie à chaque lancer
  (mob + bloc + équipement), affichées avec une mise en forme colorée selon la rareté et une petite
  fanfare (son + titre à l'écran) pour les tirages épiques/légendaires. Le stuff de base (cuir/bois)
  ne peut jamais s'obtenir sans enchantement.
  - **Mob** : un allié ajouté **définitivement** à la collection (voir `/armee` et `/invoquer`).
  - **Bloc** : débloque un type de bloc cubique utilisable comme bloc de construction en arène.
  - **Équipement** : une pièce d'armure ou une épée (cuir → or → fer → diamant → netherite).
- **`/streak`** : série actuelle et tickets disponibles.
- **`/points [acheter]`** : points de fidélité PvP gagnés à chaque kill en arène
  (`arene.points-par-kill`). `/points acheter` échange `arene.cout-ticket-points` points contre un
  ticket de roue supplémentaire.
- **`/acheterticket`** : envoie un lien cliquable vers la boutique en ligne pour acheter des tickets
  de roue avec de l'argent réel (optionnel, voir section Boutique ci-dessous).
- **`/armee`** : collection de mobs (`x2 Villager`, `x1 Zombie`...). La collection est **permanente** :
  invoquer un mob ne le retire jamais, chaque unité possédée a juste besoin de "recharger" après usage.
- **`/invoquer <mob>`** : fait apparaître une unité disponible du mob choisi comme allié en arène.
  Système de recharge par unité : chaque exemplaire possédé ne peut être réutilisé qu'une fois par
  heure (`arene.invocation-cooldown-secondes`), mais posséder plusieurs fois le même mob permet d'en
  invoquer plusieurs simultanément (chacun a son propre temps de recharge indépendant). Un allié
  n'attaque jamais son invocateur ni les alliés de celui-ci, et cible les autres joueurs à proximité.
- **`/bloc liste`** / **`/bloc choisir <type>`** : consulter et changer son bloc de construction actif.
- **`/equipement liste`** / **`/equipement equiper <numéro>`** : consulter sa collection d'armures/épées
  et choisir manuellement ce qui est porté (sinon la meilleure pièce obtenue s'équipe automatiquement).

### Arène PvP
- **`/arenepvp pos1`** et **`/arenepvp pos2`** *(admin, permission `loyaltymobs.admin`)* : définissent les
  deux coins de la zone d'arène à partir du bloc regardé par l'administrateur. `/arenepvp info` affiche
  la zone actuelle.
- **Chute mortelle sous la zone** : tomber sous le niveau du sol de l'arène (via une brèche dans la
  plateforme) tue instantanément — uniquement par en-dessous, une sortie latérale classique ne tue pas.
- **Dégâts de chute désactivés** à l'intérieur de la zone (hors chute mortelle ci-dessus).
- **Casse de blocs interdite pour tout le monde sauf les admins** (`loyaltymobs.admin`), y compris ses
  propres blocs de construction posés : ils ne disparaissent que via leur minuteur automatique.
- **Blocs de construction en main secondaire (offhand)** : chaque joueur dispose d'un pack de 32 blocs
  du type actif choisi via `/bloc choisir`, donnés dans l'offhand (utilisable automatiquement en même
  temps que l'épée en main principale). Chaque bloc posé disparaît automatiquement après
  `arene.duree-vie-bloc-secondes` (10s par défaut). Une charge se régénère toutes les
  `arene.regen-bloc-secondes` (1s par défaut) jusqu'à revenir à 32.
- **Kit PvP automatique** : à l'entrée dans la zone, le joueur passe en survie (son gamemode d'origine
  est restauré à la sortie), reçoit une épée (1er slot de la hotbar, remplacée par la meilleure épée
  débloquée) et un item d'invocation d'allié (2e slot — clic droit pour ouvrir un menu listant sa
  collection et invoquer directement). Rien de tout ça ne peut être drop, déplacé dans l'inventaire, ni
  perdu en cas de mort ; tout est retiré proprement à la sortie de la zone.
- **Sidebar dédié**, visible uniquement dans la zone : points de fidélité, K/D (persistant), série de
  kills (killstreak) en cours et top 5 des scores des joueurs actuellement dans l'arène.
- **`/classement [retirer]`** *(admin)* : invoque à sa position un hologramme (armor stands, sans
  dépendance externe) affichant le top 5 kills, top 5 morts et top 5 meilleurs K/D — s'actualise tout
  seul à chaque mort en arène.

### Boutique en argent réel (optionnelle)
Achat de tickets de roue avec de l'argent réel, sur le même principe qu'un webshop classique (Stripe +
MySQL partagé), désactivée par défaut (`boutique.enabled: false`). Voir `website-node/README.md` pour
l'installation complète du micro-service, et `website-node/schema.sql` pour le schéma MySQL de
référence. Une fois activée :
- Le site web (voir `website-node/` + section "Tickets de roue" de `index.html`) vend des packs de
  tickets (5 tickets = 1,99 €, 12 = 3,99 €, 30 = 7,99 € par défaut, modifiable en base sans
  redéploiement) via Stripe Checkout.
- Après paiement confirmé (webhook Stripe), le micro-service écrit une ligne dans la table MySQL
  `pending_ticket_grants`.
- Le plugin (`TicketSyncTask`) la lit toutes les `boutique.sync-interval-seconds` (15s par défaut),
  crédite les tickets au joueur (même hors ligne) et le prévient s'il est connecté.
- `/acheterticket` en jeu donne un lien cliquable direct vers la boutique.

Aucune donnée de carte bancaire ne transite par le plugin ni par le micro-service : Stripe héberge
lui-même la page de paiement.

## Compilation

Projet Maven standard utilisant le dépôt PaperMC. Sur une machine avec Maven et un accès internet
(pour télécharger `paper-api`, HikariCP et le driver MySQL) :

```bash
mvn clean package
```

Le jar final (avec HikariCP/MySQL inclus et relocalisés) se trouve dans `target/LoyaltyMobs.jar`.
Place-le dans `plugins/` sur un serveur Paper 1.21, démarre le serveur une fois pour générer
`config.yml`, puis configure l'arène en jeu.

## Mise en route de l'arène

1. En tant qu'op, place-toi et regarde un bloc formant un premier coin de la zone souhaitée, puis
   `/arenepvp pos1`.
2. Regarde le bloc opposé (l'autre coin), puis `/arenepvp pos2`.
3. `/arenepvp info` pour vérifier les coordonnées enregistrées.
4. La zone est immédiatement active : tout joueur qui y entre reçoit le kit PvP et le sidebar, tout
   joueur qui en sort les perd.

## Configuration (`config.yml`)

```yaml
tickets-par-jour: 1
paliers-serie:
  3: 2
  7: 5
  14: 10
  30: 25
arene:
  monde: ""          # rempli automatiquement par /arenepvp pos1/pos2
  duree-vie-bloc-secondes: 10
  regen-bloc-secondes: 1
  invocation-cooldown-secondes: 3600
  points-par-kill: 15
  cout-ticket-points: 200
duree-vie-allie-secondes: 600

# Boutique en argent réel (optionnelle) — voir website-node/README.md
boutique:
  enabled: false
  url: "https://tonsite.fr/boutique-tickets"
  sync-interval-seconds: 15
mysql:
  host: "127.0.0.1"
  port: 3306
  database: "loyaltymobs_shop"
  user: "loyaltymobs_user"
  password: "CHANGE_ME"
  pool-size: 4
  useSSL: false
```

## Permissions

- `loyaltymobs.use` (défaut : tous) — commandes joueur.
- `loyaltymobs.admin` (défaut : op) — `/arenepvp`, `/classement`, et bypass des restrictions de
  casse/pose dans la zone.

## Limites connues / pistes d'amélioration

- Les mobs invoqués passifs (vache, villageois...) suivent le joueur mais n'attaquent pas, faute d'IA
  de combat native pour ces mobs — seuls les mobs normalement hostiles se battent réellement.
- Une seule arène à la fois (les coins sont stockés globalement, pas par nom d'arène).
- `/roue`, `/armee`, `/equipement` sont en texte brut (hors menu d'invocation, qui utilise déjà une
  interface par inventaire) ; une interface par inventaire généralisée serait plus confortable pour de
  grosses collections.
- Le top scores du sidebar n'affiche que les joueurs actuellement dans l'arène (scores de session,
  éphémères) — le K/D et les points, eux, sont persistants.
- La boutique en argent réel nécessite d'héberger soi-même le micro-service Node (`website-node/`) et
  une base MySQL ; ce n'est pas un service clé en main.
