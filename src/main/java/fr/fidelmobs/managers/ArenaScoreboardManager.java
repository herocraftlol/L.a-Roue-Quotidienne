package fr.fidelmobs.managers;

import fr.fidelmobs.LoyaltyMobsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Scores en mémoire uniquement (jamais sauvegardés sur disque) : killstreak courant et
 * total de kills de la session, remis à zéro à la déconnexion du joueur.
 */
public class ArenaScoreboardManager {

    private final LoyaltyMobsPlugin plugin;
    private final Map<UUID, Integer> killstreaks = new HashMap<>();
    private final Map<UUID, Integer> scores = new HashMap<>();
    private final Set<UUID> joueursEnArene = new HashSet<>();

    public ArenaScoreboardManager(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
    }

    public void entrerEnArene(Player player) {
        joueursEnArene.add(player.getUniqueId());
        killstreaks.putIfAbsent(player.getUniqueId(), 0);
        scores.putIfAbsent(player.getUniqueId(), 0);
        appliquerSidebar(player);
    }

    public void sortirDeArene(Player player) {
        joueursEnArene.remove(player.getUniqueId());
        ScoreboardManager sm = plugin.getServer().getScoreboardManager();
        if (sm != null) {
            player.setScoreboard(sm.getMainScoreboard());
        }
    }

    public void onDeconnexion(UUID uuid) {
        joueursEnArene.remove(uuid);
        killstreaks.remove(uuid);
        scores.remove(uuid);
        rafraichirTous();
    }

    public void enregistrerElimination(Player tueur, Player victime) {
        if (tueur != null) {
            killstreaks.merge(tueur.getUniqueId(), 1, Integer::sum);
            scores.merge(tueur.getUniqueId(), 1, Integer::sum);
        }
        killstreaks.put(victime.getUniqueId(), 0);
        rafraichirTous();
    }

    private void rafraichirTous() {
        for (UUID uuid : joueursEnArene) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) {
                appliquerSidebar(p);
            }
        }
    }

    private void appliquerSidebar(Player player) {
        ScoreboardManager sm = plugin.getServer().getScoreboardManager();
        if (sm == null) return;

        Scoreboard board = sm.getNewScoreboard();
        Objective obj = board.registerNewObjective("arene_pvp", "dummy", "§b§lARÈNE PVP");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        int killstreak = killstreaks.getOrDefault(player.getUniqueId(), 0);
        double kd = plugin.getPlayerDataManager().getRatioKD(player.getUniqueId());
        int points = plugin.getPlayerDataManager().getPoints(player.getUniqueId());

        int ligne = 15;
        obj.getScore("§fPoints : §d" + points).setScore(ligne--);
        obj.getScore(String.format(java.util.Locale.ROOT, "§fK/D : §e%.2f", kd)).setScore(ligne--);
        obj.getScore("§fSérie de kills: §e" + killstreak).setScore(ligne--);
        obj.getScore("§7").setScore(ligne--);
        obj.getScore("§6§lTOP SCORES").setScore(ligne--);

        List<Map.Entry<UUID, Integer>> top = scores.entrySet().stream()
                .filter(e -> joueursEnArene.contains(e.getKey()))
                .sorted(Comparator.<Map.Entry<UUID, Integer>>comparingInt(Map.Entry::getValue).reversed())
                .limit(5)
                .toList();

        if (top.isEmpty()) {
            obj.getScore("§7Aucun score pour l'instant").setScore(ligne--);
        } else {
            int rang = 1;
            for (Map.Entry<UUID, Integer> entry : top) {
                Player joueur = plugin.getServer().getPlayer(entry.getKey());
                String nom = joueur != null ? joueur.getName() : "???";
                obj.getScore("§f" + rang + ". " + nom + " §7- §e" + entry.getValue()).setScore(ligne--);
                rang++;
            }
        }

        player.setScoreboard(board);
    }
}
