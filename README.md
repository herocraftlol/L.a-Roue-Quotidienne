# 🎰 LoyaltyMobs - La Roue Quotidienne

**LoyaltyMobs** est un plugin Paper/Minecraft 1.21 qui transforme votre serveur en une expérience de jeu captivante basée sur la fidélité des joueurs !

## ✨ Ce que fait le plugin

LoyaltyMobs récompenser vos joueurs pour leur loyauté quotidienne avec un système de roue de la fortune palpitante, des collections de mobs à invoquer en combat, et une arène PvP exclusive avec des mécaniques de jeu uniques.

### 🎁 Système de Fidélisation
- **Connexion quotidienne** : Reward vos joueurs avec des tickets quotidiens basés sur leur série de connexions
- **Série de jours consécutifs** avec des bonus de palier (3 jours, 7 jours, 14 jours, 30 jours...)
- Rappels automatiques quand des tickets non utilisés sont disponibles

### 🎡 La Roue de la Fortune
Chaque tour de roue (consommant 1 ticket) offre **3 récompenses** :
- **Mob** : Ajouté définitivement à votre collection personnelle
- **Bloc** : Débloque un type de bloc de construction exclusif pour l'arène
- **Équipement** : Armure ou épée avec des enchantements variés

Les récompenses sont colorées selon leur rareté : Commun 🟢, Rare 🔵, Épique 🟣 ou Légendaire 🟡. Les tirages épiques et légendaires sont célébrés avec des effets visuels et sonores impressionnants !

### ⚔️ Arène PvP Exclusive
- Zone PvP configurée avec des règles uniques
- **Blocs de construction éphémères** : Posez des blocs qui disparaissent après un certain temps
- **Kit PvP automatique** : Épée enchantée + invocation d'alliés à l'entrée
- **Sidebar dédié** : Affiche vos points, K/D et killstreak
- **Hologrammes de classement** : Top 5 kills, morts et meilleur K/D
- **Mobs alliés invoqués** : Votre armée vous accompagne au combat !

### 🏪 Boutique Optionnelle
- Boutique en ligne pour acheter des tickets avec de l'argent réel (via Stripe)
- Synchronisation automatique avec la base de données MySQL
- Transactions sécurisées et automatiques

## 📋 Fonctionnalités Principales

| Commande | Description |
|----------|-------------|
| `/roue` | Tourne la roue pour obtenir mob + bloc + équipement |
| `/armee` | Affiche votre collection de mobs |
| `/invoquer <mob>` | Invoque un mob ally pour combattre à vos côtés |
| `/streak` | Voir votre série de connexions et tickets |
| `/bloc choisir` | Sélectionner votre bloc de construction actif |
| `/equipement` | Gérer votre collection d'armures/épées |
| `/points` | Points de fidélité PvP et échange contre des tickets |
| `/defi` | Accomplir des défis pour des récompenses bonus |
| `/classement` | Afficher le top PvP en hologramme |
| `/arenepvp` | [Admin] Configurer la zone d'arène |

## 🔧 Installation

1. Téléchargez le fichier `.jar` depuis la section [Releases](../../releases)
2. Placez-le dans le dossier `plugins/` de votre serveur Paper 1.21
3. Redémarrez le serveur
4. Configurez `config.yml` selon vos besoins
5. Définissez la zone d'arène avec `/arenepvp pos1` et `/arenepvp pos2`

## 💾 Compilation

```bash
mvn clean package
```

Le fichier JAR compilé sera dans `target/LoyaltyMobs.jar`

## 📝 Configuration

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

## 🔑 Permissions

- `loyaltymobs.use` (défaut : tous) — Commandes joueur
- `loyaltymobs.admin` (défaut : op) — Commandes d'administration

## 📄 Licence

Ce plugin est fourni tel quel. Consultez le code source pour plus de détails.
