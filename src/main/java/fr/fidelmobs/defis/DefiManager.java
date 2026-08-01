package fr.fidelmobs.defis;

import fr.fidelmobs.Cles;
import fr.fidelmobs.LoyaltyMobsPlugin;
import fr.fidelmobs.data.PlayerDataManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Gère le menu /defi (achievements globaux + défis quotidiens) : construction du GUI
 * paginé et suivi de la progression de tous les joueurs connectés. Un défi réussi est
 * détecté silencieusement en arrière-plan (aucun message, aucun titre) : il apparaît juste
 * comme "prêt à récupérer" dans le menu, avec une icône dorée et brillante. La récompense
 * (points + éventuels tickets bonus) n'est distribuée, avec confirmation, que lorsque le
 * joueur clique dessus dans le menu — ainsi rien ne s'affiche à l'écran pendant un combat
 * ou toute autre action simplement parce qu'un défi vient d'être complété.
 */
public class DefiManager {

    private static final int LIGNES = 6;
    private static final int SLOTS_PAR_PAGE = LIGNES * 9 - 9; // dernière ligne réservée à la navigation
    private static final int SLOT_PAGE_PRECEDENTE = 45;
    private static final int SLOT_RECUPERER_TOUT = 47;
    private static final int SLOT_RESUME = 48;
    private static final int SLOT_BASCULE_ONGLET = 49;
    private static final int SLOT_PAGE_SUIVANTE = 53;

    private final LoyaltyMobsPlugin plugin;

    public DefiManager(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
        demarrerVerificationPeriodique();
    }

