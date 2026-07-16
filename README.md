# LoyaltyMobs

Plugin Paper 1.21 de fidélisation par connexions journalières, avec collection de mobs/blocs/équipements
à la "roue" façon cartes à collectionner, et une arène PvP configurable avec ses propres règles.

## Fonctionnalités

### Fidélisation
- **Connexion quotidienne** : série de jours consécutifs suivie automatiquement, avec distribution de
  tickets (`tickets-par-jour` + bonus de palier définis dans `config.yml`).
- **`/roue`** : consomme un ticket et tire une récompense aléatoire parmi 3 catégories pondérables
  (`roue.poids-categories` dans `config.yml`) :
  - **Mob** : un allié invocable en arène, classé en 5 raretés.
  - **Bloc** : débloque un type de bloc cubique utilisable comme bloc de construction en arène.
  - **Équipement** : une pièce d'armure ou une épée (cuir → or → fer → diamant → netherite), avec une
    chance d'être enchantée (auquel cas elle est considérée un cran plus rare).
- **`/streak`** : série actuelle et tickets disponibles.
- **`/armee`** : collection de mobs (`x2 Villager`, `x1 Zombie`...).
- **`/invoquer <mob>`** : fait apparaître un mob de la collection comme allié en arène (le mob est
  retiré de la collection, il n'attaque jamais son invocateur ni les alliés de celui-ci).
- **`/bloc liste`** / **`/bloc choisir <type>`** : consulter et changer son bloc de construction actif.
- **`/equipement liste`** / **`/equipement equiper <numéro>`** : consulter sa collection d'armures/épées
  et choisir manuellement ce qui est porté (sinon la meilleure pièce obtenue s'équipe automatiquement).

### Arène PvP
- **`/arenepvp pos1`** et **`/arenepvp pos2`** *(admin, permission `loyaltymobs.admin`)* : définissent les
  deux coins de la zone d'arène à partir du bloc regardé par l'administrateur (comme un `//pos1`/`//pos2`
  de WorldEdit, mais basé sur le bloc visé plutôt que la position du joueur). `/arenepvp info` affiche la
  zone actuelle. Les admins peuvent toujours casser/poser librement dans la zone pour l'aménager.
- **Dégâts de chute désactivés** à l'intérieur de la zone.
- **Casse de blocs interdite**, sauf pour un joueur cassant prématurément un bloc qu'il a lui-même posé.
- **Blocs à poser limités** : chaque joueur dispose d'un pack de 32 blocs (du type actif choisi via
  `/bloc choisir`). Chaque bloc posé disparaît automatiquement après 10 secondes
  (`arene.duree-vie-bloc-secondes`). Dès qu'un joueur commence à poser, une charge est régénérée toutes
  les 3 secondes jusqu'à revenir à 32.
- **Kit PvP automatique** : à l'entrée dans la zone, chaque joueur reçoit une armure de cuir + épée en
  bois par défaut (incassable), remplacée par les meilleures pièces d'équipement qu'il a débloquées et
  équipées. Ce kit ne peut pas être drop ni déplacé dans l'inventaire tant que le joueur est dans la
  zone, et est automatiquement retiré dès qu'il en sort.
- **Sidebar dédié**, visible uniquement dans la zone : série de kills (killstreak) en cours et top 5
  des scores des joueurs actuellement dans l'arène. Les scores sont **éphémères** : ils ne sont jamais
  sauvegardés sur disque et repartent à 0 à la déconnexion du joueur.

## Compilation

Projet Maven standard utilisant le dépôt PaperMC. Sur une machine avec Maven et un accès internet
(pour télécharger `paper-api`) :

```bash
mvn clean package
```

Le jar final se trouve dans `target/LoyaltyMobs.jar`. Place-le dans `plugins/` sur un serveur Paper 1.21,
démarre le serveur une fois pour générer `config.yml`, puis configure l'arène en jeu.

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
roue:
  poids-categories:
    mob: 60
    bloc: 20
    equipement: 20
arene:
  monde: ""          # rempli automatiquement par /arenepvp pos1/pos2
  duree-vie-bloc-secondes: 10
duree-vie-allie-secondes: 600
```

## Permissions

- `loyaltymobs.use` (défaut : tous) — commandes joueur.
- `loyaltymobs.admin` (défaut : op) — `/arenepvp`, et bypass des restrictions de casse/pose dans la zone.

## Limites connues / pistes d'amélioration

- Les mobs invoqués passifs (vache, villageois...) suivent le joueur mais n'attaquent pas, faute d'IA
  de combat native pour ces mobs — seuls les mobs normalement hostiles se battent réellement.
- Une seule arène à la fois (les coins sont stockés globalement, pas par nom d'arène).
- `/roue`, `/armee`, `/equipement` sont en texte brut ; une interface par inventaire serait plus
  confortable pour de grosses collections.
- Le classement du sidebar n'affiche que les joueurs actuellement dans l'arène (cohérent avec le
  principe de scores éphémères).
