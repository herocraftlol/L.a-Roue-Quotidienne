package fr.fidelmobs.shop;

import fr.fidelmobs.Cles;
import fr.fidelmobs.LoyaltyMobsPlugin;
import fr.fidelmobs.arena.BlockRegistry;
import fr.fidelmobs.arena.EconomieValeurs;
import fr.fidelmobs.arena.GearRegistry;
import fr.fidelmobs.arena.PowerRegistry;
import fr.fidelmobs.data.PlayerDataManager;
import fr.fidelmobs.mobs.MobRarity;
import fr.fidelmobs.mobs.MobRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Boutique (/shop) : achat direct, contre des points de fidélité, de n'importe quel mob,
 * bloc, pouvoir ou pièce d'équipement (matériau brut, sans enchantement — les versions
 * enchantées restent exclusives à la roue). Sert surtout à VISUALISER tout ce qui existe et
 * à cibler précisément ce qu'on veut, mais reste volontairement bien moins rentable que la
 * roue (voir EconomieValeurs) : c'est un complément de confort, pas une meilleure façon de
 * jouer.
 */
public class ShopManager {

    private static final int SLOTS_PAR_PAGE = 45;
    private static final int ONGLET_MOBS = 0;
    private static final int ONGLET_BLOCS = 1;
    private static final int ONGLET_POUVOIRS = 2;
    private static final int ONGLET_EQUIPEMENT = 3;
    private static final String[] NOMS_ONGLET = {"Mobs", "Blocs", "Pouvoirs", "Équipement"};
    private static final Material[] ICONES_ONGLET = {
            Material.ZOMBIE_HEAD, Material.BRICKS, Material.BLAZE_ROD, Material.IRON_CHESTPLATE
    };

    private final LoyaltyMobsPlugin plugin;

    public ShopManager(LoyaltyMobsPlugin plugin) {
        this.plugin = plugin;
    }

    public void ouvrirMenu(Player player) {
        ouvrirMenu(player, ONGLET_MOBS, 0);
    }

    public void ouvrirMenu(Player player, int onglet, int page) {
        PlayerDataManager data = plugin.getPlayerDataManager();
        UUID uuid = player.getUniqueId();
        int ongletBorne = Math.max(0, Math.min(onglet, NOMS_ONGLET.length - 1));

        List<ItemStack> icones = switch (ongletBorne) {
            case ONGLET_MOBS -> construireIconesMobs(data, uuid);
            case ONGLET_BLOCS -> construireIconesBlocs(data, uuid);
            case ONGLET_POUVOIRS -> construireIconesPouvoirs(data, uuid);
            default -> construireIconesEquipement(data, uuid);
        };

        int nbPages = Math.max(1, (icones.size() - 1) / SLOTS_PAR_PAGE + 1);
        int pageBornee = Math.max(0, Math.min(page, nbPages - 1));

        ShopInventoryHolder holder = new ShopInventoryHolder();
        holder.setOnglet(ongletBorne);
        holder.setPage(pageBornee);
        String titre = "§6✦ Boutique — " + NOMS_ONGLET[ongletBorne] + " (" + (pageBornee + 1) + "/" + nbPages + ")";
        Inventory inv = Bukkit.createInventory(holder, 54, titre);
        holder.setInventory(inv);

        int debut = pageBornee * SLOTS_PAR_PAGE;
        int fin = Math.min(icones.size(), debut + SLOTS_PAR_PAGE);
        for (int i = debut; i < fin; i++) {
            inv.setItem(i - debut, icones.get(i));
        }

        ItemStack filler = filler();
        for (int i = SLOTS_PAR_PAGE; i < 54; i++) {
            inv.setItem(i, filler);
        }
        if (pageBornee > 0) {
            inv.setItem(45, nommer(Material.ARROW, "§e« Page précédente", List.of(), Cles.SHOP_PAGE_ACTION, "prev"));
        }
        if (pageBornee < nbPages - 1) {
            inv.setItem(53, nommer(Material.ARROW, "§ePage suivante »", List.of(), Cles.SHOP_PAGE_ACTION, "next"));
        }
        for (int i = 0; i < NOMS_ONGLET.length; i++) {
            ItemStack onglet1 = new ItemStack(ICONES_ONGLET[i]);
            ItemMeta meta = onglet1.getItemMeta();
            meta.setDisplayName((i == ongletBorne ? "§a▶ " : "§7") + NOMS_ONGLET[i]);
            meta.getPersistentDataContainer().set(Cles.SHOP_ONGLET_ACTION, PersistentDataType.INTEGER, i);
            onglet1.setItemMeta(meta);
            inv.setItem(47 + i, onglet1);
        }
        inv.setItem(51, nommer(Material.GOLD_INGOT, "§eTon solde : §f" + data.getPoints(uuid) + " points",
                List.of("§7Rappel : la boutique est faite pour", "§7CIBLER ce que tu veux, mais reste",
                        "§7moins rentable que la roue (/roue)."), null, null));

        player.openInventory(inv);
    }

