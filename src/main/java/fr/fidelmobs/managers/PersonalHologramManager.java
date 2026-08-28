package fr.fidelmobs.managers;

import fr.fidelmobs.LoyaltyMobsPlugin;
import fr.fidelmobs.data.PlayerDataManager;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Petit hologramme PERSONNEL flottant au-dessus de la tête de chaque joueur en arène,
 * affichant SES statistiques (nom, kills, morts, K/D, niveau, tickets disponibles...).
 * Contrairement au hologramme de classement (public, visible de tous), celui-ci n'est
 * visible QUE par le joueur concerné : masqué à tous les autres via Player#hideEntity,
 * en pur Bukkit sans dépendance externe.
 */
public class PersonalHologramManager {

    private static final double ESPACEMENT_LIGNES = 0.27;
    private static final double HAUTEUR_AU_DESSUS_TETE = 2.5;
    private static final long INTERVALLE_ACTUALISATION_TICKS = 20L; // 1s

    private final LoyaltyMobsPlugin plugin;
    private final Map<UUID, List<ArmorStand>> hologrammes = new HashMap<>();

    public PersonalHologramManager(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
        demarrerActualisationPeriodique();
    }

    private void demarrerActualisationPeriodique() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (hologrammes.isEmpty()) return;
            for (UUID uuid : new ArrayList<>(hologrammes.keySet())) {
                Player joueur = plugin.getServer().getPlayer(uuid);
                if (joueur == null || !joueur.isOnline() || !plugin.getArenaProtectionListener().estDansArene(joueur)) {
                    retirer(uuid);
                    continue;
                }
                construire(joueur);
            }
        }, INTERVALLE_ACTUALISATION_TICKS, INTERVALLE_ACTUALISATION_TICKS);
    }

    public void entrerEnArene(Player player) {
        construire(player);
        // Cache ce nouvel hologramme à tous les autres joueurs déjà en ligne (il ne doit
        // être visible que par son propriétaire).
        for (Player autre : plugin.getServer().getOnlinePlayers()) {
            if (!autre.equals(player)) {
                for (ArmorStand stand : hologrammes.getOrDefault(player.getUniqueId(), List.of())) {
                    autre.hideEntity(plugin, stand);
                }
            }
        }
    }

    public void sortirDeArene(Player player) {
        retirer(player.getUniqueId());
    }

    /** Cache tous les hologrammes personnels déjà actifs à un joueur qui vient de se connecter. */
    public void masquerPourNouveauJoueur(Player nouveauJoueur) {
        for (Map.Entry<UUID, List<ArmorStand>> entree : hologrammes.entrySet()) {
            if (entree.getKey().equals(nouveauJoueur.getUniqueId())) continue;
            for (ArmorStand stand : entree.getValue()) {
                if (stand != null && !stand.isDead()) {
                    nouveauJoueur.hideEntity(plugin, stand);
                }
            }
        }
    }

    private void construire(Player player) {
        UUID uuid = player.getUniqueId();
        supprimerStands(uuid);

        PlayerDataManager data = plugin.getPlayerDataManager();
        List<String> lignes = new ArrayList<>();
        lignes.add("§b§l" + player.getName());
        lignes.add("§7Kills §c" + data.getKills(uuid) + " §7· Morts §8" + data.getMorts(uuid)
                + String.format(Locale.ROOT, " §7· K/D §a%.2f", data.getRatioKD(uuid)));
        lignes.add("§7Niveau §b" + plugin.getNiveauManager().getNiveau(uuid)
                + " §7· Tickets §e" + data.getTickets(uuid) + " §7· Points §d" + data.getPoints(uuid));

        Location base = player.getLocation().clone().add(0, HAUTEUR_AU_DESSUS_TETE, 0);
        if (base.getWorld() == null) return;
        double y = base.getY() + (lignes.size() - 1) * ESPACEMENT_LIGNES;

        List<ArmorStand> stands = new ArrayList<>();
        for (String texte : lignes) {
            Location loc = base.clone();
            loc.setY(y);
            ArmorStand stand = base.getWorld().spawn(loc, ArmorStand.class, as -> {
                as.setVisible(false);
                as.setMarker(true);
                as.setGravity(false);
                as.setBasePlate(false);
                as.setCustomNameVisible(true);
                as.setCustomName(texte);
                as.setInvulnerable(true);
                as.setPersistent(false);
            });
            for (Player autre : plugin.getServer().getOnlinePlayers()) {
                if (!autre.equals(player)) autre.hideEntity(plugin, stand);
            }
            stands.add(stand);
            y -= ESPACEMENT_LIGNES;
        }
        hologrammes.put(uuid, stands);
    }

    private void supprimerStands(UUID uuid) {
        List<ArmorStand> anciens = hologrammes.remove(uuid);
        if (anciens != null) {
            for (ArmorStand s : anciens) {
                if (s != null && !s.isDead()) s.remove();
            }
        }
    }

    private void retirer(UUID uuid) {
        supprimerStands(uuid);
    }

    /** Supprime tous les hologrammes personnels (arrêt du plugin). */
    public void retirerTout() {
        for (UUID uuid : new ArrayList<>(hologrammes.keySet())) {
            supprimerStands(uuid);
        }
    }
}
