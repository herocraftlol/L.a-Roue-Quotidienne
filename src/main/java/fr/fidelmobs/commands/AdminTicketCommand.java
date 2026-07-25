package fr.fidelmobs.commands;

import fr.fidelmobs.LoyaltyMobsPlugin;
import fr.fidelmobs.data.PlayerDataManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Commande réservée aux admins pour ajouter ou retirer des tickets de roue à un joueur
 * (en ligne ou non), avec le montant de leur choix.
 */
public class AdminTicketCommand implements CommandExecutor, TabCompleter {

    private final LoyaltyMobsPlugin plugin;

    public AdminTicketCommand(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("loyaltymobs.admin")) {
            sender.sendMessage("§cTu n'as pas la permission d'utiliser cette commande.");
            return true;
        }

        if (args.length < 3 || !(args[0].equalsIgnoreCase("ajouter") || args[0].equalsIgnoreCase("retirer"))) {
            sender.sendMessage("§cUsage : /adminticket <ajouter|retirer> <joueur> <montant>");
            return true;
        }

        boolean retirer = args[0].equalsIgnoreCase("retirer");
        String nomJoueur = args[1];

        OfflinePlayer cible = resoudreJoueur(nomJoueur);
        if (cible == null || (!cible.hasPlayedBefore() && !cible.isOnline())) {
            sender.sendMessage("§cJoueur introuvable : " + nomJoueur
                    + " §7(il doit s'être déjà connecté au moins une fois sur le serveur).");
            return true;
        }

        int montant;
        try {
            montant = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cMontant invalide : " + args[2]);
            return true;
        }
        if (montant <= 0) {
            sender.sendMessage("§cLe montant doit être un nombre entier positif.");
            return true;
        }

        PlayerDataManager data = plugin.getPlayerDataManager();
        UUID uuid = cible.getUniqueId();
        String nomAffiche = cible.getName() != null ? cible.getName() : nomJoueur;

        if (retirer) {
            int avant = data.getTickets(uuid);
            int retires = data.retirerTickets(uuid, montant);
            data.save(uuid);

            if (retires == 0) {
                sender.sendMessage("§e" + nomAffiche + " §7ne possédait déjà aucun ticket de roue.");
            } else if (retires < montant) {
                sender.sendMessage("§a" + retires + " ticket(s) retiré(s) à §e" + nomAffiche
                        + " §7(il n'en avait que " + avant + ", il en avait moins que les " + montant + " demandés). "
                        + "§7Solde restant : §f" + data.getTickets(uuid));
            } else {
                sender.sendMessage("§a" + retires + " ticket(s) de roue retiré(s) à §e" + nomAffiche
                        + "§a. §7Solde restant : §f" + data.getTickets(uuid));
            }
        } else {
            data.addTickets(uuid, montant);
            data.save(uuid);
            sender.sendMessage("§a" + montant + " ticket(s) de roue ajouté(s) à §e" + nomAffiche
                    + "§a. §7Solde actuel : §f" + data.getTickets(uuid));
        }

        Player joueurEnLigne = cible.getPlayer();
        if (joueurEnLigne != null) {
            if (retirer) {
                joueurEnLigne.sendMessage("§7Un admin t'a retiré des tickets de roue. §7Solde actuel : §f"
                        + data.getTickets(uuid));
            } else {
                joueurEnLigne.sendMessage("§aUn admin t'a offert §e" + montant + " ticket(s) de roue §a! §7Solde actuel : §f"
                        + data.getTickets(uuid));
            }
        }

        return true;
    }

    private OfflinePlayer resoudreJoueur(String nom) {
        Player enLigne = Bukkit.getPlayerExact(nom);
        if (enLigne != null) return enLigne;

        // getOfflinePlayer(String) est déprécié (peut se tromper en mode online avec un nom
        // jamais vu), mais reste la seule option pour cibler un joueur hors ligne par pseudo
        // sans connaître son UUID à l'avance ; on vérifie hasPlayedBefore() juste après pour
        // éviter de créer des données pour un pseudo qui ne s'est jamais connecté.
        @SuppressWarnings("deprecation")
        OfflinePlayer horsLigne = Bukkit.getOfflinePlayer(nom);
        return horsLigne;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("loyaltymobs.admin")) return new ArrayList<>();

        if (args.length == 1) {
            return List.of("ajouter", "retirer").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 3) {
            return List.of("1", "5", "10");
        }
        return new ArrayList<>();
    }
}
