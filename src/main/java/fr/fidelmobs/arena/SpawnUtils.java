package fr.fidelmobs.arena;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Recherche d'une position de spawn sûre devant le joueur. Utilisé pour l'invocation
 * de mobs alliés (commande /invoquer et item d'invocation), pour éviter qu'un mob
 * n'apparaisse dans le vide en bordure de l'arène (qui ne fait qu'une couche de blocs).
 */
public final class SpawnUtils {

    private SpawnUtils() {
    }

    public static Location trouverPositionSpawnValide(Player player) {
        Location base = player.getLocation();
        Vector direction = base.getDirection().normalize();

        for (double distance = 2.0; distance >= 1.0; distance -= 1.0) {
            Location candidate = base.clone().add(direction.clone().multiply(distance));
            if (solSolide(candidate)) {
                return candidate;
            }
        }
        return base.clone();
    }

    private static boolean solSolide(Location loc) {
        Location sousPieds = loc.clone().subtract(0, 1, 0);
        return sousPieds.getBlock().getType().isSolid();
    }
}
