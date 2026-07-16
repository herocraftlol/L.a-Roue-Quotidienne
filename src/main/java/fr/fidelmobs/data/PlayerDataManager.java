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
}
