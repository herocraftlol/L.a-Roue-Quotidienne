package fr.fidelmobs.arena;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Recherche d'une position de spawn sûre devant le joueur : toujours DEBOUT SUR un bloc
 * solide, jamais à l'intérieur d'un bloc. Utilisé pour l'invocation de mobs alliés
 * (commande /invoquer et item d'invocation).
 *
 * L'ancienne version se contentait de vérifier le bloc sous les pieds AU NIVEAU Y DU
 * JOUEUR, sans jamais chercher où se trouve réellement le sol à cet endroit : sur une
 * pente, une pyramide, ou tout terrain qui monte/descend, le mob finissait souvent
 * encastré à moitié dans un bloc. On cherche maintenant, pour chaque colonne candidate,
 * la vraie surface solide la plus proche de la hauteur du joueur (en scannant vers le
 * haut puis vers le bas), avec au moins 2 blocs d'air libres au-dessus pour que le mob
 * tienne debout sans être coincé.
 */
public final class SpawnUtils {

    private static final int PORTEE_VERTICALE = 4;

    private SpawnUtils() {
    }

    public static Location trouverPositionSpawnValide(Player player) {
        Location base = player.getLocation();
        Vector direction = base.getDirection().clone();
        direction.setY(0);
        if (direction.lengthSquared() < 0.0001) {
            direction = new Vector(1, 0, 0);
        } else {
            direction.normalize();
        }

        for (double distance = 3.0; distance >= 1.0; distance -= 0.5) {
            Location colonne = base.clone().add(direction.clone().multiply(distance));
            Location trouve = trouverSurfaceLibre(colonne);
            if (trouve != null) {
                trouve.setYaw(base.getYaw());
                trouve.setPitch(0);
                return trouve;
            }
        }

        // Repli : la colonne du joueur lui-même (forcément valide, il s'y tient déjà).
        Location repli = trouverSurfaceLibre(base);
        if (repli != null) {
            repli.setYaw(base.getYaw());
            repli.setPitch(0);
            return repli;
        }
        return base.clone();
    }

    /**
     * Cherche la surface solide la plus proche de {@code colonne.getBlockY()} (même
     * hauteur d'abord, puis de plus en plus haut/bas alternativement) et renvoie
     * l'emplacement juste au-dessus, avec 2 blocs d'air libres. {@code null} si rien
     * d'exploitable n'est trouvé dans la portée verticale.
     */
    private static Location trouverSurfaceLibre(Location colonne) {
        int baseY = colonne.getBlockY();
        for (int delta = 0; delta <= PORTEE_VERTICALE; delta++) {
            int[] offsets = delta == 0 ? new int[]{0} : new int[]{delta, -delta};
            for (int offset : offsets) {
                int y = baseY + offset;
                Location sol = new Location(colonne.getWorld(), colonne.getBlockX() + 0.5, y, colonne.getBlockZ() + 0.5);
                if (surfaceValide(sol)) {
                    return sol.clone().add(0, 1, 0);
                }
            }
        }
        return null;
    }

    /** Le bloc lui-même est solide, ET les 2 blocs juste au-dessus sont libres (pas encastré). */
    private static boolean surfaceValide(Location sol) {
        if (!sol.getBlock().getType().isSolid()) return false;
        return !sol.clone().add(0, 1, 0).getBlock().getType().isSolid()
                && !sol.clone().add(0, 2, 0).getBlock().getType().isSolid();
    }
}
