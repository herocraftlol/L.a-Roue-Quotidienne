package fr.fidelmobs.commands;

import fr.fidelmobs.LoyaltyMobsPlugin;
import fr.fidelmobs.arena.BlockRegistry;
import fr.fidelmobs.arena.GearRegistry;
import fr.fidelmobs.data.PlayerDataManager;
import fr.fidelmobs.mobs.MobRarity;
import fr.fidelmobs.mobs.MobRegistry;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class RoueCommand implements CommandExecutor {

    private static final String SEPARATEUR = "§8§m§l                                                            ";

    private final LoyaltyMobsPlugin plugin;

    public RoueCommand(LoyaltyMobsPlugin plugin) {
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

        if (!data.consommerTicket(uuid)) {
            player.sendMessage("§cTu n'as pas de ticket disponible. Connecte-toi plusieurs jours d'affilée pour en gagner !");
            return true;
        }

        player.sendMessage(" ");
        player.sendMessage(SEPARATEUR);
        player.sendMessage("      §b§l✦ ROUE DE LA FIDÉLITÉ ✦");
        player.sendMessage(SEPARATEUR);
        player.sendMessage(" ");

        // Un lancer de roue donne une récompense de chaque catégorie
        MobRarity r1 = tirerMob(player, data, uuid);
        MobRarity r2 = tirerBloc(player, data, uuid);
        MobRarity r3 = tirerEquipement(player, data, uuid);

        player.sendMessage(" ");
        player.sendMessage(SEPARATEUR);
        player.sendMessage(" ");

        data.save(uuid);
        jouerFanfare(player, meilleureRarete(r1, r2, r3));
        return true;
    }

    private MobRarity tirerMob(Player player, PlayerDataManager data, UUID uuid) {
        EntityType mob = MobRegistry.tirerMobAleatoire();
        MobRarity rarete = MobRegistry.getRarete(mob);
        data.ajouterMob(uuid, mob);

        afficherLigne(player, "☠ Allié", rarete.getCouleur() + "§l" + nomLisible(mob.name()), rarete);
        player.sendMessage("  §7Utilise §f/armee §7pour voir toute ta collection.");
        return rarete;
    }

    private MobRarity tirerBloc(Player player, PlayerDataManager data, UUID uuid) {
        Material bloc = BlockRegistry.tirerBlocAleatoire();
        MobRarity rarete = BlockRegistry.getRarete(bloc);
        boolean nouveau = !data.getBlocsDebloques(uuid).contains(bloc);
        data.debloquerBloc(uuid, bloc);

        afficherLigne(player, "▣ Bloc", rarete.getCouleur() + "§l" + nomLisible(bloc.name()), rarete);
        if (nouveau) {
            player.sendMessage("  §aNouveau bloc débloqué ! Utilise §f/bloc choisir " + bloc.name() + " §apour l'activer.");
        } else {
            player.sendMessage("  §7Tu possédais déjà ce bloc.");
        }
        return rarete;
    }

    private MobRarity tirerEquipement(Player player, PlayerDataManager data, UUID uuid) {
        ItemStack item = GearRegistry.genererObjetAleatoire();
        GearRegistry.TypeEquipement type = GearRegistry.getType(item);
        int rareteIndex = GearRegistry.getRarete(item);
        MobRarity rarete = MobRarity.values()[rareteIndex];

        int index = data.ajouterEquipement(uuid, item);

        String nom = item.getItemMeta().hasDisplayName() ? item.getItemMeta().getDisplayName() : item.getType().name();
        afficherLigne(player, "⚔ Équip", "§l" + nom, rarete);

        if (type != null) {
            int indexActuel = data.getIndexEquipe(uuid, type.slot);
            int rareteActuelle = indexActuel >= 0 && indexActuel < data.getEquipements(uuid).size()
                    ? GearRegistry.getRarete(data.getEquipements(uuid).get(indexActuel)) : -1;

            if (indexActuel < 0 || rareteIndex >= rareteActuelle) {
                data.setIndexEquipe(uuid, type.slot, index);
                player.sendMessage("  §aÉquipé automatiquement !");
            } else {
                player.sendMessage("  §7Utilise §f/equipement liste §7et §f/equipement equiper §7pour le porter à la place.");
            }
        }
        return rarete;
    }

    /**
     * Ligne de récompense uniforme : puce et cadre colorés selon la rareté, pour que les
     * meilleurs tirages sautent immédiatement aux yeux dans le chat.
     */
    private void afficherLigne(Player player, String categorie, String nomColore, MobRarity rarete) {
        String c = rarete.getCouleur();
        player.sendMessage(c + "§l▸ " + "§8[" + c + categorie + "§8] " + nomColore
                + " §8« " + c + rarete.getLabel() + "§8 »");
    }

    private MobRarity meilleureRarete(MobRarity... raretes) {
        MobRarity meilleure = MobRarity.COMMUN;
        for (MobRarity r : raretes) {
            if (r.ordinal() > meilleure.ordinal()) meilleure = r;
        }
        return meilleure;
    }

    /**
     * Petit effet sonore/visuel qui monte en intensité avec la meilleure rareté obtenue
     * lors du tirage, pour rendre les gros coups bien plus voyants qu'un simple message.
     */
    private void jouerFanfare(Player player, MobRarity meilleure) {
        switch (meilleure) {
            case LEGENDAIRE -> {
                player.sendTitle(meilleure.getCouleur() + "§l★ LÉGENDAIRE ★", "§eQuelle chance !", 5, 60, 15);
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
            case EPIQUE -> {
                player.sendTitle(meilleure.getCouleur() + "§l✦ Épique ✦", "", 5, 40, 10);
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.8f);
            }
            case RARE -> player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.3f);
            default -> player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        }
    }

    private String nomLisible(String nomBrut) {
        String s = nomBrut.toLowerCase().replace('_', ' ');
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
