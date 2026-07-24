package fr.fidelmobs.data;

import fr.fidelmobs.database.DatabaseManager;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Stocke/charge les données joueur (tickets, collection, points...). Deux modes, choisis
 * au démarrage selon {@code multi-serveur.enabled} dans le config :
 * <ul>
 *     <li><b>Local (par défaut)</b> : un fichier YAML par joueur dans {@code playerdata/},
 *     propre à ce serveur — comportement historique, aucun MySQL requis.</li>
 *     <li><b>Partagé (multi-serveur)</b> : le même YAML (identique en contenu) est stocké
 *     dans une table MySQL commune à tous les serveurs Paper de la structure Velocity, chargé
 *     à la connexion du joueur (voir {@link fr.fidelmobs.listeners.LoginListener}) et
 *     sauvegardé à sa déconnexion — un joueur retrouve exactement les mêmes tickets, la même
 *     collection, etc. quel que soit le serveur backend sur lequel il se connecte.</li>
 * </ul>
 * Dans les deux cas, l'API publique de cette classe (getTickets, ajouterMob...) est identique :
 * aucun autre fichier du plugin n'a besoin de savoir quel mode est actif.
 */
public class PlayerDataManager {

    private final JavaPlugin plugin;
    private final File dossier;
    private final DatabaseManager databaseManager;
    private final boolean modePartage;
    private final Map<UUID, YamlConfiguration> cache = new HashMap<>();

    public PlayerDataManager(JavaPlugin plugin) {
        this(plugin, null);
    }

    /**
     * @param databaseManager non-null pour activer le mode partagé (multi-serveur.enabled: true
     *                        et connexion MySQL établie avec succès) ; {@code null} pour rester
     *                        en mode local (comportement historique, fichiers YAML).
     */
    public PlayerDataManager(JavaPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.dossier = new File(plugin.getDataFolder(), "playerdata");
        if (!dossier.exists()) {
            dossier.mkdirs();
        }
        this.databaseManager = databaseManager;
        this.modePartage = databaseManager != null;
    }

    private File fichier(UUID uuid) {
        return new File(dossier, uuid.toString() + ".yml");
    }

    public YamlConfiguration get(UUID uuid) {
        return cache.computeIfAbsent(uuid, id -> {
            // En mode partagé, la donnée devrait déjà avoir été préchargée en cache par
            // chargerDepuisBase() lors de AsyncPlayerPreLoginEvent. Ce repli synchrone ne sert
            // que pour les cas non couverts par ce hook (ex: commande console sur un joueur
            // jamais réellement connu par ce process) et évite de renvoyer null.
            if (modePartage) {
                try {
                    String yaml = databaseManager.chargerDonneesJoueur(id);
                    YamlConfiguration conf = new YamlConfiguration();
                    if (yaml != null && !yaml.isBlank()) {
                        conf.loadFromString(yaml);
                    }
                    return conf;
                } catch (SQLException | InvalidConfigurationException e) {
                    plugin.getLogger().log(Level.WARNING,
                            "Impossible de charger les données MySQL de " + id + ", données vides utilisées.", e);
                    return new YamlConfiguration();
                }
            }
            File f = fichier(id);
            return YamlConfiguration.loadConfiguration(f);
        });
    }

    /**
     * Précharge en cache les données d'un joueur depuis MySQL (mode partagé uniquement).
     * À appeler depuis un contexte déjà asynchrone (AsyncPlayerPreLoginEvent) pour que
     * {@link #get(UUID)} n'ait jamais besoin de bloquer le thread principal ensuite.
     * Ne fait rien en mode local.
     */
    public void chargerDepuisBase(UUID uuid) {
        if (!modePartage) return;
        try {
            String yaml = databaseManager.chargerDonneesJoueur(uuid);
            YamlConfiguration conf = new YamlConfiguration();
            if (yaml != null && !yaml.isBlank()) {
                conf.loadFromString(yaml);
            }
            synchronized (cache) {
                cache.put(uuid, conf);
            }
        } catch (SQLException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Impossible de charger les données MySQL de " + uuid + " à la connexion.", e);
        }
    }

    /**
     * Retire un joueur du cache mémoire (mode partagé uniquement, à appeler après
     * sauvegarde à la déconnexion) pour forcer un rechargement à jour s'il se reconnecte
     * sur un autre serveur de la structure.
     */
    public void decharger(UUID uuid) {
        if (!modePartage) return;
        synchronized (cache) {
            cache.remove(uuid);
        }
    }

    public boolean isModePartage() {
        return modePartage;
    }

    public void save(UUID uuid) {
        YamlConfiguration conf = cache.get(uuid);
        if (conf == null) return;
        if (modePartage) {
            try {
                databaseManager.sauvegarderDonneesJoueur(uuid, conf.saveToString());
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Impossible de sauvegarder en MySQL les données de " + uuid, e);
            }
            return;
        }
        try {
            conf.save(fichier(uuid));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Impossible de sauvegarder les données de " + uuid, e);
        }
    }

    /**
     * Alias de {@link #save(UUID)}, à utiliser explicitement depuis un contexte déjà
     * asynchrone (ex: juste avant {@link #decharger(UUID)} à la déconnexion) — même
     * comportement, seulement pour la lisibilité des appelants.
     */
    public void sauvegarderAsync(UUID uuid) {
        save(uuid);
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

    public List<String> getPouvoirs(UUID uuid) {
        return new ArrayList<>(get(uuid).getStringList("pouvoirs"));
    }

    public int ajouterPouvoir(UUID uuid, String id) {
        List<String> liste = getPouvoirs(uuid);
        liste.add(id);
        get(uuid).set("pouvoirs", liste);
        return liste.size() - 1;
    }

    public int getIndexPouvoirEquipe(UUID uuid) {
        return get(uuid).getInt("pouvoir_equipe", -1);
    }

    public void setIndexPouvoirEquipe(UUID uuid, int index) {
        get(uuid).set("pouvoir_equipe", index);
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
        if (modePartage) {
            try {
                return databaseManager.listerUuidConnues();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Impossible de lister les joueurs connus en MySQL.", e);
                return new ArrayList<>();
            }
        }
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
}