    /**
     * Vérifie toutes les ~8 secondes, pour chaque joueur connecté, si un nouveau défi
     * (global ou quotidien) vient d'être accompli, et le marque simplement comme tel —
     * sans distribuer la récompense ni afficher quoi que ce soit : le joueur la récupérera
     * lui-même depuis le menu /defi quand il le souhaite.
     */
    private void demarrerVerificationPeriodique() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                marquerProgression(player);
            }
        }, 100L, 160L);
    }

    /**
     * Marque comme "accompli" (mais pas encore récupéré) tout défi (global ou quotidien du
     * jour) fraîchement complété. Pas de titre ni de son — juste une ligne de chat discrète
     * et positive incitant à faire /defi, pour ne pas casser l'ambiance en plein combat.
     */
    public void marquerProgression(Player player) {
        PlayerDataManager data = plugin.getPlayerDataManager();
        UUID uuid = player.getUniqueId();

        for (Defi defi : DefiRegistry.GLOBAL) {
            if (data.estDefiComplete(uuid, defi.id())) continue;
            if (defi.estComplete(data, uuid)) {
                data.marquerDefiComplete(uuid, defi.id());
                signalerDiscretement(player, defi);
            }
        }

        for (Defi defi : DefiRegistry.defisQuotidiensDuJour()) {
            if (data.estDefiQuotidienComplete(uuid, defi.id())) continue;
            if (defi.estComplete(data, uuid)) {
                data.marquerDefiQuotidienComplete(uuid, defi.id());
                signalerDiscretement(player, defi);
            }
        }
    }

    private void signalerDiscretement(Player player, Defi defi) {
        player.sendMessage("§7✦ Défi accompli : §f" + defi.nom() + " §7— tape §e/defi §7pour récupérer ta récompense !");
    }

    /** Nombre total de récompenses (globales + quotidiennes) accomplies mais pas encore réclamées. */
    public int compterRecompensesEnAttente(UUID uuid) {
        PlayerDataManager data = plugin.getPlayerDataManager();
        int total = 0;
        for (Defi defi : DefiRegistry.GLOBAL) {
            if (data.estDefiComplete(uuid, defi.id()) && !data.estDefiRecupere(uuid, defi.id())) total++;
        }
        for (Defi defi : DefiRegistry.defisQuotidiensDuJour()) {
            if (data.estDefiQuotidienComplete(uuid, defi.id()) && !data.estDefiQuotidienRecupere(uuid, defi.id())) total++;
        }
        return total;
    }

    /**
     * Récupère la récompense d'un défi accompli mais pas encore réclamé (appelé par un clic
     * sur son icône dans le menu). C'est le SEUL endroit où la récompense est distribuée et
     * où un message/son de confirmation apparaît.
     */
    private void recuperer(Player player, String defiId, boolean quotidien) {
        PlayerDataManager data = plugin.getPlayerDataManager();
        UUID uuid = player.getUniqueId();

        Defi defi = quotidien
                ? DefiRegistry.defisQuotidiensDuJour().stream().filter(d -> d.id().equals(defiId)).findFirst().orElse(null)
                : DefiRegistry.parId(defiId);
        if (defi == null) return;

        boolean complete = quotidien ? data.estDefiQuotidienComplete(uuid, defiId) : data.estDefiComplete(uuid, defiId);
        boolean dejaRecupere = quotidien ? data.estDefiQuotidienRecupere(uuid, defiId) : data.estDefiRecupere(uuid, defiId);
        if (!complete || dejaRecupere) {
            return; // rien à récupérer (pas encore accompli, ou déjà réclamé)
        }

        if (quotidien) {
            data.marquerDefiQuotidienRecupere(uuid, defiId);
        } else {
            data.marquerDefiRecupere(uuid, defiId);
        }

        data.ajouterPoints(uuid, defi.recompensePoints());
        if (defi.recompenseTickets() > 0) {
            data.addTickets(uuid, defi.recompenseTickets());
        }
        data.save(uuid);

        String suffixeTickets = defi.recompenseTickets() > 0
                ? " §7et §e+" + defi.recompenseTickets() + " ticket(s)"
                : "";
        player.sendMessage(defi.rarete().getCouleur() + "✦ Récompense récupérée : §l" + defi.nom()
                + "§r §7— §a+" + defi.recompensePoints() + " points" + suffixeTickets);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);

        // Réaffiche le menu à la même page pour montrer l'icône fraîchement marquée récupérée.
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof DefiInventoryHolder holder) {
            ouvrirMenu(player, holder.isQuotidien(), holder.getPage());
        }
    }

    /**
     * Récupère en une fois TOUTES les récompenses disponibles (défis globaux ET quotidiens
     * du jour confondus, peu importe l'onglet actuellement affiché), plutôt que de devoir
     * cliquer sur chaque défi un par un.
     */
    public void recupererTout(Player player) {
        PlayerDataManager data = plugin.getPlayerDataManager();
        UUID uuid = player.getUniqueId();
        int totalPoints = 0;
        int totalTickets = 0;
        int nombre = 0;

        for (Defi defi : DefiRegistry.GLOBAL) {
            if (data.estDefiComplete(uuid, defi.id()) && !data.estDefiRecupere(uuid, defi.id())) {
                data.marquerDefiRecupere(uuid, defi.id());
                data.ajouterPoints(uuid, defi.recompensePoints());
                if (defi.recompenseTickets() > 0) data.addTickets(uuid, defi.recompenseTickets());
                totalPoints += defi.recompensePoints();
                totalTickets += defi.recompenseTickets();
                nombre++;
            }
        }
        for (Defi defi : DefiRegistry.defisQuotidiensDuJour()) {
            if (data.estDefiQuotidienComplete(uuid, defi.id()) && !data.estDefiQuotidienRecupere(uuid, defi.id())) {
                data.marquerDefiQuotidienRecupere(uuid, defi.id());
                data.ajouterPoints(uuid, defi.recompensePoints());
                if (defi.recompenseTickets() > 0) data.addTickets(uuid, defi.recompenseTickets());
                totalPoints += defi.recompensePoints();
                totalTickets += defi.recompenseTickets();
                nombre++;
            }
        }
        data.save(uuid);

        if (nombre == 0) {
            player.sendMessage("§7Aucune récompense à récupérer pour le moment.");
            return;
        }

        String suffixe = totalTickets > 0 ? " §7et §e+" + totalTickets + " ticket(s)" : "";
        player.sendMessage("§a✦ " + nombre + " récompense(s) récupérée(s) — §a+" + totalPoints + " points" + suffixe);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);

        if (player.getOpenInventory().getTopInventory().getHolder() instanceof DefiInventoryHolder holder) {
            ouvrirMenu(player, holder.isQuotidien(), holder.getPage());
        }
    }

    // ---- GUI ----

    public void ouvrirMenu(Player player, boolean quotidien, int page) {
        marquerProgression(player);

        PlayerDataManager data = plugin.getPlayerDataManager();
        List<Defi> liste = quotidien ? DefiRegistry.defisQuotidiensDuJour() : DefiRegistry.GLOBAL;
        int nbPages = Math.max(1, (liste.size() - 1) / SLOTS_PAR_PAGE + 1);
        int pageBornee = Math.max(0, Math.min(page, nbPages - 1));

        DefiInventoryHolder holder = new DefiInventoryHolder();
        holder.setQuotidien(quotidien);
        holder.setPage(pageBornee);

        int nbCompletes = 0;
        int nbARecuperer = 0;
        for (Defi defi : liste) {
            boolean complete = quotidien
                    ? data.estDefiQuotidienComplete(player.getUniqueId(), defi.id())
                    : data.estDefiComplete(player.getUniqueId(), defi.id());
            boolean recupere = quotidien
                    ? data.estDefiQuotidienRecupere(player.getUniqueId(), defi.id())
                    : data.estDefiRecupere(player.getUniqueId(), defi.id());
            if (complete) nbCompletes++;
            if (complete && !recupere) nbARecuperer++;
        }

        String titre = "§d✦ Défis — " + (quotidien ? "Quotidiens" : "Globaux")
                + " (" + (pageBornee + 1) + "/" + nbPages + ")"
                + (nbARecuperer > 0 ? " §e[" + nbARecuperer + " à récupérer]" : "");
        Inventory inv = Bukkit.createInventory(holder, LIGNES * 9, titre);
        holder.setInventory(inv);

        int debut = pageBornee * SLOTS_PAR_PAGE;
        int fin = Math.min(liste.size(), debut + SLOTS_PAR_PAGE);
        int slot = 0;
        for (int i = debut; i < fin; i++) {
            inv.setItem(slot++, construireIcone(liste.get(i), data, player.getUniqueId(), quotidien));
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
        // Nombre de récompenses en attente sur l'AUTRE onglet aussi, pour que le bouton
        // "tout récupérer" (qui agit sur les deux onglets à la fois) affiche un total complet.
        List<Defi> autreListe = quotidien ? DefiRegistry.GLOBAL : DefiRegistry.defisQuotidiensDuJour();
        int nbARecupererAutreOnglet = 0;
        for (Defi defi : autreListe) {
            boolean completeAutre = quotidien
                    ? data.estDefiComplete(player.getUniqueId(), defi.id())
                    : data.estDefiQuotidienComplete(player.getUniqueId(), defi.id());
            boolean recupereAutre = quotidien
                    ? data.estDefiRecupere(player.getUniqueId(), defi.id())
                    : data.estDefiQuotidienRecupere(player.getUniqueId(), defi.id());
            if (completeAutre && !recupereAutre) nbARecupererAutreOnglet++;
        }
        int totalARecuperer = nbARecuperer + nbARecupererAutreOnglet;

        inv.setItem(SLOT_RECUPERER_TOUT, nommer(
                totalARecuperer > 0 ? Material.CHEST : Material.BARRIER,
                totalARecuperer > 0 ? "§e★ Tout récupérer (" + totalARecuperer + ")" : "§7Rien à récupérer",
                totalARecuperer > 0
                        ? List.of("§7Récupère en un clic toutes les", "§7récompenses disponibles,", "§7globales ET quotidiennes.")
                        : List.of("§7Aucune récompense en attente", "§7pour le moment.")));

        inv.setItem(SLOT_BASCULE_ONGLET, nommer(
                quotidien ? Material.CLOCK : Material.NETHER_STAR,
                quotidien ? "§bClique : voir les défis §lGlobaux" : "§dClique : voir les défis §lQuotidiens",
                List.of("§7Onglet actuel : " + (quotidien ? "§bQuotidiens" : "§dGlobaux"))));
        inv.setItem(SLOT_RESUME, nommer(Material.PAPER, "§f✦ Résumé", List.of(
                "§7Accomplis : §a" + nbCompletes + " §7/ §f" + liste.size(),
                "§7Récompenses en attente : §e" + nbARecuperer,
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
        boolean recupere = complete && (quotidien
                ? data.estDefiQuotidienRecupere(uuid, defi.id())
                : data.estDefiRecupere(uuid, defi.id()));
        boolean aRecuperer = complete && !recupere;

        ItemStack icone = new ItemStack(defi.icone());
        ItemMeta meta = icone.getItemMeta();
        String prefixeNom = recupere ? "§a✔ " : aRecuperer ? "§e★ " : defi.rarete().getCouleur();
        meta.setDisplayName(prefixeNom + "§l" + defi.nom());

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
        if (aRecuperer) {
            lore.add("§e★ Clique ici pour récupérer ta récompense !");
        } else if (recupere) {
            lore.add("§a✔ Récompense récupérée.");
        } else {
            lore.add("§8Pas encore accompli.");
        }
        meta.setLore(lore);

        if (recupere || aRecuperer) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        meta.getPersistentDataContainer().set(Cles.DEFI_ID, PersistentDataType.STRING, defi.id());
        meta.getPersistentDataContainer().set(Cles.DEFI_QUOTIDIEN, PersistentDataType.INTEGER, quotidien ? 1 : 0);
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

    /**
     * Gère tout clic dans le menu /defi : navigation (page précédente/suivante, bascule
     * d'onglet) ou récupération de récompense (clic sur l'icône d'un défi accompli).
     */
    public void gererClic(Player player, DefiInventoryHolder holder, int slotClique, ItemStack itemClique) {
        if (slotClique == SLOT_PAGE_PRECEDENTE) {
            ouvrirMenu(player, holder.isQuotidien(), holder.getPage() - 1);
            return;
        }
        if (slotClique == SLOT_PAGE_SUIVANTE) {
            ouvrirMenu(player, holder.isQuotidien(), holder.getPage() + 1);
            return;
        }
        if (slotClique == SLOT_BASCULE_ONGLET) {
            ouvrirMenu(player, !holder.isQuotidien(), 0);
            return;
        }
        if (slotClique == SLOT_RECUPERER_TOUT) {
            recupererTout(player);
            return;
        }

        if (itemClique == null || !itemClique.hasItemMeta()) return;
        String defiId = itemClique.getItemMeta().getPersistentDataContainer().get(Cles.DEFI_ID, PersistentDataType.STRING);
        Integer quotidienFlag = itemClique.getItemMeta().getPersistentDataContainer().get(Cles.DEFI_QUOTIDIEN, PersistentDataType.INTEGER);
        if (defiId == null || quotidienFlag == null) return;

        recuperer(player, defiId, quotidienFlag == 1);
    }
}
