# LoyaltyMobs

Plugin Paper 1.21 de fidélisation par connexions journalières : les joueurs qui se
connectent plusieurs jours d'affilée gagnent des **tickets**, qu'ils dépensent à la
**roue** pour obtenir un mob aléatoire (pondéré par rareté, façon cartes à collectionner).
Les mobs obtenus s'accumulent dans une collection personnelle, consultable et utilisable
pour invoquer des alliés en arène PvP (à usage unique : le mob est consommé de la collection
au moment de l'invocation).

## Fonctionnalités

- **Connexion quotidienne** : à chaque connexion sur un nouveau jour calendaire, le plugin
  vérifie si le joueur s'est connecté la veille (série continue) ou non (série repartant à 1),
  et distribue des tickets (`tickets-par-jour` + bonus de palier définis dans `config.yml`).
- **`/roue`** : consomme un ticket et tire un mob aléatoire parmi tous les `EntityType`
  invocables, classés en 5 raretés (Commun, Peu commun, Rare, Épique, Légendaire) avec des
  probabilités décroissantes. Le mob rejoint la collection du joueur.
- **`/armee`** : affiche la collection du joueur, triée par rareté puis par nom
  (ex. `x2 Villager`, `x1 Zombie`, `x5 Wolf`...).
- **`/invoquer <mob>`** : si le joueur possède au moins un exemplaire du mob et se trouve
  dans la zone d'arène définie dans `config.yml`, fait apparaître ce mob à ses côtés, le
  retire de sa collection, et le lie à lui comme allié :
  - il n'attaque jamais son invocateur ni les alliés de celui-ci ;
  - il peut cibler les autres joueurs (comportement hostile standard de Minecraft pour les
    mobs hostiles — les mobs passifs comme la vache ne deviennent pas combattants, ils
    suivent simplement le joueur) ;
  - il disparaît à sa mort, à la déconnexion de son propriétaire, ou après la durée de vie
    maximale configurée.
- **`/streak`** : affiche la série actuelle et le nombre de tickets disponibles.

## Compilation

Ce projet est un module Maven standard utilisant le dépôt PaperMC. Sur une machine avec
Maven et un accès internet (pour télécharger `paper-api`) :

```bash
mvn clean package
```

Le jar final se trouve dans `target/LoyaltyMobs.jar`. Place-le dans le dossier `plugins/`
de ton serveur Paper 1.21, démarre le serveur une fois pour générer `config.yml`, puis
ajuste les coordonnées de l'arène.

## Configuration (`config.yml`)

```yaml
tickets-par-jour: 1
paliers-serie:
  3: 2
  7: 5
  14: 10
  30: 25
arene:
  activer: true
  monde: "world"
  coin1: {x: -50, y: 0, z: -50}
  coin2: {x: 50, y: 255, z: 50}
rayon-recherche-cible: 20
duree-vie-allie-secondes: 600
```

- `arene.activer: false` autorise `/invoquer` n'importe où (déconseillé pour du PvP équilibré).
- Ajuste `coin1`/`coin2` aux coins opposés de ta zone d'arène (comme un `//pos1` / `//pos2` de WorldEdit).
- `duree-vie-allie-secondes: 0` = pas de limite de temps, le mob reste tant qu'il n'est pas tué.

## Pistes d'amélioration possibles

- Ajouter une interface graphique (inventaire) pour `/armee` et `/invoquer` plutôt que du texte.
- Ajouter un cooldown ou une animation pour `/roue` (ex. via le widget interactif Bukkit ou un titre animé).
- Limiter `/invoquer` au nombre d'alliés simultanés par joueur.
- Sauvegarder la collection dans une base de données (SQLite/MySQL) si le nombre de joueurs est important,
  plutôt qu'un fichier YAML par joueur.
