package fr.fidelmobs.commands;

import fr.fidelmobs.LoyaltyMobsPlugin;
import fr.fidelmobs.arena.ArrowRegistry;
import fr.fidelmobs.arena.BlockRegistry;
import fr.fidelmobs.arena.GearRegistry;
import fr.fidelmobs.arena.PowerRegistry;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class RoueCommand implements CommandExecutor {

    private static final String SEPARATEUR = "§8§m§l                                                            ";
    private static final Random RANDOM = new Random();

    // Délai (en ticks, 20 ticks = 1s) entre chaque étape de l'animation de révélation des
    // récompenses. Volontairement allongé pour laisser au joueur le temps de lire le nom
    // de la récompense et sa rareté avant que le titre suivant n'apparaisse.
    private static final int DELAI_ENTRE_ETAPES = 50;

    private final LoyaltyMobsPlugin plugin;

    public RoueCommand(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Une étape de révélation animée : catégorie + nom coloré déjà formatés, et rareté
     * (utilisée pour choisir le son joué et l'intensité du titre).
     */
    private record Etape(String categorie, String nomAffiche, MobRarity rarete) {
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

        // ---- Phase 1 : tirage pur (sans effet de bord) de chaque catégorie ----
        EntityType mob = MobRegistry.tirerMobAleatoire();
        Material bloc = BlockRegistry.tirerBlocAleatoire(data.getBlocsDebloques(uuid));
        ItemStack equip = GearRegistry.genererObjetAleatoire();
        ItemStack fleche = ArrowRegistry.genererFlecheAleatoire();
        PowerRegistry.PowerDefinition pouvoir = PowerRegistry.tirerPouvoirAleatoire();

        MobRarity rMob = MobRegistry.getRarete(mob);
        MobRarity rBloc = BlockRegistry.getRarete(bloc);
        MobRarity rEquip = MobRarity.values()[GearRegistry.getRarete(equip)];
        MobRarity rFleche = MobRarity.values()[ArrowRegistry.getRarete(fleche)];
        MobRarity rPouvoir = pouvoir.rarete();

        // ---- Garantie : au moins une récompense RARE ou mieux parmi les 5 catégories ----
        MobRarity meilleure = meilleureRarete(rMob, rBloc, rEquip, rFleche, rPouvoir);
        if (meilleure.ordinal() < MobRarity.RARE.ordinal()) {
            int forcee = RANDOM.nextInt(5);
            switch (forcee) {
                case 0 -> {
                    mob = MobRegistry.tirerMobAleatoire(MobRarity.RARE.ordinal());
                    rMob = MobRegistry.getRarete(mob);
                }
                case 1 -> {
                    bloc = BlockRegistry.tirerBlocAleatoire(data.getBlocsDebloques(uuid), MobRarity.RARE.ordinal());
                    rBloc = BlockRegistry.getRarete(bloc);
                }
                case 2 -> {
                    equip = GearRegistry.genererObjetAleatoire(MobRarity.RARE.ordinal());
                    rEquip = MobRarity.values()[GearRegistry.getRarete(equip)];
                }
                case 3 -> {
                    fleche = ArrowRegistry.genererFlecheAleatoire(MobRarity.RARE.ordinal());
                    rFleche = MobRarity.values()[ArrowRegistry.getRarete(fleche)];
                }
                default -> {
                    pouvoir = PowerRegistry.tirerPouvoirAleatoire(MobRarity.RARE.ordinal());
                    rPouvoir = pouvoir.rarete();
                }
            }
            meilleure = meilleureRarete(rMob, rBloc, rEquip, rFleche, rPouvoir);
        }

        player.sendMessage(" ");
        player.sendMessage(SEPARATEUR);
        player.sendMessage("      §b§l✦ ROUE DE LA FIDÉLITÉ ✦");
        player.sendMessage(SEPARATEUR);
        player.sendMessage(" ");

        // ---- Phase 2 : application (mutation des données + messages de chat détaillés) ----
        List<Etape> etapes = new ArrayList<>();
        etapes.add(new Etape("☠ Allié", appliquerMob(player, data, uuid, mob, rMob), rMob));
        etapes.add(new Etape("▣ Bloc", appliquerBloc(player, data, uuid, bloc, rBloc), rBloc));
        etapes.add(new Etape("⚔ Équip", appliquerEquipement(player, data, uuid, equip, rEquip), rEquip));
        etapes.add(new Etape("➶ Flèche", appliquerFleche(player, data, uuid, fleche, rFleche), rFleche));
        etapes.add(new Etape("✪ Pouvoir", appliquerPouvoir(player, data, uuid, pouvoir, rPouvoir), rPouvoir));

        player.sendMessage(" ");
        player.sendMessage(SEPARATEUR);
        player.sendMessage(" ");

        data.save(uuid);

        // Si le joueur est déjà en arène et qu'une pièce d'équipement/flèche vient d'être
        // équipée automatiquement (meilleure rareté), on rafraîchit immédiatement son kit
        // pour qu'il puisse s'en servir tout de suite (flèche tirable en permanence dès
        // qu'elle est équipée, arme/armure à jour) sans devoir ressortir/rentrer en arène.
        if (plugin.getArenaProtectionListener().estDansArene(player)) {
            plugin.getKitManager().appliquerKit(player);
            player.updateInventory();
        }

        // ---- Phase 3 : animation (titres + sons) révélant chaque récompense, puis fanfare finale ----
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1f);
        player.sendTitle("§b§l✦ ROUE DE LA FIDÉLITÉ ✦", "§7Découverte des récompenses...", 8, 32, 10);
        animerRecompenses(player, etapes, meilleure);

        return true;
    }

    private String appliquerMob(Player player, PlayerDataManager data, UUID uuid, EntityType mob, MobRarity rarete) {
        data.ajouterMob(uuid, mob);
        String nom = nomLisible(mob.name());
        afficherLigne(player, "☠ Allié", rarete.getCouleur() + "§l" + nom, rarete);
        player.sendMessage("  §7Utilise §f/armee §7pour voir toute ta collection.");
        return nom;
    }

    private String appliquerBloc(Player player, PlayerDataManager data, UUID uuid, Material bloc, MobRarity rarete) {
        boolean nouveau = !data.getBlocsDebloques(uuid).contains(bloc);
        data.debloquerBloc(uuid, bloc);
        String nom = nomLisible(bloc.name());
        afficherLigne(player, "▣ Bloc", rarete.getCouleur() + "§l" + nom, rarete);
        if (nouveau) {
            player.sendMessage("  §aNouveau bloc débloqué ! Utilise §f/bloc choisir " + bloc.name() + " §apour l'activer.");
        } else {
            player.sendMessage("  §7Tu possédais déjà ce bloc.");
        }
        return nom;
    }

    private String appliquerEquipement(Player player, PlayerDataManager data, UUID uuid, ItemStack item, MobRarity rarete) {
        GearRegistry.TypeEquipement type = GearRegistry.getType(item);
        int rareteIndex = rarete.ordinal();
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
                player.sendMessage("  §7Utilise le menu d'équipement (arène) §7ou §f/equipement equiper §7pour le porter à la place.");
            }
        }
        return nom;
    }

    private String appliquerFleche(Player player, PlayerDataManager data, UUID uuid, ItemStack item, MobRarity rarete) {
        int rareteIndex = rarete.ordinal();
        int index = data.ajouterFleche(uuid, item);

        String nom = item.getItemMeta().hasDisplayName() ? item.getItemMeta().getDisplayName() : item.getType().name();
        afficherLigne(player, "➶ Flèche", "§l" + nom, rarete);
        String effet = ArrowRegistry.decrireEffet(item);
        if (effet != null) {
            player.sendMessage("  §8✦ " + effet);
        }

        int indexActuel = data.getIndexFlecheEquipee(uuid);
        int rareteActuelle = indexActuel >= 0 && indexActuel < data.getFleches(uuid).size()
                ? ArrowRegistry.getRarete(data.getFleches(uuid).get(indexActuel)) : -1;

        if (indexActuel < 0 || rareteIndex >= rareteActuelle) {
            data.setIndexFlecheEquipee(uuid, index);
            player.sendMessage("  §aÉquipée automatiquement !");
        } else {
            player.sendMessage("  §7Utilise le menu d'équipement (arène) §7pour la porter à la place.");
        }
        return nom;
    }

    private String appliquerPouvoir(Player player, PlayerDataManager data, UUID uuid,
                                     PowerRegistry.PowerDefinition pouvoir, MobRarity rarete) {
        int rareteIndex = rarete.ordinal();
        int index = data.ajouterPouvoir(uuid, pouvoir.id());

        afficherLigne(player, "✪ Pouvoir", rarete.getCouleur() + "§l" + pouvoir.nom(), rarete);
        String effet = PowerRegistry.decrireEffet(pouvoir.id());
        if (effet != null) {
            player.sendMessage("  §8✦ " + effet);
        }

        int indexActuel = data.getIndexPouvoirEquipe(uuid);
        List<String> pouvoirs = data.getPouvoirs(uuid);
        int rareteActuelle = indexActuel >= 0 && indexActuel < pouvoirs.size()
                ? PowerRegistry.getRarete(pouvoirs.get(indexActuel)).ordinal() : -1;

        if (indexActuel < 0 || rareteIndex >= rareteActuelle) {
            data.setIndexPouvoirEquipe(uuid, index);
            player.sendMessage("  §aÉquipé automatiquement !");
        } else {
            player.sendMessage("  §7Utilise le sélecteur de pouvoirs (arène, 6e slot) §7pour l'équiper à la place.");
        }
        return pouvoir.nom();
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
     * Anime la révélation des 4 récompenses l'une après l'autre (titre + son selon la rareté
     * de chacune), puis termine sur une fanfare finale reprenant la meilleure rareté obtenue.
     */
    private void animerRecompenses(Player player, List<Etape> etapes, MobRarity meilleure) {
        int delai = 35; // laisse le titre d'intro (phase 3) se terminer avant la première étape
        for (Etape etape : etapes) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                String sousTitre = etape.nomAffiche() + " §8« " + etape.rarete().getCouleur() + etape.rarete().getLabel() + "§8 »";
                // fadeIn 5, stay 45 (2,25s), fadeOut 10 : bien plus lisible que l'ancien
                // enchaînement rapide, surtout pour les noms de récompense les plus longs.
                player.sendTitle(etape.rarete().getCouleur() + "§l" + etape.categorie(), sousTitre, 5, 45, 10);
                jouerSonEtape(player, etape.rarete());
            }, delai);
            delai += DELAI_ENTRE_ETAPES;
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            jouerFanfare(player, meilleure);
        }, delai + 10);
    }

    /**
     * Petit son joué à la révélation de chaque récompense individuelle, qui monte en
     * intensité avec sa rareté propre (indépendamment de la fanfare finale sur la meilleure).
     */
    private void jouerSonEtape(Player player, MobRarity rarete) {
        switch (rarete) {
            case LEGENDAIRE -> player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1f);
            case EPIQUE -> player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 0.9f);
            case RARE -> player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.3f);
            case PEU_COMMUN -> player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1f);
            default -> player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1f);
        }
    }

    /**
     * Titre/son final qui monte en intensité avec la meilleure rareté obtenue lors du
     * tirage, pour rendre les gros coups bien plus voyants qu'un simple message.
     */
    private void jouerFanfare(Player player, MobRarity meilleure) {
        switch (meilleure) {
            case LEGENDAIRE -> {
                player.sendTitle(meilleure.getCouleur() + "§l★ LÉGENDAIRE ★", "§eQuelle chance !", 8, 90, 20);
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
            case EPIQUE -> {
                player.sendTitle(meilleure.getCouleur() + "§l✦ Épique ✦", "", 8, 60, 15);
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