    private List<ItemStack> construireIconesMobs(PlayerDataManager data, UUID uuid) {
        List<EntityType> tries = new ArrayList<>(MobRegistry.all().keySet());
        tries.sort(Comparator.comparing((EntityType t) -> MobRegistry.getRarete(t).ordinal()).reversed()
                .thenComparing(Enum::name));

        List<ItemStack> icones = new ArrayList<>();
        for (EntityType type : tries) {
            MobRarity rarete = MobRegistry.getRarete(type);
            int possedes = data.getNombreMob(uuid, type);
            Material iconeMat = Material.matchMaterial(type.name() + "_SPAWN_EGG");
            if (iconeMat == null) iconeMat = Material.ZOMBIE_HEAD;

            List<String> lore = new ArrayList<>();
            lore.add("§7Possédés : " + rarete.getCouleur() + possedes);
            icones.add(construireIcone(iconeMat, nomLisible(type.name()), rarete,
                    EconomieValeurs.prixBoutique(rarete), lore, "MOB", type.name(), false));
        }
        return icones;
    }

    private List<ItemStack> construireIconesBlocs(PlayerDataManager data, UUID uuid) {
        List<Material> possedes = new ArrayList<>();
        List<Map.Entry<Material, MobRarity>> tries = new ArrayList<>(BlockRegistry.getTousLesBlocs().entrySet());
        tries.sort(Comparator.<Map.Entry<Material, MobRarity>>comparingInt(e -> e.getValue().ordinal()).reversed()
                .thenComparing(e -> e.getKey().name()));

        List<ItemStack> icones = new ArrayList<>();
        for (Map.Entry<Material, MobRarity> entree : tries) {
            Material bloc = entree.getKey();
            MobRarity rarete = entree.getValue();
            boolean dejaPossede = data.getBlocsDebloques(uuid).contains(bloc);
            icones.add(construireIcone(bloc, nomLisible(bloc.name()), rarete,
                    EconomieValeurs.prixBoutique(rarete), List.of(), "BLOC", bloc.name(), dejaPossede));
        }
        return icones;
    }

    private List<ItemStack> construireIconesPouvoirs(PlayerDataManager data, UUID uuid) {
        List<PowerRegistry.PowerDefinition> tries = new ArrayList<>(PowerRegistry.getTous());
        tries.sort(Comparator.comparing((PowerRegistry.PowerDefinition p) -> p.rarete().ordinal()).reversed()
                .thenComparing(PowerRegistry.PowerDefinition::nom));

        List<ItemStack> icones = new ArrayList<>();
        for (PowerRegistry.PowerDefinition pouvoir : tries) {
            int possedes = data.getNombrePouvoir(uuid, pouvoir.id());
            List<String> lore = new ArrayList<>();
            lore.add("§7Charges possédées : " + pouvoir.rarete().getCouleur() + possedes);
            String effet = PowerRegistry.decrireEffet(pouvoir.id());
            if (effet != null) lore.add("§8" + effet);
            icones.add(construireIcone(pouvoir.icone(), pouvoir.nom(), pouvoir.rarete(),
                    EconomieValeurs.prixBoutique(pouvoir.rarete()), lore, "POUVOIR", pouvoir.id(), false));
        }
        return icones;
    }

