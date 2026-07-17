package fr.fidelmobs.managers;

import fr.fidelmobs.Cles;
import fr.fidelmobs.LoyaltyMobsPlugin;
import fr.fidelmobs.arena.BlockRegistry;
import fr.fidelmobs.data.PlayerDataManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BuildBlockManager {

    public static final int TAILLE_PACK = 32;
    public static final int SLOT_BLOC = 1; // 2e slot de la barre d'accès rapide (index 1)

    private final LoyaltyMobsPlugin plugin;
    private final Map<UUID, Integer> charges = new HashMap<>();
    private final Map<UUID, BukkitTask> tachesRegen = new HashMap<>();
    // emplacement (clé encodée) -> proprietaire, pour n'autoriser que le poseur a casser prematurement son bloc
    private final Map<Long, UUID> proprietairesBlocsPoses = new HashMap<>();

    public BuildBlockManager(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
    }

    private long cleLocation(Location loc) {
        return loc.getBlockX() * 100000000L + loc.getBlockY() * 100000L + (loc.getBlockZ() & 0xFFFFF);
    }

    public void enregistrerProprietaire(Block block, UUID proprietaire) {
        proprietairesBlocsPoses.put(cleLocation(block.getLocation()), proprietaire);
    }

    public boolean estProprietaire(Block block, UUID joueur) {
        UUID proprietaire = proprietairesBlocsPoses.get(cleLocation(block.getLocation()));
        return proprietaire != null && proprietaire.equals(joueur);
    }

    public void retirerTracking(Block block) {
        proprietairesBlocsPoses.remove(cleLocation(block.getLocation()));
    }

    private ItemStack creerItemBloc(Material material, int quantite) {
        if (quantite <= 0) quantite = 1; // Bukkit n'aime pas les stacks à 0, on masque via l'affichage
        ItemStack item = new ItemStack(material, Math.max(1, quantite));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§bBlocs de construction");
        meta.getPersistentDataContainer().set(Cles.CHARGE_BLOC, PersistentDataType.INTEGER, 1);
        item.setItemMeta(meta);
        return item;
    }

    private Material blocActifOuDefaut(UUID uuid) {
        PlayerDataManager data = plugin.getPlayerDataManager();
        Material actif = data.getBlocActif(uuid);
        if (actif != null && data.getBlocsDebloques(uuid).contains(actif)) {
            return actif;
        }
        return BlockRegistry.getBlocParDefaut();
    }

    public void entrerEnArene(Player player) {
        UUID uuid = player.getUniqueId();
        charges.put(uuid, TAILLE_PACK);
        resynchroniser(player);
        demarrerRegen(player);
    }

    public void sortirDeArene(Player player) {
        UUID uuid = player.getUniqueId();
        BukkitTask tache = tachesRegen.remove(uuid);
        if (tache != null) tache.cancel();
        charges.remove(uuid);
        if (player.getInventory().getItem(SLOT_BLOC) != null && estItemCharge(player.getInventory().getItem(SLOT_BLOC))) {
            player.getInventory().setItem(SLOT_BLOC, null);
        }
    }

    public void resynchroniser(Player player) {
        int n = charges.getOrDefault(player.getUniqueId(), 0);
        if (n <= 0) {
            player.getInventory().setItem(SLOT_BLOC, null);
            return;
        }
        player.getInventory().setItem(SLOT_BLOC, creerItemBloc(blocActifOuDefaut(player.getUniqueId()), n));
    }

    private void demarrerRegen(Player player) {
        UUID uuid = player.getUniqueId();
        BukkitTask ancienne = tachesRegen.remove(uuid);
        if (ancienne != null) ancienne.cancel();

        long periode = Math.max(1, plugin.getConfig().getInt("arene.regen-bloc-secondes", 3)) * 20L;

        BukkitTask tache = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !plugin.getArenaManager().estDansArene(player.getLocation())) {
                    cancel();
                    tachesRegen.remove(uuid);
                    return;
                }
                int n = charges.getOrDefault(uuid, 0);
                if (n < TAILLE_PACK) {
                    charges.put(uuid, n + 1);
                }
                // On revalide systématiquement l'affichage à chaque passage (pas uniquement
                // quand les charges augmentent) : garde-fou contre toute désynchronisation
                // client qu'on n'aurait pas anticipée ailleurs.
                resynchroniser(player);
            }
        }.runTaskTimer(plugin, periode, periode);

        tachesRegen.put(uuid, tache);
    }

    public boolean estItemCharge(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Integer v = item.getItemMeta().getPersistentDataContainer().get(Cles.CHARGE_BLOC, PersistentDataType.INTEGER);
        return v != null && v == 1;
    }

    /**
     * Consomme une charge suite à une pose de bloc réussie, et programme la disparition du bloc posé.
     */
    public void onBlocPose(Player player, Block block) {
        UUID uuid = player.getUniqueId();
        int n = charges.getOrDefault(uuid, 0);
        charges.put(uuid, Math.max(0, n - 1));

        Material typePose = block.getType();
        Location loc = block.getLocation();
        int dureeVie = plugin.getConfig().getInt("arene.duree-vie-bloc-secondes", 10);

        plugin.getServer().getScheduler().runTask(plugin, () -> resynchroniser(player));

        new BukkitRunnable() {
            @Override
            public void run() {
                Block b = loc.getBlock();
                if (b.getType() == typePose) {
                    b.setType(Material.AIR);
                }
                retirerTracking(b);
            }
        }.runTaskLater(plugin, dureeVie * 20L);
    }

    public void arreterTout() {
        for (BukkitTask t : tachesRegen.values()) {
            t.cancel();
        }
        tachesRegen.clear();
        charges.clear();
        proprietairesBlocsPoses.clear();
    }
}
