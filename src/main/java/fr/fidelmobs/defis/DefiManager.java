package fr.fidelmobs.defis;

import fr.fidelmobs.LoyaltyMobsPlugin;
import fr.fidelmobs.data.PlayerDataManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Gère le menu /defi (achievements globaux + défis quotidiens) : construction du GUI
 * paginé, vérification périodique de la progression de tous les joueurs connectés, et
 * attribution automatique des récompenses (points de fidélité, tickets bonus) dès qu'un
 * défi est accompli.
 */
public class DefiManager {

    private static final int LIGNES = 6;
    private static final int SLOTS_PAR_PAGE = LIGNES * 9 - 9; // dernière ligne réservée à la navigation
    private static final int SLOT_PAGE_PRECEDENTE = 45;
    private static final int SLOT_BASCULE_ONGLET = 49;
    private static final int SLOT_RESUME = 48;
    private static final int SLOT_PAGE_SUIVANTE = 53;

    private final LoyaltyMobsPlugin plugin;

    public DefiManager(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
        demarrerVerificationPeriodique();
    }

    /**
     * Vérifie toutes les ~8 secondes, pour chaque joueur connecté, si un nouveau défi
     * (global ou quotidien) vient d'être accompli, et distribue la récompense le cas
     * échéant. Un délai de quelques secondes est largement suffisant pour un système de
     * défis (contrairement à des mécaniques de combat), et évite d'avoir à instrumenter
     * précisément chaque évènement du plugin.
     */
    private void demarrerVerificationPeriodique() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                verifierEtRecompenser(player);
            }
        }, 100L, 160L);
    }

    /**
     * Vérifie tous les défis (globaux + quotidiens du jour) pour ce joueur et distribue
     * la récompense de chaque défi fraîchement accompli. Peut être appelée à la demande
     * (ouverture du menu, après une action marquante) en plus de la vérification périodique.
     */
    public void verifierEtRecompenser(Player player) {
        PlayerDataManager data = plugin.getPlayerDataManager();
        UUID uuid = player.getUniqueId();

        for (Defi defi : DefiRegistry.GLOBAL) {
            if (data.estDefiComplete(uuid, defi.id())) continue;
            if (!defi.estComplete(data, uuid)) continue;
            data.marquerDefiComplete(uuid, defi.id());
            recompenser(player, defi);
        }

        for (Defi defi : DefiRegistry.defisQuotidiensDuJour()) {
            if (data.estDefiQuotidienComplete(uuid, defi.id())) continue;
            if (!defi.estComplete(data, uuid)) continue;
            data.marquerDefiQuotidienComplete(uuid, defi.id());
            recompenser(player, defi);
        }

        data.save(uuid);
    }

    private void recompenser(Player player, Defi defi) {
        PlayerDataManager data = plugin.getPlayerDataManager();
        UUID uuid = player.getUniqueId();

        data.ajouterPoints(uuid, defi.recompensePoints());
        if (defi.recompenseTickets() > 0) {
            data.addTickets(uuid, defi.recompenseTickets());
        }

        String suffixeTickets = defi.recompenseTickets() > 0
                ? " §7et §e+" + defi.recompenseTickets() + " ticket(s)"
                : "";
        player.sendTitle(defi.rarete().getCouleur() + "§l✦ Défi accompli !",
                defi.rarete().getCouleur() + defi.nom(), 5, 60, 15);
        player.sendMessage(defi.rarete().getCouleur() + "✦ Défi accompli : §l" + defi.nom()
                + "§r §7— §a+" + defi.recompensePoints() + " points" + suffixeTickets);
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
    }

    // ---- GUI ----

    public void ouvrirMenu(Player player, boolean quotidien, int page) {
        verifierEtRecompenser(player);

        PlayerDataManager data = plugin.getPlayerDataManager();
        List<Defi> liste = quotidien ? DefiRegistry.defisQuotidiensDuJour() : DefiRegistry.GLOBAL;
        int nbPages = Math.max(1, (liste.size() - 1) / SLOTS_PAR_PAGE + 1);
        int pageBornee = Math.max(0, Math.min(page, nbPages - 1));

        DefiInventoryHolder holder = new DefiInventoryHolder();
        holder.setQuotidien(quotidien);
        holder.setPage(pageBornee);

        String titre = "§d✦ Défis — " + (quotidien ? "Quotidiens" : "Globaux")
                + " (" + (pageBornee + 1) + "/" + nbPages + ")";
        Inventory inv = Bukkit.createInventory(holder, LIGNES * 9, titre);
        holder.setInventory(inv);

        int debut = pageBornee * SLOTS_PAR_PAGE;
        int fin = Math.min(liste.size(), debut + SLOTS_PAR_PAGE);
        int slot = 0;
        int nbCompletes = 0;
        for (int i = debut; i < fin; i++) {
            inv.setItem(slot++, construireIcone(liste.get(i), data, player.getUniqueId(), quotidien));
        }
        for (Defi defi : liste) {
            boolean complete = quotidien
                    ? data.estDefiQuotidienComplete(player.getUniqueId(), defi.id())
                    : data.estDefiComplete(player.getUniqueId(), defi.id());
            if (complete) nbCompletes++;
        }

        ItemStack filler = filler();
        for (int i = SLOTS_PAR_PAGE; i < LIGNES * 9; i++) {
            inv.setItem(i, filler);
        }

        if (pageBornee > 0) {
            inv.setItem(SLOT_PAGE_PRECEDENTE, nommer(Material.ARROW, "§e« Page précédente", List.of()));
        }
        if (pageBornee < nbPages - 1) {
            inv.setItem(SLOT_PAGE_SUIVANTE, nommer(Material.ARROW, "§ePage suivante »", List.of()));
        }
        inv.setItem(SLOT_BASCULE_ONGLET, nommer(
                quotidien ? Material.CLOCK : Material.NETHER_STAR,
                quotidien ? "§bClique : voir les défis §lGlobaux" : "§dClique : voir les défis §lQuotidiens",
                List.of("§7Onglet actuel : " + (quotidien ? "§bQuotidiens" : "§dGlobaux"))));
        inv.setItem(SLOT_RESUME, nommer(Material.PAPER, "§f✦ Résumé", List.of(
                "§7Accomplis : §a" + nbCompletes + " §7/ §f" + liste.size(),
                "",
                quotidien ? "§7Les défis quotidiens se réactualisent" : "§7Défis globaux : progression permanente,",
                quotidien ? "§7chaque jour à minuit." : "§7jamais réinitialisée."
        )));

        player.openInventory(inv);
    }

    private ItemStack construireIcone(Defi defi, PlayerDataManager data, UUID uuid, boolean quotidien) {
        boolean complete = quotidien
                ? data.estDefiQuotidienComplete(uuid, defi.id())
                : data.estDefiComplete(uuid, defi.id());

        ItemStack icone = new ItemStack(defi.icone());
        ItemMeta meta = icone.getItemMeta();
        meta.setDisplayName((complete ? "§a✔ " : defi.rarete().getCouleur()) + "§l" + defi.nom());

        List<String> lore = new ArrayList<>();
        lore.add("§7Difficulté : " + defi.rarete().getCouleur() + defi.rarete().getLabel());
        lore.add("");
        for (String ligne : decouper(defi.description(), 40)) {
            lore.add("§7" + ligne);
        }
        lore.add("");
        lore.add("§7Progression : §f" + defi.texteProgression(data, uuid));
        String recompense = "§7Récompense : §a+" + defi.recompensePoints() + " points";
        if (defi.recompenseTickets() > 0) {
            recompense += " §7et §e+" + defi.recompenseTickets() + " ticket(s)";
        }
        lore.add(recompense);
        lore.add("");
        lore.add(complete ? "§a✔ Défi accompli !" : "§8Pas encore accompli");
        meta.setLore(lore);

        if (complete) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        icone.setItemMeta(meta);
        return icone;
    }

    private ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack nommer(Material material, String nom, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(nom);
        if (!lore.isEmpty()) meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** Découpe une description en lignes d'à peu près {@code largeur} caractères, mot par mot. */
    private List<String> decouper(String texte, int largeur) {
        List<String> lignes = new ArrayList<>();
        StringBuilder courante = new StringBuilder();
        for (String mot : texte.split(" ")) {
            if (courante.length() + mot.length() + 1 > largeur) {
                lignes.add(courante.toString());
                courante = new StringBuilder();
            }
            if (!courante.isEmpty()) courante.append(' ');
            courante.append(mot);
        }
        if (!courante.isEmpty()) lignes.add(courante.toString());
        return lignes;
    }

    public void gererClicNavigation(Player player, DefiInventoryHolder holder, int slotClique) {
        if (slotClique == SLOT_PAGE_PRECEDENTE) {
            ouvrirMenu(player, holder.isQuotidien(), holder.getPage() - 1);
        } else if (slotClique == SLOT_PAGE_SUIVANTE) {
            ouvrirMenu(player, holder.isQuotidien(), holder.getPage() + 1);
        } else if (slotClique == SLOT_BASCULE_ONGLET) {
            ouvrirMenu(player, !holder.isQuotidien(), 0);
        }
    }
}