    private List<ItemStack> construireIconesEquipement(PlayerDataManager data, UUID uuid) {
        java.util.Set<String> signaturesPossedees = new java.util.HashSet<>();
        for (ItemStack item : data.getEquipements(uuid)) {
            signaturesPossedees.add(GearRegistry.getSignature(item));
        }

        List<ItemStack> icones = new ArrayList<>();
        for (GearRegistry.TypeEquipement type : GearRegistry.TypeEquipement.values()) {
            for (int tier = 0; tier < MobRarity.values().length; tier++) {
                MobRarity rarete = MobRarity.values()[tier];
                ItemStack brut = GearRegistry.construireBrut(type, tier);
                boolean dejaPossede = signaturesPossedees.contains(GearRegistry.getSignature(brut))
                        || tier == 0; // le brut de base (bois/cuir) est de toute façon déjà toujours équipable

                ItemStack icone = brut.clone();
                ItemMeta meta = icone.getItemMeta();
                List<String> lore = new ArrayList<>();
                if (meta.hasLore()) lore.addAll(meta.getLore());
                remplirLoreAchat(lore, rarete, EconomieValeurs.prixBoutique(rarete), dejaPossede,
                        tier == 0 ? "§7Toujours disponible gratuitement" : null);
                meta.setLore(lore);
                meta.getPersistentDataContainer().set(Cles.SHOP_CATEGORIE, PersistentDataType.STRING, "GEAR");
                meta.getPersistentDataContainer().set(Cles.SHOP_ID, PersistentDataType.STRING, type.name() + ":" + tier);
                icone.setItemMeta(meta);
                icones.add(icone);
            }
        }
        return icones;
    }

    private ItemStack construireIcone(Material material, String nom, MobRarity rarete, int prix,
                                       List<String> loreSupplementaire, String categorie, String id, boolean dejaPossede) {
        ItemStack icone = new ItemStack(material);
        ItemMeta meta = icone.getItemMeta();
        meta.setDisplayName(rarete.getCouleur() + "§l" + nom);
        List<String> lore = new ArrayList<>(loreSupplementaire);
        remplirLoreAchat(lore, rarete, prix, dejaPossede, null);
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(Cles.SHOP_CATEGORIE, PersistentDataType.STRING, categorie);
        meta.getPersistentDataContainer().set(Cles.SHOP_ID, PersistentDataType.STRING, id);
        icone.setItemMeta(meta);
        return icone;
    }

    private void remplirLoreAchat(List<String> lore, MobRarity rarete, int prix, boolean dejaPossede, String noteExtra) {
        lore.add("§7Rareté : " + rarete.getCouleur() + rarete.getLabel());
        if (noteExtra != null) {
            lore.add(noteExtra);
        } else if (dejaPossede) {
            lore.add("§7Prix : §8" + prix + " points");
            lore.add("§8✔ Déjà possédé");
        } else {
            lore.add("§7Prix : §e" + prix + " points");
            lore.add("§eClique pour acheter !");
        }
    }

