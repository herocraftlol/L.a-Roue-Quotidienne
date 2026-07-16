package fr.fidelmobs.listeners;

import fr.fidelmobs.LoyaltyMobsPlugin;
import fr.fidelmobs.arena.ArenaManager;
import fr.fidelmobs.arena.BlockRegistry;
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

        gererChangementZone(event.getPlayer(), event.getTo());
    }

    @EventHandler
    public void onTeleportation(PlayerTeleportEvent event) {
        // Une téléportation (commande /tp, ender pearl, portail...) doit être détectée
        // immédiatement, sans attendre un futur PlayerMoveEvent qui peut être retardé
        // ou ne jamais correspondre à un vrai changement de bloc suivi.
        gererChangementZone(event.getPlayer(), event.getTo());
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
        plugin.getScoreboardManager().enregistrerElimination(tueur, victime);
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
