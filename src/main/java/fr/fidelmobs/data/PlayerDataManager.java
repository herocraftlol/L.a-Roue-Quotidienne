package fr.fidelmobs.data;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class PlayerDataManager {

    private final JavaPlugin plugin;
    private final File dossier;
    private final Map<UUID, YamlConfiguration> cache = new HashMap<>();

    public PlayerDataManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dossier = new File(plugin.getDataFolder(), "playerdata");
        if (!dossier.exists()) {
            dossier.mkdirs();
        }
    }

    private File fichier(UUID uuid) {
        return new File(dossier, uuid.toString() + ".yml");
    }

    public YamlConfiguration get(UUID uuid) {
        return cache.computeIfAbsent(uuid, id -> {
            File f = fichier(id);
            YamlConfiguration conf = YamlConfiguration.loadConfiguration(f);
            return conf;
        });
    }

    public void save(UUID uuid) {
        YamlConfiguration conf = cache.get(uuid);
        if (conf == null) return;
        try {
            conf.save(fichier(uuid));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Impossible de sauvegarder les données de " + uuid, e);
        }
    }

    // ---- Série de connexions ----

    public int getStreak(UUID uuid) {
        return get(uuid).getInt("streak", 0);
    }

    public void setStreak(UUID uuid, int valeur) {
        get(uuid).set("streak", valeur);
    }

    public LocalDate getLastLogin(UUID uuid) {
        String s = get(uuid).getString("lastLogin");
        if (s == null) return null;
        return LocalDate.parse(s);
    }

    public void setLastLogin(UUID uuid, LocalDate date) {
        get(uuid).set("lastLogin", date.toString());
    }

    // ---- Tickets ----

    public int getTickets(UUID uuid) {
        return get(uuid).getInt("tickets", 0);
    }

    public void addTickets(UUID uuid, int montant) {
        get(uuid).set("tickets", getTickets(uuid) + montant);
    }

    /**
     * Retire jusqu'à {@code montant} tickets au joueur (utilisé par la commande admin).
     * Ne descend jamais sous 0 : si le joueur en possède moins que le montant demandé,
     * seuls les tickets réellement possédés sont retirés. Retourne le nombre réellement
     * retiré, pour que la commande puisse en informer précisément l'administrateur.
     */
    public int retirerTickets(UUID uuid, int montant) {
        int solde = getTickets(uuid);
        int retires = Math.min(solde, Math.max(0, montant));
        get(uuid).set("tickets", solde - retires);
        return retires;
    }

    public boolean consommerTicket(UUID uuid) {
        int t = getTickets(uuid);
        if (t <= 0) return false;
        get(uuid).set("tickets", t - 1);
        return true;
    }

    // ---- Collection de mobs ----

    private String cheminMob(EntityType type) {
        return "mobs." + type.name();
    }

    public int getNombreMob(UUID uuid, EntityType type) {
        return get(uuid).getInt(cheminMob(type), 0);
    }

    public void ajouterMob(UUID uuid, EntityType type) {
        get(uuid).set(cheminMob(type), getNombreMob(uuid, type) + 1);
    }

    public boolean retirerMob(UUID uuid, EntityType type) {
        int n = getNombreMob(uuid, type);
        if (n <= 0) return false;
        get(uuid).set(cheminMob(type), n - 1);
        return true;
    }

    public Map<EntityType, Integer> getCollection(UUID uuid) {
        Map<EntityType, Integer> resultat = new HashMap<>();
        YamlConfiguration conf = get(uuid);
        if (conf.contains("mobs")) {
            for (String cle : conf.getConfigurationSection("mobs").getKeys(false)) {
                int n = conf.getInt("mobs." + cle, 0);
                if (n > 0) {
                    try {
                        resultat.put(EntityType.valueOf(cle), n);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }
        return resultat;
    }

    // ---- Blocs de construction débloqués (arène PvP) ----

    public Set<Material> getBlocsDebloques(UUID uuid) {
        List<String> noms = get(uuid).getStringList("blocs_debloques");
        Set<Material> resultat = new HashSet<>();
        for (String n : noms) {
            try {
                resultat.add(Material.valueOf(n));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return resultat;
    }

    public void debloquerBloc(UUID uuid, Material material) {
        Set<Material> blocs = getBlocsDebloques(uuid);
        blocs.add(material);
        List<String> noms = new ArrayList<>();
        for (Material m : blocs) noms.add(m.name());
        get(uuid).set("blocs_debloques", noms);
    }

    public Material getBlocActif(UUID uuid) {
        String s = get(uuid).getString("bloc_actif");
        if (s != null) {
            try {
                return Material.valueOf(s);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    public void setBlocActif(UUID uuid, Material material) {
        get(uuid).set("bloc_actif", material.name());
    }

    // ---- Équipement PvP (collection d'objets obtenus à la roue) ----

    @SuppressWarnings("unchecked")
    public List<ItemStack> getEquipements(UUID uuid) {
        List<?> brut = get(uuid).getList("equipements");
        List<ItemStack> resultat = new ArrayList<>();
        if (brut != null) {
            for (Object o : brut) {
                if (o instanceof ItemStack is) {
                    resultat.add(is);
                }
            }
        }
        return resultat;
    }

    public int ajouterEquipement(UUID uuid, ItemStack item) {
        List<ItemStack> liste = getEquipements(uuid);
        liste.add(item);
        get(uuid).set("equipements", liste);
        return liste.size() - 1;
    }

    private String cheminEquipe(EquipmentSlot slot) {
        return "equipe." + slot.name();
    }

    public int getIndexEquipe(UUID uuid, EquipmentSlot slot) {
        return get(uuid).getInt(cheminEquipe(slot), -1);
    }

    public void setIndexEquipe(UUID uuid, EquipmentSlot slot, int index) {
        get(uuid).set(cheminEquipe(slot), index);
    }

    // ---- Flèches à effet (collection obtenue à la roue, tirables avec l'arc du kit) ----

    @SuppressWarnings("unchecked")
    public List<ItemStack> getFleches(UUID uuid) {
        List<?> brut = get(uuid).getList("fleches");
        List<ItemStack> resultat = new ArrayList<>();
        if (brut != null) {
            for (Object o : brut) {
                if (o instanceof ItemStack is) {
                    resultat.add(is);
                }
            }
        }
        return resultat;
    }

    public int ajouterFleche(UUID uuid, ItemStack item) {
        List<ItemStack> liste = getFleches(uuid);
        liste.add(item);
        get(uuid).set("fleches", liste);
        return liste.size() - 1;
    }

    public int getIndexFlecheEquipee(UUID uuid) {
        return get(uuid).getInt("fleche_equipee", -1);
    }

    public void setIndexFlecheEquipee(UUID uuid, int index) {
        get(uuid).set("fleche_equipee", index);
    }

    // ---- Pouvoirs spéciaux (collection obtenue à la roue, catégorie "Pouvoir") ----
    // Système de charges par pouvoir (comme les mobs invoqués) : si un pouvoir est obtenu
    // plusieurs fois, il peut être réutilisé autant de fois que de copies possédées avant
    // de devoir attendre une recharge. Chaque pouvoir a son propre temps de recharge,
    // totalement indépendant des autres pouvoirs équipés/possédés.

    private String cheminPouvoir(String id) {
        return "pouvoirs_possedes." + id;
    }

    public int getNombrePouvoir(UUID uuid, String id) {
        return get(uuid).getInt(cheminPouvoir(id), 0);
    }

    public void ajouterPouvoir(UUID uuid, String id) {
        get(uuid).set(cheminPouvoir(id), getNombrePouvoir(uuid, id) + 1);
    }

    /** Tous les pouvoirs possédés (id -> nombre de copies), les copies à 0 sont exclues. */
    public Map<String, Integer> getPouvoirsPossedes(UUID uuid) {
        Map<String, Integer> resultat = new HashMap<>();
        YamlConfiguration conf = get(uuid);
        if (conf.contains("pouvoirs_possedes")) {
            for (String cle : conf.getConfigurationSection("pouvoirs_possedes").getKeys(false)) {
                int n = conf.getInt("pouvoirs_possedes." + cle, 0);
                if (n > 0) {
                    resultat.put(cle, n);
                }
            }
        }
        return resultat;
    }

    public String getPouvoirEquipe(UUID uuid) {
        return get(uuid).getString("pouvoir_equipe");
    }

    public void setPouvoirEquipe(UUID uuid, String id) {
        get(uuid).set("pouvoir_equipe", id);
    }

    // ---- Cooldowns de pouvoirs (par pouvoir, sur le même principe que les cooldowns
    // d'invocation : un pouvoir n'est jamais perdu, mais chaque copie possédée ne peut être
    // réutilisée qu'après son propre temps de recharge) ----

    private String cheminCooldownPouvoir(String id) {
        return "pouvoir_cooldowns." + id;
    }

    public List<Long> getCooldownsActifsPouvoir(UUID uuid, String id) {
        List<Long> bruts = get(uuid).getLongList(cheminCooldownPouvoir(id));
        long maintenant = System.currentTimeMillis();
        List<Long> actifs = new ArrayList<>();
        for (long t : bruts) {
            if (t > maintenant) actifs.add(t);
        }
        if (actifs.size() != bruts.size()) {
            get(uuid).set(cheminCooldownPouvoir(id), actifs);
        }
        return actifs;
    }

    /** Nombre de copies de ce pouvoir actuellement disponibles à l'utilisation. */
    public int getUnitesDisponiblesPouvoir(UUID uuid, String id) {
        int possedees = getNombrePouvoir(uuid, id);
        int enRecharge = getCooldownsActifsPouvoir(uuid, id).size();
        return Math.max(0, possedees - enRecharge);
    }

    /** Timestamp (epoch millis) auquel la prochaine copie redeviendra disponible, ou -1. */
    public long getProchaineDisponibilitePouvoir(UUID uuid, String id) {
        long minimum = -1;
        for (long t : getCooldownsActifsPouvoir(uuid, id)) {
            if (minimum == -1 || t < minimum) minimum = t;
        }
        return minimum;
    }

    /** Consomme une charge de ce pouvoir : part en recharge sans jamais être retiré de la collection. */
    public void utiliserUnitePouvoir(UUID uuid, String id, long dureeCooldownMs) {
        List<Long> actuels = new ArrayList<>(getCooldownsActifsPouvoir(uuid, id));
        actuels.add(System.currentTimeMillis() + dureeCooldownMs);
        get(uuid).set(cheminCooldownPouvoir(id), actuels);
    }

    // ---- Statistiques PvP (kills / morts, persistantes pour le classement) ----

    public int getKills(UUID uuid) {
        return get(uuid).getInt("kills", 0);
    }

    public void ajouterKill(UUID uuid) {
        get(uuid).set("kills", getKills(uuid) + 1);
    }

    public int getMorts(UUID uuid) {
        return get(uuid).getInt("morts", 0);
    }

    public void ajouterMort(UUID uuid) {
        get(uuid).set("morts", getMorts(uuid) + 1);
    }

    /**
     * Ratio K/D. Sans mort enregistrée, on renvoie directement le nombre de kills
     * (convention habituelle) plutôt qu'une division par zéro.
     */
    public double getRatioKD(UUID uuid) {
        int kills = getKills(uuid);
        int morts = getMorts(uuid);
        return morts == 0 ? kills : (double) kills / morts;
    }

    /**
     * Liste tous les joueurs ayant déjà des données sauvegardées (utilisé pour construire
     * les classements du hologramme, y compris pour les joueurs hors ligne).
     */
    public List<UUID> getToutesLesUUID() {
        List<UUID> resultat = new ArrayList<>();
        File[] fichiers = dossier.listFiles((dir, nom) -> nom.endsWith(".yml"));
        if (fichiers != null) {
            for (File f : fichiers) {
                String nom = f.getName().substring(0, f.getName().length() - 4);
                try {
                    resultat.add(UUID.fromString(nom));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return resultat;
    }

    // ---- Cooldowns d'invocation (collection permanente : un mob n'est plus jamais perdu,
    // mais chaque unité possédée ne peut être invoquée qu'une fois par heure) ----

    private String cheminCooldown(EntityType type) {
        return "invocation_cooldowns." + type.name();
    }

    /**
     * Timestamps (epoch millis) auxquels chaque unité actuellement "en recharge" redevient
     * disponible. Purge automatiquement les entrées expirées avant de les retourner.
     */
    public List<Long> getCooldownsActifs(UUID uuid, EntityType type) {
        List<Long> bruts = get(uuid).getLongList(cheminCooldown(type));
        long maintenant = System.currentTimeMillis();
        List<Long> actifs = new ArrayList<>();
        for (long t : bruts) {
            if (t > maintenant) actifs.add(t);
        }
        if (actifs.size() != bruts.size()) {
            get(uuid).set(cheminCooldown(type), actifs);
        }
        return actifs;
    }

    /**
     * Nombre d'unités de ce mob actuellement disponibles à l'invocation (possédées moins
     * celles encore en recharge).
     */
    public int getUnitesDisponibles(UUID uuid, EntityType type) {
        int possedees = getNombreMob(uuid, type);
        int enRecharge = getCooldownsActifs(uuid, type).size();
        return Math.max(0, possedees - enRecharge);
    }

    /**
     * Timestamp (epoch millis) auquel la prochaine unité redeviendra disponible,
     * ou -1 si aucune n'est actuellement en recharge.
     */
    public long getProchaineDisponibilite(UUID uuid, EntityType type) {
        long minimum = -1;
        for (long t : getCooldownsActifs(uuid, type)) {
            if (minimum == -1 || t < minimum) minimum = t;
        }
        return minimum;
    }

    /**
     * Marque une unité comme utilisée : elle repart pour un temps de recharge avant de
     * redevenir disponible. Ne retire JAMAIS le mob de la collection (système permanent).
     */
    public void utiliserUniteMob(UUID uuid, EntityType type, long dureeCooldownMs) {
        List<Long> actuels = new ArrayList<>(getCooldownsActifs(uuid, type));
        actuels.add(System.currentTimeMillis() + dureeCooldownMs);
        get(uuid).set(cheminCooldown(type), actuels);
    }

    // ---- Points de fidélité PvP (gagnés à chaque kill, échangeables contre des tickets) ----

    public int getPoints(UUID uuid) {
        return get(uuid).getInt("points", 0);
    }

    public void ajouterPoints(UUID uuid, int montant) {
        get(uuid).set("points", getPoints(uuid) + montant);
        // Compteurs dédiés au système de défis : contrairement au solde de points (qui
        // peut être dépensé), ces compteurs ne font qu'augmenter, donc utilisables comme
        // objectif de progression fiable ("gagner X points au total").
        if (montant > 0) {
            incrementerCompteur(uuid, "points_gagnes_total", montant);
            incrementerCompteurQuotidien(uuid, "points_gagnes", montant);
        }
    }

    /**
     * Dépense des points si le solde est suffisant. Ne modifie rien et renvoie false sinon.
     */
    public boolean retirerPoints(UUID uuid, int montant) {
        int solde = getPoints(uuid);
        if (solde < montant) return false;
        get(uuid).set("points", solde - montant);
        return true;
    }

    // ---- Compteurs génériques (système de défis) ----
    // Sert de base à la plupart des défis qui ne correspondent à aucune statistique déjà
    // suivie ailleurs (nombre de tirages à la roue, d'invocations, de flèches tirées...).

    public int getCompteur(UUID uuid, String cle) {
        return get(uuid).getInt("compteurs." + cle, 0);
    }

    public void incrementerCompteur(UUID uuid, String cle, int delta) {
        get(uuid).set("compteurs." + cle, getCompteur(uuid, cle) + delta);
    }

    public void definirCompteur(UUID uuid, String cle, int valeur) {
        get(uuid).set("compteurs." + cle, valeur);
    }

    /** Ne retient que la plus grande valeur jamais atteinte (ex. meilleure série de kills). */
    public void enregistrerRecord(UUID uuid, String cle, int valeur) {
        if (valeur > getCompteur(uuid, cle)) {
            get(uuid).set("compteurs." + cle, valeur);
        }
    }

    // ---- Défis globaux (progression permanente, ne se réinitialise jamais) ----

    public boolean estDefiComplete(UUID uuid, String id) {
        return get(uuid).getBoolean("defis_completes." + id, false);
    }

    public void marquerDefiComplete(UUID uuid, String id) {
        get(uuid).set("defis_completes." + id, true);
    }

    // ---- Défis quotidiens (compteurs et complétions remis à zéro chaque jour) ----
    // Le pool de défis proposés est le même pour tout le monde un jour donné (tiré au sort
    // de façon déterministe à partir de la date, voir DefiRegistry#defisQuotidiensDuJour) :
    // seule la PROGRESSION de chaque joueur est individuelle et stockée ici. La réinitialisation
    // se fait paresseusement : dès qu'on constate que la date stockée n'est plus celle
    // d'aujourd'hui, compteurs et complétions quotidiens sont effacés avant de continuer.

    private void assurerJourQuotidienAJour(UUID uuid) {
        String aujourdHui = LocalDate.now().toString();
        String dernier = get(uuid).getString("defis_quotidiens.date");
        if (!aujourdHui.equals(dernier)) {
            get(uuid).set("defis_quotidiens.date", aujourdHui);
            get(uuid).set("defis_quotidiens.compteurs", null);
            get(uuid).set("defis_quotidiens.completes", null);
        }
    }

    public int getCompteurQuotidien(UUID uuid, String cle) {
        assurerJourQuotidienAJour(uuid);
        return get(uuid).getInt("defis_quotidiens.compteurs." + cle, 0);
    }

    public void incrementerCompteurQuotidien(UUID uuid, String cle, int delta) {
        assurerJourQuotidienAJour(uuid);
        get(uuid).set("defis_quotidiens.compteurs." + cle, getCompteurQuotidien(uuid, cle) + delta);
    }

    public boolean estDefiQuotidienComplete(UUID uuid, String id) {
        assurerJourQuotidienAJour(uuid);
        return get(uuid).getBoolean("defis_quotidiens.completes." + id, false);
    }

    public void marquerDefiQuotidienComplete(UUID uuid, String id) {
        assurerJourQuotidienAJour(uuid);
        get(uuid).set("defis_quotidiens.completes." + id, true);
    }
}
