package fr.fidelmobs.commands;

import fr.fidelmobs.LoyaltyMobsPlugin;
import fr.fidelmobs.database.DatabaseManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Génère un code de liaison à usage unique permettant au joueur d'associer son compte
 * Minecraft à un compte sur le site web (arène de stratégie). Fonctionne aussi bien pour
 * les comptes premium que crack : aucune vérification Mojang n'est effectuée, seul l'UUID
 * connu par CE serveur (donné par le protocole de connexion, online ou offline-mode) est
 * utilisé. Le lien ne prouve donc rien de plus que "cette personne est connectée à ce
 * serveur Minecraft avec ce pseudo/UUID", ce qui est suffisant pour attribuer une armée.
 */
public class LierCommand implements CommandExecutor {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final LoyaltyMobsPlugin plugin;

    public LierCommand(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Cette commande est réservée aux joueurs.");
            return true;
        }

        DatabaseManager db = plugin.getDatabaseManager();
        if (db == null || plugin.getArmySyncTask() == null) {
            player.sendMessage("§cL'arène de stratégie en ligne n'est pas activée sur ce serveur pour le moment.");
            return true;
        }

        String code = genererCode();
        int expirationMinutes = Math.max(1, plugin.getConfig().getInt("strategie-web.lien-expiration-minutes", 10));
        String uuid = player.getUniqueId().toString();
        String pseudo = player.getName();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // On retire d'abord les éventuels anciens codes de ce joueur : un seul code
            // valide à la fois, pour éviter d'accumuler des lignes inutiles en base.
            try (Connection conn = db.getConnection();
                 PreparedStatement nettoyage = conn.prepareStatement(
                         "DELETE FROM web_link_codes WHERE uuid = ?")) {
                nettoyage.setString(1, uuid);
                nettoyage.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Impossible de nettoyer les anciens codes de liaison : " + e.getMessage());
            }

            try (Connection conn = db.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO web_link_codes (code, uuid, pseudo, created_at, expires_at) "
                                 + "VALUES (?, ?, ?, NOW(), DATE_ADD(NOW(), INTERVAL ? MINUTE))")) {
                stmt.setString(1, code);
                stmt.setString(2, uuid);
                stmt.setString(3, pseudo);
                stmt.setInt(4, expirationMinutes);
                stmt.executeUpdate();

                Bukkit.getScheduler().runTask(plugin, () -> envoyerMessage(player, code, expirationMinutes));

                // Première synchronisation immédiate de l'armée pour que le site ait déjà des
                // données fraîches dès que le code est saisi, sans attendre le prochain cycle.
                plugin.getArmySyncTask().synchroniser(player.getUniqueId(), pseudo);
            } catch (SQLException e) {
                plugin.getLogger().warning("Impossible de générer un code de liaison : " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage("§cErreur lors de la génération du code, réessaie plus tard."));
            }
        });

        return true;
    }

    private void envoyerMessage(Player player, String code, int expirationMinutes) {
        String url = plugin.getConfig().getString("strategie-web.url", "https://tonsite.fr/arene.html");

        player.sendMessage(Component.text("Ton code de liaison : ").color(NamedTextColor.GRAY)
                .append(Component.text(code).color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD)));
        player.sendMessage(Component.text("Entre-le sur le site, onglet \"Lier mon compte\", dans les "
                + expirationMinutes + " minutes.").color(NamedTextColor.DARK_GRAY));
        player.sendMessage(Component.text("👉 Ouvrir l'arène de stratégie en ligne")
                .color(NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.openUrl(url)));
    }

    private String genererCode() {
        int n = 100000 + RANDOM.nextInt(900000);
        return Integer.toString(n);
    }
}
