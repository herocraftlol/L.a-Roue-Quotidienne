# LoyaltyMobs

![Version](https://img.shields.io/badge/Version-1.0.43-blue)
![Paper](https://img.shields.io/badge/Paper-1.21-green)
![Java](https://img.shields.io/badge/Java-21-orange)

**LoyaltyMobs** est un plugin Minecraft Paper 1.21 qui transforme votre serveur en une véritable expérience de jeu collaborative. Fidélisez vos joueurs avec un système de **connexion quotidienne**, une **roue de la fortune** pour collectionner des mobs, des blocs, des équipements, des pouvoirs et des flèches à effet, le tout combiné à une **arène PvP** unique en son genre, avec une vraie économie de **points de fidélité**, des **défis** journaliers et globaux, un **système de niveaux**, la **sublimation** de collection et une **boutique en ligne** (Stripe) totalement optionnelle.

## 📥 Télécharger

Téléchargez la dernière version ici : **[Releases](https://github.com/herocraftlol/L.a-Roue-Quotidienne/releases)**

Le fichier `.jar` compilé du plugin et le code source complet sont joints à chaque publication.

## 🆕 Dernières nouveautés (v1.0.43)

Cette version **enrichit considérablement** le contenu de l'arène PvP : deux nouveaux pouvoirs (tour d'échafaudage éphémère et immobilisation totale), un **rééquilibrage complet des flèches à effet** (les effets Cécité et Faiblesse, jugés trop frustrants en PvP, sont retirés et remplacés par des variantes plus lisibles), une **IA du dragon allié** simplifiée et plus robuste, un **système de purge automatique** des anciennes flèches interdites avec compensation en points, ainsi que des **défis** dont les noms indiquent désormais littéralement ce qu'il faut faire.

- **🗼 Nouveau pouvoir : Tour éphémère** *(rare)* — fait apparaître une tour d'échafaudage de 8 blocs sous toi, qui s'effondre automatiquement après 2 minutes. Parfait pour prendre de la hauteur ou fuir un combat. S'arrête proprement si un obstacle bloque la construction.
- **🕸️ Nouveau pouvoir : Immobilisation** *(épique)* — immobilise **totalement** la cible (joueur ou mob adverse le plus proche) pendant 4 secondes. Plus aucun déplacement possible, idéal pour annihiler une charge ou une fuite.
- **🏹 Flèches à effet rééquilibrées** : les flèches Cécité/Faiblesse sont **retirées du jeu** (trop frustrantes en PvP) et remplacées par de nouvelles variantes plus lisibles — *Flèche affamante* (Faim), *Flèche rongeuse* (Wither), *Flèche étourdissante* (Nausée), *Flèche cataclysmique* (Wither + Poison), *Flèche empoisonnée* (Poison), *Flèche toxique* (Poison + Nausée).
- **🧹 Migration automatique des anciennes flèches** : à chaque connexion, les flèches Cécité/Faiblesse encore en possession d'un joueur sont **retirées et compensées** en points de fidélité (20 × tier de la flèche). Le joueur est prévenu en jeu avec le montant compensé.
- **🐉 IA du dragon allié simplifiée** : le dragon fonce désormais droit sur sa cible, mord à portée (toujours 1,5 s de cooldown entre deux morsures) et la tête reste en permanence tournée vers elle. Beaucoup plus robuste : plus de risque de plantage sur vecteur nul ou de blocage dans le décor près du sol.
- **📍 Position d'invocation corrigée** (`SpawnUtils`) : un mob invoqué trouve désormais une vraie surface solide (2 blocs d'air libres au-dessus) au lieu de simplement vérifier le bloc sous les pieds — fini les mobs encastrés sur pente ou en bordure d'arène.
- **🎯 Alerte de seuil de points** : dès que tu franchis un multiple du coût d'un ticket (`arene.cout-ticket-points`), un message te rappelle que tu peux `/points acheter` pour l'échanger contre un ticket de roue.
- **📜 Défis aux noms littéraux** : tous les défis indiquent désormais littéralement ce qu'il faut faire (« Éliminer 50 joueurs », « Poser 50 blocs », « Se connecter 14 jours d'affilée »...) au lieu de titres thématiques — beaucoup plus clair d'un coup d'œil.
- **🛠 Compilation propre** : Paper API 1.21.1+ (attributs unifiés et item flags modernisés), import `fr.fidelmobs.Cles` correctement référencé, lambdas sans warning.

---

## 📜 Changelog

### v1.0.43
- **Nouveau pouvoir** : Tour éphémère (rare) — tour d'échafaudage de 8 blocs, s'effondre après 2 min
- **Nouveau pouvoir** : Immobilisation (épique) — cible totalement immobilisée pendant 4 s
- **Flèches rééquilibrées** : suppression des flèches Cécité et Faiblesse, remplacées par Faim / Wither / Nausée / Poison
- **Migration automatique** : les flèches Cécité/Faiblesse restantes en collection sont purgées à la connexion et remplacées par des points de fidélité
- **Dragon allié** : IA simplifiée (vol droit vers la cible + morsure à portée, 1,5 s de cooldown), beaucoup plus robuste
- **SpawnUtils** : recherche d'une vraie surface solide avec 2 blocs d'air libres, au lieu d'un simple contrôle sous les pieds
- **Alerte de seuil de points** : notification automatique dès qu'on peut acheter un ticket avec ses points
- **Défis** : noms désormais littéraux (« Éliminer X joueurs » au lieu de titres thématiques)
- **Compilation Paper 1.21.1+** : `Attribute.GENERIC_MAX_HEALTH`, `ItemFlag.HIDE_ADDITIONAL_TOOLTIP`, import `Cles` ajouté

### v1.0.42
- Correction de `Attribute.MAX_HEALTH` → `GENERIC_MAX_HEALTH` (Paper 1.21.1+) dans `PowerRegistry`
- Correction de `ItemFlag.HIDE_POTION_EFFECTS` → `HIDE_ADDITIONAL_TOOLTIP` (Paper 1.20.5+) dans `ArrowRegistry`
- Import manquant de `fr.fidelmobs.Cles` ajouté dans `AllyListener`
- Variables locales rendues effectivement finales pour les lambdas de `BlockRegistry` et `MobRegistry`
- Description courte du `plugin.yml` mise à jour pour refléter toutes les fonctionnalités
- Version bumpée à `1.0.42` dans `pom.xml` et `plugin.yml`

### v1.0.41
- Nouvelle boutique `/shop` : achat direct de mobs, blocs, pouvoirs et équipements bruts contre des points de fidélité
- Nouvelle commande `/sacrifier` : sublimation d'une catégorie (ou tout) de la collection contre des points (irréversible, confirmation 30 s)
- Hologramme personnel au-dessus de la tête de chaque joueur en arène (stats visibles uniquement par lui)
- `/invoquer` coûte désormais des points de fidélité proportionnels à la rareté du mob
- Économie des points harmonisée (boutique / sublimation / coûts) autour du prix du ticket de roue
- Corrections de compatibilité avec l'API Paper 1.21 (attributs, item flags) et optimisations

### v1.0.40
- Correction de l'ordre des onglets du menu équipement (chaque onglet affiche le bon emplacement)
- Infobulles des flèches à effet épurées (effets de potion masqués, affichage plus propre)
- Ajustements internes de compatibilité avec l'API Paper 1.21

### v1.0.39
- Nouveau système d'équipements enchantés à collectionner (variante brute / enchantée faible / enchantée forte par matériau)
- Menu d'équipement repensé avec onglets par emplacement (Arme, Casque, Plastron, Jambières, Bottes, Flèches)
- Teinte des pièces en couleur selon leur rareté
- Reconnaissance des doublons à la roue basée sur la signature complète (matériau + enchantements)
- Rééquilibrage des récompenses de défis (points réduits, tickets réservés aux défis légendaires)
- Correction de la gestion des cibles invalides des mobs alliés

### v1.0.38
- Système de niveaux avec progression par XP
- Défis quotidiens et globaux avec récompenses variées
- Améliorations de stabilité et corrections de bugs
- Améliorations de l'arène PvP (combat et gestion des blocs)
- Nouvelles flèches magiques avec effets supplémentaires
- Système de pouvoirs spéciaux optimisé
- Optimisations générales des performances

---

## Fonctionnalités

### Fidélisation
- **Connexion quotidienne** : série de jours consécutifs suivie automatiquement, avec distribution de tickets (`tickets-par-jour` + bonus de palier définis dans `config.yml`). Un rappel s'affiche à chaque connexion tant qu'il reste des tickets non utilisés.
- **`/roue`** : consomme un ticket et donne une récompense de **chaque** catégorie à chaque lancer (mob + bloc + équipement + pouvoir), affichée avec une mise en forme colorée selon la rareté et une petite fanfare (son + titre à l'écran) pour les tirages épiques/légendaires. Le stuff de base (cuir/bois) ne peut jamais s'obtenir sans enchantement.
  - **Mob** : un allié ajouté **définitivement** à la collection (voir `/armee` et `/invoquer`).
  - **Bloc** : débloque un type de bloc cubique utilisable comme bloc de construction en arène.
  - **Équipement** : une pièce d'armure ou une épée (cuir → or → fer → diamant → netherite).
  - **Pouvoir** : un pouvoir spécial à collectionner et à utiliser en arène.
- **`/streak`** : la série actuelle et les tickets disponibles.
- **`/points [acheter]`** : points de fidélité gagnés à chaque kill en arène (`arene.points-par-kill`). `/points acheter` échange `arene.cout-ticket-points` points contre un ticket de roue supplémentaire.
- **`/shop`** : la boutique des points de fidélité, pour acheter directement mobs, blocs, pouvoirs et équipement avec vos points gagnés en jeu.
- **`/sacrifier <mobs|equipements|pouvoirs|blocs|tout> [confirmer]`** : sublimer une catégorie (ou toute votre collection) contre des points, en la remettant à zéro (irréversible).
- **`/acheterticket`** : envoie un lien cliquable vers la boutique en ligne pour acheter des tickets de roue avec de l'argent réel (optionnel, voir section Boutique ci-dessous).
- **`/armee`** : collection de mobs (`x2 Villager`, `x1 Zombie`...). La collection est **permanente** : invoquer un mob ne le retire jamais, chaque unité possédée a juste besoin de "charger" après usage.
- **`/invoquer <mob>`** : fait apparaître une unité disponible du mob choisi comme allié en arène (coûte des points de fidélité proportionnels à sa rareté). Système de recharge par unité : chaque exemplaire possédé ne peut être réutilisé qu'une fois après le temps de recharge (`arene.invocation-cooldown-secondes`), mais posséder plusieurs fois le même mob permet d'en invoquer plusieurs simultanément (chacun a son propre temps de recharge indépendant). Un allié n'attaque jamais son invocateur ni les alliés de celui-ci, et cible les autres joueurs à proximité.
- **`/bloc liste`** / **`/bloc choisir <type>`** : consulter et changer son bloc de construction actif.
- **`/equipement liste`** / **`/equipement equiper <numéro>`** : consulter sa collection d'armures et d'épées et choisir manuellement ce qui est porté (sinon la meilleure pièce obtenue s'équipe automatiquement).

### Arène PvP
- **`/arenepvp pos1`** et **`/arenepvp pos2`** *(admin, permission `loyaltymobs.admin`)* : définissent les deux coins de la zone d'arène à partir du bloc regardé par l'administrateur. `/arenepvp info` affiche la zone actuelle.
- **Chute mortelle sous la zone** : tomber sous le niveau du sol de l'arène (via une brèche dans la plateforme) tue instantanément — uniquement par en-dessous, une sortie latérale classique ne tue pas.
- **Dégâts de chute désactivés** à l'intérieur de la zone (hors chute mortelle ci-dessus).
- **Casse de blocs interdite pour tout le monde sauf les administrateurs** (`loyaltymobs.admin`), y compris ses propres blocs de construction posés : ils ne disparaissent que via leur minuteur automatique.
- **Blocs de construction en main secondaire (offhand)** : chaque joueur dispose d'un pack de 32 blocs du type actif choisi via `/bloc choisir`, donnés dans l'offhand (utilisables automatiquement en même temps que l'épée en main principale). Chaque bloc posé disparaît automatiquement après `arene.duree-vie-bloc-secondes` (10 s par défaut). Une charge se régénère toutes les `arene.regen-bloc-secondes` (1 s par défaut) jusqu'à revenir à 32.
- **Kit PvP automatique** : à l'entrée dans la zone, le joueur passe en survie (son gamemode d'origine est restauré à la sortie), reçoit une épée (1er slot de la hotbar, remplacée par la meilleure épée débloquée) et un item d'invocation d'allié (2e slot — clic droit pour ouvrir un menu listant sa collection et invoquer directement). Rien de tout ça ne peut être jeté, déplacé dans l'inventaire, ni perdu en cas de mort ; tout est retiré proprement à la sortie de la zone.
- **Barre latérale (sidebar) dédiée**, visible uniquement dans la zone : points de fidélité, K/D (persistant), série de kills (killstreak) en cours et top 5 des scores des joueurs actuellement présents dans l'arène.
- **Hologramme personnel** au-dessus de la tête de chaque joueur en arène, affichant ses statistiques (kills, morts, K/D, niveau, tickets disponibles...), visible **uniquement par lui**.
- **`/classement [retirer]`** *(admin)* : invoque à sa position un hologramme (armor stands, sans dépendance externe) affichant le top 5 kills, top 5 morts et top 5 meilleurs K/D — s'actualise tout seul à chaque mort en arène.

### Économie & progression
- **Points de fidélité** : gagnés à chaque kill en arène, utilisés pour acheter des tickets (`/points acheter`), invoquer des mobs (`/invoquer`), ou acheter directement des objets à la boutique (`/shop`).
- **Sublimation (`/sacrifier`)** : convertissez des catégories de collection contre des points — vous remettez à zéro une catégorie pour pouvoir réinvestir vos gains ailleurs.
- **Défis (`/defi`)** : défis quotidiens et globaux avec récompenses en points et en tickets.
- Le prix des objets à la boutique et la valeur de leur sublimation suivent une logique de rareté cohérente, calibrée autour du coût d'un ticket.

### Boutique en argent réel (optionnelle)
Achat de tickets de roue avec de l'argent réel, sur le même principe qu'un magasin classique (Stripe + MySQL partagé), désactivée par défaut (`boutique.enabled: false`). Voir `website-node/README.md` pour l'installation complète du micro-service et `website-node/schema.sql` pour le schéma MySQL de référence. Une fois activée :
- Le site web (voir `website-node/`) vend des packs de tickets (5 tickets = 1,99 €, 12 = 3,99 €, 30 = 7,99 € par défaut, modifiable en base sans redéploiement) via Stripe Checkout.
- Après paiement confirmé (webhook Stripe), le micro-service écrit une ligne dans la table MySQL `pending_ticket_groups`.
- Le plugin (`TicketSyncTask`) la lit toutes les `boutique.sync-interval-secondes` (15 s par défaut), crédite les tickets au joueur (même hors ligne) et le prévient s'il est connecté.
- `/acheterticket` en jeu donne un lien cliquable direct vers la boutique.

Aucune donnée de carte bancaire ne transite par le plugin ni par le micro-service : Stripe héberge lui-même la page de paiement.

## Compilation

Projet Maven standard utilisant le dépôt PaperMC. Sur une machine avec Maven et un accès internet (pour télécharger `paper-api`, HikariCP et le driver MySQL) :

```bash
mvn clean package
```

Le jar final (avec HikariCP/MySQL inclus et relocalisés) se trouve dans `target/LoyaltyMobs.jar`. Place-le dans `plugins/` sur un serveur Paper 1.21, démarre le serveur une fois pour générer `config.yml`, puis configure l'arène en jeu.

## Mise en route de l'arène

1. En tant qu'op, place-toi et regarde un bloc formant un premier coin de la zone souhaitée, puis `/arenepvp pos1`.
2. Regarde le bloc opposé (l'autre coin), puis `/arenepvp pos2`.
3. `/arenepvp info` pour vérifier les coordonnées enregistrées.
4. La zone est immédiatement active : tout joueur qui y entre reçoit le kit PvP et la sidebar, tout joueur qui en sort les perd.
5. Vérifiez dans `config.yml` les paramètres de l'arène (points par kill, coût d'un ticket, temps de recharge des pouvoirs et des invocations) pour qu'ils correspondent à votre jeu.

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
  arc-cooldown-secondes: 3
  pouvoir-cooldown-secondes: 300
  points-par-kill: 15
  cout-ticket-points: 1500
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

- `loyaltymobs.use` (défaut : tous) — commandes du joueur.
- `loyaltymobs.admin` (défaut : op) — `/arenepvp`, `/classement`, et bypass des restrictions de cassage/pose dans la zone.

## Limites connues / pistes d'amélioration

- Les mobs invoqués passifs (vache, villageois...) suivent le joueur mais n'attaquent pas, faute d'IA de combat native pour ces mobs — seuls les mobs normalement hostiles se battent réellement.
- Une seule arène à la fois (les coins sont stockés globalement, pas par nom d'arène).
- Plusieurs commandes (`/roue`, `/armee`, `/equipement`) sont en texte brut (hors menus d'invocation, de boutique et de défis qui utilisent des interfaces par inventaire) ; une interface généralisée serait plus confortable pour les grosses collections.
- La boutique en argent réel nécessite d'héberger soi-même le micro-service Node (`website-node/`) et une base MySQL ; ce n'est pas un service clé en main.