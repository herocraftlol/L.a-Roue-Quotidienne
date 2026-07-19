package fr.fidelmobs.listeners;

import fr.fidelmobs.Cles;
import fr.fidelmobs.LoyaltyMobsPlugin;
import fr.fidelmobs.arena.ArenaManager;
import fr.fidelmobs.arena.BlockRegistry;
import fr.fidelmobs.data.PlayerDataManager;
import fr.fidelmobs.managers.BlockSelectorInventoryHolder;
import fr.fidelmobs.managers.InvocationInventoryHolder;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ArenaProtectionListener implements Listener {

    private final LoyaltyMobsPlugin plugin;
    private final Set<UUID> joueursDansArene = new HashSet<>();
    // Mode de jeu du joueur avant son entrée en arène, pour le restaurer à la sortie
    private final Map<UUID, GameMode> modeAvantArene = new HashMap<>();

    public ArenaProtectionListener(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean estDansArene(Player player) {
        return joueursDansArene.contains(player.getUniqueId());
    }

    /**
     * Applique/retire le kit et les effets d'arène selon la nouvelle position du joueur.
     * Factorisé pour être appelé aussi bien depuis un déplacement classique que depuis
     * une téléportation (qui ne déclenche pas forcément de PlayerMoveEvent immédiat).
     */
    private void gererChangementZone(Player player, Location nouvelleLocation) {
        if (nouvelleLocation == null) return;
        ArenaManager arene = plugin.getArenaManager();
        boolean etaitDedans = joueursDansArene.contains(player.getUniqueId());
        boolean estDedans = arene.estDansArene(nouvelleLocation);

        if (estDedans && !etaitDedans) {
            joueursDansArene.add(player.getUniqueId());
            modeAvantArene.put(player.getUniqueId(), player.getGameMode());
            player.setGameMode(GameMode.SURVIVAL);
            try {
                plugin.getKitManager().appliquerKit(player);
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Échec de l'application du kit pour " + player.getName(), e);
            }
            try {
                plugin.getBuildBlockManager().entrerEnArene(player);
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Échec de la distribution des blocs pour " + player.getName(), e);
            }
            plugin.getInvocationManager().donnerItem(player);
            plugin.getBlockSelectorManager().donnerItem(player);
            plugin.getScoreboardManager().entrerEnArene(player);
            // Le changement de gamemode et les modifications d'inventaire dans le même tick
            // peuvent se désynchroniser côté client (le paquet de resync du gamemode écrase
            // parfois un slot tout juste posé) : on force un renvoi complet de l'inventaire,
            // puis on réapplique une seconde fois au tick suivant par sécurité.
            player.updateInventory();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || !joueursDansArene.contains(player.getUniqueId())) return;
                plugin.getKitManager().appliquerKit(player);
                plugin.getBuildBlockManager().resynchroniser(player);
                plugin.getInvocationManager().donnerItem(player);
                plugin.getBlockSelectorManager().donnerItem(player);
                player.updateInventory();
            });
            player.sendMessage("§c§lVous entrez dans l'arène PvP !");
        } else if (!estDedans && etaitDedans) {
            joueursDansArene.remove(player.getUniqueId());
            GameMode modePrecedent = modeAvantArene.remove(player.getUniqueId());
            if (modePrecedent != null) {
                player.setGameMode(modePrecedent);
            }
            plugin.getKitManager().retirerKit(player);
            plugin.getBuildBlockManager().sortirDeArene(player);
            plugin.getInvocationManager().retirerItem(player);
            plugin.getBlockSelectorManager().retirerItem(player);
            plugin.getScoreboardManager().sortirDeArene(player);
            player.updateInventory();
            player.sendMessage("§7Vous quittez l'arène PvP.");
        }
    }

    @EventHandler
    public void onDeplacement(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        // on ne traite que les changements de bloc pour éviter de spammer à chaque micro-mouvement
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        Location destination = event.getTo();

        // Vérifiée avant le traitement de sortie de zone : le joueur doit encore être
        // considéré "dans l'arène" au moment de la mort pour que le kill/la mort soit comptée.
        verifierChuteMortelle(player, destination);
        gererChangementZone(player, destination);
    }

    @EventHandler
    public void onTeleportation(PlayerTeleportEvent event) {
        // Une téléportation (commande /tp, ender pearl, portail...) doit être détectée
        // immédiatement, sans attendre un futur PlayerMoveEvent qui peut être retardé
        // ou ne jamais correspondre à un vrai changement de bloc suivi.
        Player player = event.getPlayer();
        Location destination = event.getTo();

        verifierChuteMortelle(player, destination);
        gererChangementZone(player, destination);
    }

    /**
     * Si le joueur passe sous le niveau du sol de l'arène (chute dans le vide via une
     * brèche dans la plateforme, qui ne fait qu'une seule couche de blocs), il est tué
     * instantanément plutôt que de tomber indéfiniment. Les joueurs en créatif/spectateur
     * ne sont pas concernés.
     */
    private void verifierChuteMortelle(Player player, Location destination) {
        if (destination == null) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.isDead() || player.getHealth() <= 0) return;
        if (plugin.getArenaManager().estSousLaZone(destination)) {
            player.setHealth(0.0);
        }
    }

    @EventHandler
    public void onChute(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (plugin.getArenaManager().estDansArene(player.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPoseBloc(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getArenaManager().estDansArene(event.getBlock().getLocation())) {
            return; // hors arène, comportement vanilla normal
        }

        boolean itemCharge = plugin.getBuildBlockManager().estItemCharge(event.getItemInHand());

        if (!player.hasPermission("loyaltymobs.admin")) {
            // joueurs normaux : uniquement le bloc de charge dédié, sur un type autorisé
            if (!itemCharge) {
                event.setCancelled(true);
                return;
            }
            if (!BlockRegistry.estAutorise(event.getBlock().getType())) {
                event.setCancelled(true);
                return;
            }
        }

        // Qu'il s'agisse d'un admin (qui peut aussi aménager librement l'arène avec n'importe
        // quel bloc) ou d'un joueur normal : si c'est un bloc de charge posé avec l'item dédié,
        // il doit être suivi et programmé pour disparaître après sa durée de vie.
        if (itemCharge) {
            plugin.getBuildBlockManager().enregistrerProprietaire(event.getBlock(), player.getUniqueId());
            plugin.getBuildBlockManager().onBlocPose(player, event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCasseBloc(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Location loc = event.getBlock().getLocation();

        if (!plugin.getArenaManager().estDansArene(loc)) {
            return; // hors arène, comportement vanilla normal
        }
        if (player.hasPermission("loyaltymobs.admin")) {
            return; // seuls les admins peuvent éditer l'arène (terrain ou blocs posés)
        }

        // Personne ne peut casser quoi que ce soit à la main dans l'arène : ni le terrain déjà
        // présent, ni un bloc de construction qu'on vient de poser soi-même — ces derniers
        // disparaissent uniquement tout seuls, après leur durée de vie.
        event.setCancelled(true);
    }

    @EventHandler
    public void onInteragirInvocation(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return; // évite un double déclenchement main + main secondaire
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (!estDansArene(player)) return;
        if (!plugin.getInvocationManager().estItemInvocation(event.getItem())) return;

        event.setCancelled(true);
        plugin.getInvocationManager().ouvrirMenu(player);
    }

    @EventHandler
    public void onInteragirSelecteurBloc(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return; // évite un double déclenchement main + main secondaire
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (!estDansArene(player)) return;
        if (!plugin.getBlockSelectorManager().estItemSelecteur(event.getItem())) return;

        event.setCancelled(true);
        plugin.getBlockSelectorManager().ouvrirMenu(player);
    }

    @EventHandler
    public void onClicMenuInvocation(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof InvocationInventoryHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clique = event.getCurrentItem();
        if (clique == null || !clique.hasItemMeta()) return;

        String typeName = clique.getItemMeta().getPersistentDataContainer().get(Cles.INVOCATION_TYPE, PersistentDataType.STRING);
        if (typeName == null) return;

        EntityType type;
        try {
            type = EntityType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            return;
        }

        player.closeInventory();
        plugin.getInvocationManager().invoquer(player, type);
    }

    @EventHandler
    public void onClicMenuSelecteurBloc(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof BlockSelectorInventoryHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clique = event.getCurrentItem();
        if (clique == null || !clique.hasItemMeta()) return;

        String nomBloc = clique.getItemMeta().getPersistentDataContainer().get(Cles.BLOC_CHOIX, PersistentDataType.STRING);
        if (nomBloc == null) return;

        Material material;
        try {
            material = Material.valueOf(nomBloc);
        } catch (IllegalArgumentException e) {
            return;
        }

        player.closeInventory();
        plugin.getBlockSelectorManager().choisir(player, material);
    }

    @EventHandler
    public void onDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!estDansArene(player)) return;

        ItemStack drop = event.getItemDrop().getItemStack();
        boolean itemDuKit = plugin.getKitManager().estKit(drop);
        boolean itemCharge = plugin.getBuildBlockManager().estItemCharge(drop);
        boolean itemInvocation = plugin.getInvocationManager().estItemInvocation(drop);
        boolean itemSelecteur = plugin.getBlockSelectorManager().estItemSelecteur(drop);
        if (itemDuKit || itemCharge || itemInvocation || itemSelecteur) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClicInventaire(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!estDansArene(player)) return;

        boolean clicSurKit = event.getCurrentItem() != null && (plugin.getKitManager().estKit(event.getCurrentItem())
                || plugin.getBuildBlockManager().estItemCharge(event.getCurrentItem())
                || plugin.getInvocationManager().estItemInvocation(event.getCurrentItem())
                || plugin.getBlockSelectorManager().estItemSelecteur(event.getCurrentItem()));
        boolean curseurKit = event.getCursor() != null && (plugin.getKitManager().estKit(event.getCursor())
                || plugin.getBuildBlockManager().estItemCharge(event.getCursor())
                || plugin.getInvocationManager().estItemInvocation(event.getCursor())
                || plugin.getBlockSelectorManager().estItemSelecteur(event.getCursor()));

        if (clicSurKit || curseurKit) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMort(PlayerDeathEvent event) {
        Player victime = event.getEntity();
        if (!estDansArene(victime)) return;
        Player tueur = victime.getKiller();

        PlayerDataManager data = plugin.getPlayerDataManager();
        data.ajouterMort(victime.getUniqueId());
        data.save(victime.getUniqueId());
        if (tueur != null) {
            data.ajouterKill(tueur.getUniqueId());
            int points = Math.max(0, plugin.getConfig().getInt("arene.points-par-kill", 15));
            data.ajouterPoints(tueur.getUniqueId(), points);
            data.save(tueur.getUniqueId());
            tueur.sendMessage("§7+" + points + " points de fidélité §8(kill sur " + victime.getName() + ")");
        }

        plugin.getScoreboardManager().enregistrerElimination(tueur, victime);
        // Kills/morts/K-D viennent de changer : on rafraîchit l'hologramme de classement
        // s'il est actif, pour qu'il reste à jour sans intervention manuelle.
        plugin.getHologramManager().actualiser();

        // Le kit, les charges de blocs et l'item d'invocation sont prêtés pour la durée du
        // combat en arène : ils ne doivent jamais finir en loot au sol suite à une mort
        // (PvP ou chute dans le vide).
        event.getDrops().removeIf(item -> plugin.getKitManager().estKit(item)
                || plugin.getBuildBlockManager().estItemCharge(item)
                || plugin.getInvocationManager().estItemInvocation(item)
                || plugin.getBlockSelectorManager().estItemSelecteur(item));
    }

    @EventHandler
    public void onDeconnexion(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        joueursDansArene.remove(uuid);
        modeAvantArene.remove(uuid);
        plugin.getBuildBlockManager().sortirDeArene(event.getPlayer());
        plugin.getScoreboardManager().onDeconnexion(uuid);
    }
}
