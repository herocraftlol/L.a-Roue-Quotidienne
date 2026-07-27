# 🎰 LoyaltyMobs — La Roue Quotidienne

**LoyaltyMobs** est un plugin Minecraft Paper 1.21 de fidélisation qui transforme chaque connexion en une expérience excitante ! 

Fidélisez vos joueurs avec un système de **roue quotidienne** façon cartes à collectionner, où chaque tour peut leur procurer des mobs alliés, des blocs de construction exclusifs, des équipements de plus en plus puissants, des flèches magiques à effets, et des pouvoirs spéciaux dévastateurs. Le tout dans une **arène PvP compétitive** avec son propre écosystème de combat.

---

## ✨ Ce que le plugin vous offre

### 🎁 Système de Fidélisation par la Roue
- **Connexion quotidienne** : chaque joueur reçoit des tickets de roue en fonction de sa série de connexion (bonus de palier pour les séries longues)
- **La Roue (/roue)** : un ticket = trois récompenses guaranteed (mob + bloc + équipement), avec des effets visuels et sonores spectaculaires pour les tirages Épique/Légendaire
- **5 catégories de récompenses** :
  - 🧟 **Mobs alliés** : collectez des dizaines de mobs qui vous suivront au combat
  - 🧱 **Blocs de construction** : débloquez des blocs exclusifs pour l'arène
  - ⚔️ **Équipements** : progression armure/épée de cuir → netherite avec enchantements
  - 🏹 **Flèches magiques** : 5 niveaux d'effets (ralentissement, poison, affaiblissement, dégâts instantanés)
  - ⚡ **Pouvoirs spéciaux** : vitesse, saut, force, etc. avec charges multiples

### ⚔️ Arène PvP Entièrement Configurable
- Zone PvP délimitée avec `/arenepvp pos1/pos2`
- **Kit automatique** : armure, épée, arc avec flèches magiques, et invocation d'alliés
- **Construction en combat** : blocs qui disparaissent après X secondes, recharge automatique
- **Chute mortelle** : mechanic de combat supplémentaire sous la plateforme
- **Sidebar dynamique** : scores, K/D, killstreak en temps réel
- **Classements holographiques** (/classement) pour le top kills/décès/K-D

### 🛒 Boutique en Ligne (Optionnelle)
- Synchronisation Stripe + MySQL pour les achats de tickets en argent réel
- Linkage automatique des achats au compte du joueur en jeu
- Aucune donnée bancaire ne transite par le plugin

### 🔧 Système d'Armée d'Alliés
- Collection permanente de mobs (/armee) — jamais perdus même après utilisation
- Invocation avec temps de recharge indépendant par unité
- Les alliés n'attaquent pas leur maître ni ses alliés
- Système de rechargement intelligent : plus vous en avez, plus vous pouvez en invoquer simultanément

---

## 📋 Fonctionnalités Détaillées

### Commandes Joueurs
| Commande | Description |
|----------|-------------|
| `/roue` | Tourne la roue et gagne des récompenses (mob + bloc + équipement) |
| `/streak` | Affiche votre série de connexion et tickets disponibles |
| `/points [acheter]` | Affiche/achète des tickets avec vos points PvP |
| `/armee` | Liste votre collection de mobs |
| `/invoquer <mob>` | Invoque un mob allié en arène |
| `/bloc liste` / `choisir` | Gérez vos blocs de construction |
| `/equipement liste` / `equiper` | Gérez votre collection d'équipements |
| `/acheterticket` | Lien vers la boutique pour acheter des tickets |
| `/classement` | Affiche le top 5 PvP (admin) |

### Arène PvP
- **Zone délimitée** avec `/arenepvp pos1/pos2` (admin)
- **Kit automatique** à l'entrée : armure, épée, arc, invocation d'alliés
- **Construction en combat** : blocs temporaires avec recharge
- **Sidebar temps réel** : K/D, killstreak, scores des joueurs dans l'arène
- **Classement holographique** (/classement) — top kills/décès/K-D

### Boutique en Argent Réel
- Achat de tickets via Stripe Checkout
- Synchronisation MySQL automatique
- Aucune donnée bancaire ne transite par le plugin

---

## 🚀 Installation

1. Placez `LoyaltyMobs.jar` dans le dossier `plugins/` de votre serveur Paper 1.21
2. Redémarrez le serveur pour générer `config.yml`
3. Configurez l'arène avec `/arenepvp pos1` et `/arenepvp pos2` (en tant qu'admin)

## Compilation

```bash
mvn clean package
```

Le jar final se trouve dans `target/LoyaltyMobs.jar`.

## Configuration (`config.yml`)

```yaml
tickets-par-jour: 1
paliers-serie:
  3: 2
  7: 5
  14: 10
  30: 25
arene:
  monde: ""
  duree-vie-bloc-secondes: 10
  regen-bloc-secondes: 1
  invocation-cooldown-secondes: 3600
  points-par-kill: 15
  cout-ticket-points: 200
duree-vie-allie-secondes: 600
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

- `loyaltymobs.use` (défaut : tous) — commandes joueur
- `loyaltymobs.admin` (défaut : op) — `/arenepvp`, `/classement`, bypass restrictions zone
