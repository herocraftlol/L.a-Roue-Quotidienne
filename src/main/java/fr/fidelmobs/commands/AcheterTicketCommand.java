package fr.fidelmobs.commands;

import fr.fidelmobs.LoyaltyMobsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AcheterTicketCommand implements CommandExecutor {

    private final LoyaltyMobsPlugin plugin;

    public AcheterTicketCommand(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Cette commande est réservée aux joueurs.");
            return true;
        }

        String url = plugin.getConfig().getString("boutique.url", "");
        if (url == null || url.isBlank()) {
            player.sendMessage("§cLa boutique en ligne n'est pas configurée pour le moment.");
            return true;
        }

        player.sendMessage(Component.text("Tu peux acheter des tickets de roue avec de l'argent réel sur notre boutique en ligne.")
                .color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("👉 Clique ici pour ouvrir la boutique")
                .color(NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.openUrl(url)));
        player.sendMessage(Component.text("Les tickets achetés sont crédités automatiquement dans les minutes qui suivent le paiement.")
                .color(NamedTextColor.DARK_GRAY));
        return true;
    }
}
