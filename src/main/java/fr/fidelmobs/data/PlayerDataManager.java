package fr.fidelmobs.data;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
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
}
