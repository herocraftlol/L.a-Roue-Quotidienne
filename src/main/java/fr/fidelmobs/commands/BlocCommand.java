package fr.fidelmobs.commands;

import fr.fidelmobs.LoyaltyMobsPlugin;
import fr.fidelmobs.arena.BlockRegistry;
import fr.fidelmobs.data.PlayerDataManager;
import fr.fidelmobs.mobs.MobRarity;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class BlocCommand implements CommandExecutor, TabCompleter {

    private final LoyaltyMobsPlugin plugin;

    public BlocCommand(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Cette commande est réservée aux joueurs.");
            return true;
        }

        PlayerDataManager data = plugin.getPlayerDataManager();
        UUID uuid = player.getUniqueId();
        Set<Material> debloques = data.getBlocsDebloques(uuid);

        if (args.length == 0 || args[0].equalsIgnoreCase("liste")) {
            if (debloques.isEmpty()) {
                player.sendMessage("§7Tu n'as encore débloqué aucun bloc. Utilise §f/roue §7pour en obtenir !");
                return true;
            }
            player.sendMessage("§b=== Blocs débloqués ===");
            Material actif = data.getBlocActif(uuid);
            for (Material m : debloques) {
                MobRarity rarete = BlockRegistry.getRarete(m);
                String marque = m.equals(actif) ? " §a(actif)" : "";
                player.sendMessage(rarete.getCouleur() + m.name() + " §7(" + rarete.getLabel() + ")" + marque);
            }
            player.sendMessage("§7Utilise §f/bloc choisir <type> §7pour changer ton bloc actif.");
            return true;
        }

        if (args[0].equalsIgnoreCase("choisir")) {
            if (args.length < 2) {
                player.sendMessage("§cUsage : /bloc choisir <type>");
                return true;
            }
            Material demande = BlockRegistry.parseNom(args[1]);
            if (demande == null) {
                player.sendMessage("§cBloc inconnu ou non utilisable en arène : " + args[1]);
                return true;
            }
            if (!debloques.contains(demande)) {
                player.sendMessage("§cTu n'as pas encore débloqué ce bloc.");
                return true;
            }
            data.setBlocActif(uuid, demande);
            data.save(uuid);
            player.sendMessage("§aBloc actif défini sur " + demande.name() + ".");
            return true;
        }

        player.sendMessage("§cUsage : /bloc <liste|choisir> [type]");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return new ArrayList<>();

        if (args.length == 1) {
            return List.of("liste", "choisir").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("choisir")) {
            Set<Material> debloques = plugin.getPlayerDataManager().getBlocsDebloques(player.getUniqueId());
            return debloques.stream()
                    .map(m -> m.name().toLowerCase())
                    .filter(n -> n.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
