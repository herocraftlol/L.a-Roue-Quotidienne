package fr.fidelmobs.managers;

import fr.fidelmobs.Cles;
import fr.fidelmobs.LoyaltyMobsPlugin;
import fr.fidelmobs.arena.PowerRegistry;
import fr.fidelmobs.data.PlayerDataManager;
import fr.fidelmobs.mobs.MobRarity;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Item donné dans l'arène (avant-avant-avant-dernier slot de la hotbar, 6e sur 9) pour
 * ouvrir un menu regroupant tous les pouvoirs spéciaux obtenus à la roue et en équiper un,
 * qui apparaît alors utilisable au 5e slot (voir {@link PowerUseManager}).
 *
 * Chaque pouvoir a ses propres charges (une par copie obtenue à la roue) et son propre temps
 * de recharge, totalement indépendant des autres : obtenir plusieurs fois le même pouvoir
 * permet de l'utiliser plusieurs fois de suite avant de devoir attendre, et utiliser un
 * pouvoir n'affecte jamais la disponibilité d'un autre pouvoir possédé.
 */
public class PowerSelectorManager {

    public static final int SLOT_SELECTEUR_POUVOIR = 5; // 6e slot de la barre d'accès rapide

    private final LoyaltyMobsPlugin plugin;

    public PowerSelectorManager(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
    }

    public ItemStack creerItem() {
        ItemStack item = new ItemStack(Material.BEACON);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§b§l✦ Pouvoirs spéciaux");
        meta.setLore(List.of(
                "§7Clic droit pour choisir le pouvoir",
                "§7à utiliser parmi ta collection.",
                "§7Le pouvoir choisi s'active ensuite",
                "§7avec le 5e slot de ta hotbar.",
                "§7Chaque pouvoir a ses propres charges",
                "§7et sa propre recharge, indépendantes",
                "§7des autres pouvoirs possédés."
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(Cles.POUVOIR_SELECTEUR, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean estItemSelecteur(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(Cles.POUVOIR_SELECTEUR, PersistentDataType.BYTE);
    }

    public void donnerItem(Player player) {
        player.getInventory().setItem(SLOT_SELECTEUR_POUVOIR, creerItem());
    }

    public void retirerItem(Player player) {
        ItemStack actuel = player.getInventory().getItem(SLOT_SELECTEUR_POUVOIR);
        if (estItemSelecteur(actuel)) {
            player.getInventory().setItem(SLOT_SELECTEUR_POUVOIR, null);
        }
    }

    public void ouvrirMenu(Player player) {
        PlayerDataManager data = plugin.getPlayerDataManager();
        UUID uuid = player.getUniqueId();
        Map<String, Integer> pouvoirs = data.getPouvoirsPossedes(uuid);

        if (pouvoirs.isEmpty()) {
            player.sendMessage("§7Tu n'as encore aucun pouvoir spécial. Utilise §f/roue §7pour en obtenir !");
            return;
        }

        List<String> tries = pouvoirs.keySet().stream()
                .sorted(Comparator.comparing((String id) -> PowerRegistry.getRarete(id).ordinal())
                        .reversed()
                        .thenComparing(id -> id))
                .collect(Collectors.toList());

        int taille = Math.min(54, Math.max(9, ((tries.size() - 1) / 9 + 1) * 9));
        PowerSelectorInventoryHolder holder = new PowerSelectorInventoryHolder();
        Inventory inv = Bukkit.createInventory(holder, taille, "§b✦ Pouvoirs spéciaux");
        holder.setInventory(inv);

        String equipe = data.getPouvoirEquipe(uuid);
        for (String id : tries) {
            inv.addItem(creerIcone(player, id, pouvoirs.get(id), id.equals(equipe)));
        }

        player.openInventory(inv);
    }

    /**
     * Traite un clic sur une icône du menu : équipe le pouvoir correspondant, qui apparaît
     * ensuite au 5e slot de la hotbar (dans l'arène) prêt à être activé.
     */
    public void choisir(Player player, String id) {
        PlayerDataManager data = plugin.getPlayerDataManager();
        UUID uuid = player.getUniqueId();
        if (data.getNombrePouvoir(uuid, id) <= 0) return;

        data.setPouvoirEquipe(uuid, id);
        data.save(uuid);
        player.sendMessage("§aPouvoir équipé : " + PowerRegistry.getRarete(id).getCouleur()
                + PowerRegistry.getNom(id));

        if (plugin.getArenaProtectionListener().estDansArene(player)) {
            plugin.getPowerUseManager().equiper(player);
            player.updateInventory();
        }
    }

    private ItemStack creerIcone(Player player, String id, int nombre, boolean equipe) {
        PlayerDataManager data = plugin.getPlayerDataManager();
        int disponibles = data.getUnitesDisponiblesPouvoir(player.getUniqueId(), id);
        MobRarity rarete = PowerRegistry.getRarete(id);

        ItemStack icone = PowerRegistry.construireIcone(id);
        ItemMeta meta = icone.getItemMeta();
        List<String> lore = new ArrayList<>(meta.hasLore() ? meta.getLore() : List.of());
        lore.add("");
        lore.add("§7Copies possédées : " + rarete.getCouleur() + nombre);
        if (disponibles > 0) {
            lore.add("§aCharges disponibles : " + disponibles + "/" + nombre);
        } else {
            long prochaine = data.getProchaineDisponibilitePouvoir(player.getUniqueId(), id);
            String attente = prochaine > 0 ? formatDuree(prochaine - System.currentTimeMillis()) : "bientôt";
            lore.add("§cToutes les charges sont en recharge");
            lore.add("§7Prochaine dans §e" + attente);
        }
        lore.add("");
        lore.add(equipe ? "§aPouvoir actuellement équipé" : "§eClique pour équiper !");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(Cles.POUVOIR_CHOIX_ID, PersistentDataType.STRING, id);
        icone.setItemMeta(meta);
        return icone;
    }

    private String formatDuree(long ms) {
        long totalSecondes = Math.max(0, ms / 1000);
        long minutes = totalSecondes / 60;
        long secondes = totalSecondes % 60;
        if (minutes > 0) {
            return minutes + " min " + secondes + " s";
        }
        return secondes + " s";
    }
}
