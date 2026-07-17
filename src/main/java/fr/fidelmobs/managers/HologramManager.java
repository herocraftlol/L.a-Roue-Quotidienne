package fr.fidelmobs.managers;

import fr.fidelmobs.LoyaltyMobsPlugin;
import fr.fidelmobs.data.PlayerDataManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.ArmorStand;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Hologramme de classement PvP (top kills, top morts, meilleurs K/D), construit avec
 * des armor stands invisibles empilés — aucune dépendance externe requise. Un seul
 * hologramme actif à la fois : en invoquer un nouveau retire l'ancien.
 */
public class HologramManager {

    private static final double ESPACEMENT_LIGNES = 0.27;
    private static final int TAILLE_TOP = 5;

    private final LoyaltyMobsPlugin plugin;
    private final List<ArmorStand> lignesActuelles = new ArrayList<>();
    private Location emplacementActuel;

    public HologramManager(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
    }

    public void invoquer(Location emplacement) {
        this.emplacementActuel = emplacement.clone();
        reconstruire();
    }

    /**
     * Reconstruit le hologramme à son emplacement mémorisé, si un hologramme est actif.
     * À appeler après tout événement qui change kills/morts/K-D (mort en arène) pour que
     * le classement affiché reste à jour sans qu'un admin ait besoin de relancer /classement.
     */
    public void actualiser() {
        if (emplacementActuel == null) return;
        reconstruire();
    }

    private void reconstruire() {
        for (ArmorStand stand : lignesActuelles) {
            if (stand != null && !stand.isDead()) {
                stand.remove();
            }
        }
        lignesActuelles.clear();

        Location emplacement = emplacementActuel;
        if (emplacement == null || emplacement.getWorld() == null) return;

        List<String> lignes = new ArrayList<>();
        lignes.add("§6§l✦ CLASSEMENT ARÈNE PVP ✦");
        lignes.add("§7");
        lignes.add("§c§lTop Kills");
        lignes.addAll(classement(PlayerDataManager::getKills, "§f%d. %s §7- §c%d kill(s)"));
        lignes.add("§7");
        lignes.add("§8§lTop Morts");
        lignes.addAll(classement(PlayerDataManager::getMorts, "§f%d. %s §7- §8%d mort(s)"));
        lignes.add("§7 ");
        lignes.add("§a§lMeilleurs K/D");
        lignes.addAll(classementKD());

        double y = emplacement.getY() + (lignes.size() - 1) * ESPACEMENT_LIGNES;
        for (String texte : lignes) {
            Location loc = emplacement.clone();
            loc.setY(y);
            ArmorStand stand = emplacement.getWorld().spawn(loc, ArmorStand.class, as -> {
                as.setVisible(false);
                as.setMarker(true);
                as.setGravity(false);
                as.setBasePlate(false);
                as.setCustomNameVisible(true);
                as.setCustomName(texte);
                as.setInvulnerable(true);
                as.setPersistent(false);
            });
            lignesActuelles.add(stand);
            y -= ESPACEMENT_LIGNES;
        }
    }

    public void retirer() {
        for (ArmorStand stand : lignesActuelles) {
            if (stand != null && !stand.isDead()) {
                stand.remove();
            }
        }
        lignesActuelles.clear();
        emplacementActuel = null;
    }

    @FunctionalInterface
    private interface StatFn {
        int valeur(PlayerDataManager data, UUID uuid);
    }

    private List<String> classement(StatFn stat, String format) {
        PlayerDataManager data = plugin.getPlayerDataManager();
        List<UUID> tous = data.getToutesLesUUID();

        List<UUID> tries = tous.stream()
                .filter(u -> stat.valeur(data, u) > 0)
                .sorted(Comparator.comparingInt((UUID u) -> stat.valeur(data, u)).reversed())
                .limit(TAILLE_TOP)
                .collect(Collectors.toList());

        if (tries.isEmpty()) {
            return List.of("§7Aucune donnée pour l'instant");
        }

        List<String> resultat = new ArrayList<>();
        int rang = 1;
        for (UUID uuid : tries) {
            resultat.add(String.format(Locale.ROOT, format, rang, nomJoueur(uuid), stat.valeur(data, uuid)));
            rang++;
        }
        return resultat;
    }

    private List<String> classementKD() {
        PlayerDataManager data = plugin.getPlayerDataManager();
        List<UUID> tous = data.getToutesLesUUID();

        List<UUID> eligibles = tous.stream()
                .filter(u -> data.getKills(u) > 0 || data.getMorts(u) > 0)
                .sorted(Comparator.comparingDouble((UUID u) -> data.getRatioKD(u)).reversed())
                .limit(TAILLE_TOP)
                .collect(Collectors.toList());

        if (eligibles.isEmpty()) {
            return List.of("§7Aucune donnée pour l'instant");
        }

        List<String> resultat = new ArrayList<>();
        int rang = 1;
        for (UUID uuid : eligibles) {
            resultat.add(String.format(Locale.ROOT, "§f%d. %s §7- §a%.2f", rang, nomJoueur(uuid), data.getRatioKD(uuid)));
            rang++;
        }
        return resultat;
    }

    private String nomJoueur(UUID uuid) {
        OfflinePlayer joueur = Bukkit.getOfflinePlayer(uuid);
        String nom = joueur.getName();
        return nom != null ? nom : "???";
    }
}