    /** Traite un achat depuis le menu. */
    public void acheter(Player player, String categorie, String id) {
        PlayerDataManager data = plugin.getPlayerDataManager();
        UUID uuid = player.getUniqueId();

        switch (categorie) {
            case "MOB" -> {
                EntityType type;
                try {
                    type = EntityType.valueOf(id);
                } catch (IllegalArgumentException e) {
                    return;
                }
                MobRarity rarete = MobRegistry.getRarete(type);
                int prix = EconomieValeurs.prixBoutique(rarete);
                if (!data.retirerPoints(uuid, prix)) {
                    player.sendMessage(messageManqueDePoints(prix, data.getPoints(uuid)));
                    return;
                }
                data.ajouterMob(uuid, type);
                data.save(uuid);
                player.sendMessage("§a✦ Acheté : " + rarete.getCouleur() + nomLisible(type.name()) + " §7(§c-" + prix + " points§7)");
            }
            case "BLOC" -> {
                Material bloc;
                try {
                    bloc = Material.valueOf(id);
                } catch (IllegalArgumentException e) {
                    return;
                }
                if (data.getBlocsDebloques(uuid).contains(bloc)) {
                    player.sendMessage("§7Tu possèdes déjà ce bloc.");
                    return;
                }
                MobRarity rarete = BlockRegistry.getRarete(bloc);
                int prix = EconomieValeurs.prixBoutique(rarete);
                if (!data.retirerPoints(uuid, prix)) {
                    player.sendMessage(messageManqueDePoints(prix, data.getPoints(uuid)));
                    return;
                }
                data.debloquerBloc(uuid, bloc);
                data.save(uuid);
                player.sendMessage("§a✦ Acheté : " + rarete.getCouleur() + nomLisible(bloc.name()) + " §7(§c-" + prix + " points§7)");
            }
            case "POUVOIR" -> {
                MobRarity rarete = PowerRegistry.getRarete(id);
                int prix = EconomieValeurs.prixBoutique(rarete);
                if (!data.retirerPoints(uuid, prix)) {
                    player.sendMessage(messageManqueDePoints(prix, data.getPoints(uuid)));
                    return;
                }
                data.ajouterPouvoir(uuid, id);
                data.save(uuid);
                player.sendMessage("§a✦ Acheté : " + rarete.getCouleur() + id + " §7(§c-" + prix + " points§7)");
            }
            case "GEAR" -> {
                String[] parts = id.split(":");
                if (parts.length != 2) return;
                GearRegistry.TypeEquipement type;
                int tier;
                try {
                    type = GearRegistry.TypeEquipement.valueOf(parts[0]);
                    tier = Integer.parseInt(parts[1]);
                } catch (IllegalArgumentException e) {
                    return;
                }
                if (tier == 0) {
                    player.sendMessage("§7L'objet de base est déjà disponible gratuitement depuis le menu d'équipement !");
                    return;
                }
                ItemStack brut = GearRegistry.construireBrut(type, tier);
                String signature = GearRegistry.getSignature(brut);
                boolean dejaPossede = data.getEquipements(uuid).stream()
                        .anyMatch(i -> GearRegistry.getSignature(i).equals(signature));
                if (dejaPossede) {
                    player.sendMessage("§7Tu possèdes déjà cette pièce.");
                    return;
                }
                MobRarity rarete = MobRarity.values()[tier];
                int prix = EconomieValeurs.prixBoutique(rarete);
                if (!data.retirerPoints(uuid, prix)) {
                    player.sendMessage(messageManqueDePoints(prix, data.getPoints(uuid)));
                    return;
                }
                data.ajouterEquipement(uuid, brut);
                data.save(uuid);
                player.sendMessage("§a✦ Acheté : " + rarete.getCouleur() + brut.getItemMeta().getDisplayName()
                        + " §7(§c-" + prix + " points§7)");
            }
            default -> {
            }
        }
    }

    private String messageManqueDePoints(int prix, int solde) {
        return "§cIl te faut §e" + prix + " points §c(tu as §e" + solde + "§c).";
    }

    private ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack nommer(Material material, String nom, List<String> lore,
                              org.bukkit.NamespacedKey cle, String valeur) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(nom);
        if (!lore.isEmpty()) meta.setLore(lore);
        if (cle != null) meta.getPersistentDataContainer().set(cle, PersistentDataType.STRING, valeur);
        item.setItemMeta(meta);
        return item;
    }

    private String nomLisible(String nomBrut) {
        String s = nomBrut.toLowerCase().replace('_', ' ');
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
