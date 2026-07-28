# 🎰 LoyaltyMobs

<div align="center">

![Paper 1.21](https://img.shields.io/badge/Paper-1.21-brightgreen)
![Java 21](https://img.shields.io/badge/Java-21-blue)
![License](https://img.shields.io/badge/License-Custom-orange)

**Un plugin de fidélisation innovant pour serveurs Minecraft Paper 1.21 — combine des récompenses de connexion quotidiennes, un système de roue style gacha, et une arène PvP entièrement personnalisable !**

</div>

---

## ✨ Présentation

**LoyaltyMobs** est un plugin complet conçu pour récompenser la fidélité des joueurs et créer un sentiment de progression à long terme sur votre serveur Minecraft.

Découvrez une expérience unique où chaque connexion compte, où la roue tourne pour vous offrir des mobs, blocs et équipements rares, et où une arène PvP personnalisée attend les guerriers les plus déterminés !

---

## 🎁 Système de Fidélisation

### Connexion Quotidienne
- **Suivi automatique des séries** de connexions journalières consécutives
- **Distribution de tickets** basée sur votre série (bonus de palier à 3, 7, 14 et 30 jours)
- **Notifications intelligentes** vous rappelant vos tickets non utilisés

### 🎰 La Roue Quotidienne
Tournez la roue et tentez de remporter :
- **Mobs** 🐾 — Ajoutez des alliés permanents à votre armée !
- **Blocs** 🧱 — Débloquez des blocs de construction exclusifs pour l'arène
- **Équipements** ⚔️ — Collectez armures et épées de plus en plus puissantes

> 💡 *Chaque ticket vous donne une chance dans chaque catégorie — le stuff de base (cuir/bois) inclut toujours des enchantements !*

### Commandes de Fidélisation
| Commande | Description |
|----------|-------------|
| `/streak` | Affiche votre série et vos tickets disponibles |
| `/roue` | Tourne la roue (consomme un ticket) |
| `/armee` | Consultez votre collection de mobs |
| `/invoquer <mob>` | Invoquez un allié pour combattre à vos côtés |
| `/bloc liste/choisir` | Gérez vos blocs de construction |
| `/equipement liste/equiper` | Consultez et équipez votre matériel |
| `/points [acheter]` | Points de fidélité PvP et achat de tickets |
| `/acheterticket` | Lien vers la boutique (argent réel) |

---

## ⚔️ Arène PvP

Entrez dans la zone d'arène et vivez une expérience PvP独有的 avec :

- **Kit PvP automatique** — Épée optimisée selon votre progression + item d'invocation d'alliés
- **Construction en offhand** — Posez des blocs avec votre main secondaire (32 blocs max, régénération automatique)
- **Système de chute mortelle** — Défendez le fond de l'arène ou mourrez !
- **Sidebar dédié** — Stats en temps réel : points, K/D, killstreak, top 5 du serveur
- **Hologrammes de classement** — Top kills, morts et K/D actualisés en temps réel

### Configuration de l'Arène
```bash
/arenepvp pos1          # Définir le coin 1 (admin)
/arenepvp pos2          # Définir le coin 2 (admin)
/arenepvp info          # Vérifier la zone
/classement [retirer]   # Afficher les classements (admin)
```

---

## 🛒 Boutique en Argent Réel (Optionnelle)

Proposez l'achat de tickets de roue avec de l'argent réel via **Stripe** :

- **Synchronisation MySQL** automatique entre le site web et le serveur
- **Crédits instantanés** même pour les joueurs hors ligne
- **Aucune donnée bancaire** ne transite par vos serveurs — Stripe gère tout !

*Désactivée par défaut — voyez `website-node/README.md` pour l'installation.*

---

## 📦 Installation

### Prérequis
- Serveur **Paper ou Purpur 1.21+**
- **Java 21**
- **Maven** (pour compiler)

### Compilation
```bash
mvn clean package
```
Le fichier `.jar` sera dans `target/LoyaltyMobs.jar`

### Mise en Place
1. Placez `LoyaltyMobs.jar` dans le dossier `plugins/` de votre serveur
2. Redémarrez le serveur (le `config.yml` sera généré automatiquement)
3. Configurez l'arène avec `/arenepvp pos1` et `/arenepvp pos2`

---

## ⚙️ Configuration Rapide

```yaml
tickets-par-jour: 1
paliers-serie:
  3: 2
  7: 5
  14: 10
  30: 25
arene:
  duree-vie-bloc-secondes: 10
  invocation-cooldown-secondes: 3600
  points-par-kill: 15
  cout-ticket-points: 200
boutique:
  enabled: false
  url: "https://votresite.com/boutique"
```

---

## 🔐 Permissions

| Permission | Description | Défaut |
|------------|-------------|--------|
| `loyaltymobs.use` | Commandes joueur | Tous |
| `loyaltymobs.admin` | Configuration arène, /classement | OP |

---

## 🚀 Auteur

Développé avec ❤️ pour la communauté Minecraft française.

---

<div align="center">

**⭐ N'hésitez pas à star ce projet si vous l'appréciez !**

</div>
