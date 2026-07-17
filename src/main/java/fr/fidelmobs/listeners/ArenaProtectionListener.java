package fr.fidelmobs.listeners;

import fr.fidelmobs.LoyaltyMobsPlugin;
import fr.fidelmobs.arena.ArenaManager;
import fr.fidelmobs.arena.BlockRegistry;
import fr.fidelmobs.data.PlayerDataManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

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
            plugin.getKitManager().appliquerKit(player);
            plugin.getBuildBlockManager().entrerEnArene(player);
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
        if (player.hasPermission("loyaltymobs.admin")) {
            return; // les admins peuvent aménager librement l'arène
        }

        if (!plugin.getBuildBlockManager().estItemCharge(event.getItemInHand())) {
            event.setCancelled(true);
            return;
        }

        if (!BlockRegistry.estAutorise(event.getBlock().getType())) {
            event.setCancelled(true);
            return;
        }

        plugin.getBuildBlockManager().enregistrerProprietaire(event.getBlock(), player.getUniqueId());
        plugin.getBuildBlockManager().onBlocPose(player, event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCasseBloc(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Location loc = event.getBlock().getLocation();

        if (!plugin.getArenaManager().estDansArene(loc)) {
            return; // hors arène, comportement vanilla normal
        }
        if (player.hasPermission("loyaltymobs.admin")) {
            return; // les admins peuvent aménager librement l'arène
        }

        if (plugin.getBuildBlockManager().estProprietaire(event.getBlock(), player.getUniqueId())) {
            // le joueur casse son propre bloc posé : autorisé, pas de drop
            event.setDropItems(false);
            plugin.getBuildBlockManager().retirerTracking(event.getBlock());
        } else {
            // impossible de casser le terrain ou les blocs des autres en arène
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!estDansArene(player)) return;

        boolean itemDuKit = plugin.getKitManager().estKit(event.getItemDrop().getItemStack());
        boolean itemCharge = plugin.getBuildBlockManager().estItemCharge(event.getItemDrop().getItemStack());
        if (itemDuKit || itemCharge) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClicInventaire(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!estDansArene(player)) return;

        boolean clicSurKit = event.getCurrentItem() != null && (plugin.getKitManager().estKit(event.getCurrentItem())
                || plugin.getBuildBlockManager().estItemCharge(event.getCurrentItem()));
        boolean curseurKit = event.getCursor() != null && (plugin.getKitManager().estKit(event.getCursor())
                || plugin.getBuildBlockManager().estItemCharge(event.getCursor()));

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
            data.save(tueur.getUniqueId());
        }

        plugin.getScoreboardManager().enregistrerElimination(tueur, victime);

        // Le kit et les charges de blocs sont prêtés pour la durée du combat en arène :
        // ils ne doivent jamais finir en loot au sol suite à une mort (PvP ou chute dans le vide).
        event.getDrops().removeIf(item -> plugin.getKitManager().estKit(item)
                || plugin.getBuildBlockManager().estItemCharge(item));
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
