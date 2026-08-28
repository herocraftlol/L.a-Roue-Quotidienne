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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class RoueCommand implements CommandExecutor {

    private static final Random RANDOM = new Random();

    // Délai (en ticks, 20 ticks = 1s) entre chaque étape de l'animation de révélation des
    // récompenses au titre. Volontairement court : le titre ne montre plus que les
    // nouveautés (voir Etape.nouveau), donc il y a déjà moins d'étapes à traverser — pas
    // besoin d'un délai aussi long qu'avant pour rester lisible.
    private static final int DELAI_ENTRE_ETAPES = 32;

    private final LoyaltyMobsPlugin plugin;

    public RoueCommand(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Une étape de révélation animée au titre : catégorie + nom coloré déjà formatés, un
     * détail optionnel (enchantements pour l'équipement, effet pour les flèches), rareté
     * (son + intensité du titre), et {@code nouveau} qui indique si c'est une vraie
     * nouveauté (sinon l'étape n'est pas montrée au titre — voir animerRecompenses).
     */
    private record Etape(String categorie, String nomAffiche, String detail, MobRarity rarete, boolean nouveau) {
    }

    /** Résultat d'une catégorie de récompense : nom + détail court + nouveauté, pour le chat ET le titre. */
    private record Resultat(String nom, String detail, boolean nouveau) {
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
        data.incrementerCompteur(uuid, "roue_utilisee", 1);
        data.incrementerCompteurQuotidien(uuid, "roue_utilisee", 1);

        // ---- Phase 1 : tirage pur (sans effet de bord) de chaque catégorie ----
        Set<String> signaturesGearPossedees = data.getEquipements(uuid).stream()
                .map(GearRegistry::getSignature).collect(Collectors.toSet());
        Set<Integer> flechesDejaPossedees = data.getFleches(uuid).stream()
                .map(ArrowRegistry::getModeleId).collect(Collectors.toSet());

        EntityType mob = MobRegistry.tirerMobAleatoire();
        Material bloc = BlockRegistry.tirerBlocAleatoire(data.getBlocsDebloques(uuid));
        ItemStack equip = tirerEquipementSansDoublon(0, signaturesGearPossedees);
        ItemStack fleche = tirerFlecheSansDoublon(0, flechesDejaPossedees);
        PowerRegistry.PowerDefinition pouvoir = PowerRegistry.tirerPouvoirAleatoire();

        MobRarity rMob = MobRegistry.getRarete(mob);
        MobRarity rBloc = BlockRegistry.getRarete(bloc);
        MobRarity rEquip = equip != null ? MobRarity.values()[GearRegistry.getRarete(equip)] : GearRegistry.tirerRareteSeule(0);
        MobRarity rFleche = fleche != null ? MobRarity.values()[ArrowRegistry.getRarete(fleche)] : ArrowRegistry.tirerRareteSeule(0);
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
                    equip = tirerEquipementSansDoublon(MobRarity.RARE.ordinal(), signaturesGearPossedees);
                    rEquip = equip != null ? MobRarity.values()[GearRegistry.getRarete(equip)]
                            : GearRegistry.tirerRareteSeule(MobRarity.RARE.ordinal());
                }
                case 3 -> {
                    fleche = tirerFlecheSansDoublon(MobRarity.RARE.ordinal(), flechesDejaPossedees);
                    rFleche = fleche != null ? MobRarity.values()[ArrowRegistry.getRarete(fleche)]
                            : ArrowRegistry.tirerRareteSeule(MobRarity.RARE.ordinal());
                }
                default -> {
                    pouvoir = PowerRegistry.tirerPouvoirAleatoire(MobRarity.RARE.ordinal());
                    rPouvoir = pouvoir.rarete();
                }
            }
            meilleure = meilleureRarete(rMob, rBloc, rEquip, rFleche, rPouvoir);
        }

        // ---- Phase 2 : application (mutation des données) + UNE ligne de chat par catégorie ----
        player.sendMessage("§b§l✦ Roue §8— §7récompenses :");
        Resultat rMobRes = appliquerMob(player, data, uuid, mob, rMob);
        Resultat rBlocRes = appliquerBloc(player, data, uuid, bloc, rBloc);
        Resultat rEquipRes = appliquerEquipement(player, data, uuid, equip, rEquip);
        Resultat rFlecheRes = appliquerFleche(player, data, uuid, fleche, rFleche);
        Resultat rPouvoirRes = appliquerPouvoir(player, data, uuid, pouvoir, rPouvoir);

        List<Etape> etapes = new ArrayList<>();
        etapes.add(new Etape("☠ Allié", rMobRes.nom(), rMobRes.detail(), rMob, rMobRes.nouveau()));
        etapes.add(new Etape("▣ Bloc", rBlocRes.nom(), rBlocRes.detail(), rBloc, rBlocRes.nouveau()));
        etapes.add(new Etape("⚔ Équip", rEquipRes.nom(), rEquipRes.detail(), rEquip, rEquipRes.nouveau()));
        etapes.add(new Etape("➶ Flèche", rFlecheRes.nom(), rFlecheRes.detail(), rFleche, rFlecheRes.nouveau()));
        etapes.add(new Etape("✪ Pouvoir", rPouvoirRes.nom(), rPouvoirRes.detail(), rPouvoir, rPouvoirRes.nouveau()));

        data.save(uuid);

        // Si le joueur est déjà en arène et qu'une pièce d'équipement/flèche vient d'être
        // équipée automatiquement (meilleure rareté), on rafraîchit immédiatement son kit.
        if (plugin.getArenaProtectionListener().estDansArene(player)) {
            plugin.getKitManager().appliquerKit(player);
            player.updateInventory();
        }

        // ---- Phase 3 : titre animé, UNIQUEMENT pour les vraies nouveautés ----
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1f);
        animerRecompenses(player, etapes, meilleure);

        return true;
    }

    private Resultat appliquerMob(Player player, PlayerDataManager data, UUID uuid, EntityType mob, MobRarity rarete) {
        int avant = data.getNombreMob(uuid, mob);
        data.ajouterMob(uuid, mob);
        String nom = nomLisible(mob.name());
        ligneCompacte(player, "☠", nom, rarete, null, avant == 0 ? "§anouveau" : "§7×" + (avant + 1));
        return new Resultat(nom, null, avant == 0);
    }

    private Resultat appliquerBloc(Player player, PlayerDataManager data, UUID uuid, Material bloc, MobRarity rarete) {
        boolean nouveau = !data.getBlocsDebloques(uuid).contains(bloc);
        data.debloquerBloc(uuid, bloc);
        String nom = nomLisible(bloc.name());
        ligneCompacte(player, "▣", nom, rarete, null, nouveau ? "§anouveau" : "§7déjà possédé");
        return new Resultat(nom, null, nouveau);
    }

    private Resultat appliquerEquipement(Player player, PlayerDataManager data, UUID uuid, ItemStack item, MobRarity rarete) {
        if (item == null) {
            return appliquerBonusCollectionComplete(player, data, uuid, "⚔", rarete);
        }

        GearRegistry.TypeEquipement type = GearRegistry.getType(item);
        int rareteIndex = rarete.ordinal();
        int index = data.ajouterEquipement(uuid, item);

        String nom = item.getItemMeta().hasDisplayName() ? item.getItemMeta().getDisplayName() : item.getType().name();
        String enchantements = GearRegistry.formatEnchantements(item);

        String suffixe = null;
        if (type != null) {
            int indexActuel = data.getIndexEquipe(uuid, type.slot);
            int rareteActuelle = indexActuel >= 0 && indexActuel < data.getEquipements(uuid).size()
                    ? GearRegistry.getRarete(data.getEquipements(uuid).get(indexActuel)) : -1;
            if (indexActuel < 0 || rareteIndex >= rareteActuelle) {
                data.setIndexEquipe(uuid, type.slot, index);
                suffixe = "§aéquipé";
            }
        }
        ligneCompacte(player, "⚔", nom, rarete, enchantements, suffixe);
        return new Resultat(nom, enchantements, true);
    }

    private Resultat appliquerFleche(Player player, PlayerDataManager data, UUID uuid, ItemStack item, MobRarity rarete) {
        if (item == null) {
            return appliquerBonusCollectionComplete(player, data, uuid, "➶", rarete);
        }

        int rareteIndex = rarete.ordinal();
        int index = data.ajouterFleche(uuid, item);

        String nom = item.getItemMeta().hasDisplayName() ? item.getItemMeta().getDisplayName() : item.getType().name();
        String effet = ArrowRegistry.decrireEffet(item);

        int indexActuel = data.getIndexFlecheEquipee(uuid);
        int rareteActuelle = indexActuel >= 0 && indexActuel < data.getFleches(uuid).size()
                ? ArrowRegistry.getRarete(data.getFleches(uuid).get(indexActuel)) : -1;

        String suffixe = null;
        if (indexActuel < 0 || rareteIndex >= rareteActuelle) {
            data.setIndexFlecheEquipee(uuid, index);
            suffixe = "§aéquipée";
        }
        ligneCompacte(player, "➶", nom, rarete, effet, suffixe);
        return new Resultat(nom, effet, true);
    }

    /**
     * Utilisé quand la collection (équipement ou flèches) est déjà complète à ce palier
     * minimum : plutôt que de rendre un doublon réel, on convertit la récompense en points
     * de fidélité, à la hauteur de la rareté qui aurait dû être tirée. Ce n'est pas une
     * nouveauté au sens du titre (pas d'objet réellement débloqué).
     */
    private Resultat appliquerBonusCollectionComplete(Player player, PlayerDataManager data, UUID uuid,
                                                        String icone, MobRarity rarete) {
        int points = 20 * (rarete.ordinal() + 1);
        data.ajouterPoints(uuid, points);
        ligneCompacte(player, icone, points + " points", rarete, null, "§7collection complète");
        return new Resultat(points + " points", "collection complète", false);
    }

    private Resultat appliquerPouvoir(Player player, PlayerDataManager data, UUID uuid,
                                       PowerRegistry.PowerDefinition pouvoir, MobRarity rarete) {
        data.ajouterPouvoir(uuid, pouvoir.id());

        String actuel = data.getPouvoirEquipe(uuid);
        boolean deja = pouvoir.id().equals(actuel);
        int rareteActuelle = actuel != null ? PowerRegistry.getRarete(actuel).ordinal() : -1;

        String suffixe;
        if (deja) {
            suffixe = "§7+1 charge";
        } else if (actuel == null || rarete.ordinal() >= rareteActuelle) {
            data.setPouvoirEquipe(uuid, pouvoir.id());
            suffixe = "§aéquipé";
        } else {
            suffixe = null;
        }
        ligneCompacte(player, "✪", pouvoir.nom(), rarete, null, suffixe);
        return new Resultat(pouvoir.nom(), null, !deja);
    }

    /**
     * Tire une pièce d'équipement/arme que le joueur ne possède pas encore (même matériau ET
     * même niveau d'enchantement). Renvoie {@code null} si la collection est déjà complète
     * à ce tier minimum : dans ce cas on ne rend JAMAIS un doublon.
     */
    private ItemStack tirerEquipementSansDoublon(int minTierOrdinal, Set<String> signaturesDejaPossedees) {
        return GearRegistry.genererObjetAleatoire(minTierOrdinal, signaturesDejaPossedees);
    }

    /**
     * Tire une flèche à effet que le joueur ne possède pas encore. Renvoie {@code null} si
     * tous les paliers possibles sont déjà possédés (collection complète).
     */
    private ItemStack tirerFlecheSansDoublon(int minTierOrdinal, Set<Integer> tiersDejaPossedes) {
        return ArrowRegistry.genererFlecheAleatoire(minTierOrdinal, tiersDejaPossedes);
    }

    /**
     * UNE seule ligne de chat compacte par récompense : icône+rareté, nom, détail court
     * entre parenthèses s'il y en a un, et un petit suffixe de statut. Remplace l'ancien
     * format qui pouvait prendre 2 à 4 lignes par catégorie (10 à 20 lignes au total).
     */
    private void ligneCompacte(Player player, String icone, String nom, MobRarity rarete, String detail, String suffixe) {
        String c = rarete.getCouleur();
        StringBuilder sb = new StringBuilder();
        sb.append(c).append(icone).append(" §f").append(nom);
        if (detail != null && !detail.isBlank()) {
            sb.append(" §7(§8").append(detail).append("§7)");
        }
        sb.append(" §8[").append(c).append(rarete.getLabel()).append("§8]");
        if (suffixe != null) {
            sb.append(" §8- ").append(suffixe);
        }
        player.sendMessage(sb.toString());
    }

    private MobRarity meilleureRarete(MobRarity... raretes) {
        MobRarity meilleure = MobRarity.COMMUN;
        for (MobRarity r : raretes) {
            if (r.ordinal() > meilleure.ordinal()) meilleure = r;
        }
        return meilleure;
    }

    /**
     * Anime au titre UNIQUEMENT les récompenses qui sont de vraies nouveautés (voir
     * Etape.nouveau) : un bloc déjà possédé, ou un bonus de points "collection complète",
     * n'a pas besoin d'un titre plein écran — la ligne de chat suffit. Ça réduit
     * automatiquement le nombre d'étapes (donc la durée totale de l'animation) sans avoir
     * à changer le délai à chaque fois.
     */
    private void animerRecompenses(Player player, List<Etape> etapes, MobRarity meilleure) {
        List<Etape> nouveautes = etapes.stream().filter(Etape::nouveau).toList();

        if (nouveautes.isEmpty()) {
            // Rien de vraiment nouveau cette fois (que des doublons/bonus) : juste la
            // fanfare finale, pas de titre par étape.
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) jouerFanfare(player, meilleure);
            }, 5L);
            return;
        }

        int delai = 3;
        for (Etape etape : nouveautes) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                String base = etape.nomAffiche() + " §8« " + etape.rarete().getCouleur() + etape.rarete().getLabel() + "§8 »";
                String sousTitre = etape.detail() != null ? base + " §7— §f" + etape.detail() : base;
                // fadeIn 3, stay 28 (1,4s), fadeOut 6 : plus vif qu'avant, largement assez
                // pour lire vu qu'il n'y a plus que les vraies nouveautés à afficher.
                player.sendTitle(etape.rarete().getCouleur() + "§l" + etape.categorie(), sousTitre, 3, 28, 6);
                jouerSonEtape(player, etape.rarete());
            }, delai);
            delai += DELAI_ENTRE_ETAPES;
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) jouerFanfare(player, meilleure);
        }, delai + 6);
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
                player.sendTitle(meilleure.getCouleur() + "§l★ LÉGENDAIRE ★", "§eQuelle chance !", 6, 60, 15);
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
            case EPIQUE -> {
                player.sendTitle(meilleure.getCouleur() + "§l✦ Épique ✦", "", 6, 45, 12);
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
