package fr.fidelmobs.arena;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;

public class ArenaManager {

    private final JavaPlugin plugin;

    private World monde;
    private int x1, y1, z1;
    private int x2, y2, z2;
    private boolean configuree = false;

    public ArenaManager(JavaPlugin plugin) {
        this.plugin = plugin;
        charger();
    }

    private void charger() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("arene");
        if (section == null) return;

        String mondeNom = section.getString("monde");
        if (mondeNom == null || !section.contains("coin1") || !section.contains("coin2")) {
            configuree = false;
            return;
        }

        World w = plugin.getServer().getWorld(mondeNom);
        if (w == null) {
            configuree = false;
            return;
        }

        monde = w;
        x1 = section.getInt("coin1.x");
        y1 = section.getInt("coin1.y");
        z1 = section.getInt("coin1.z");
        x2 = section.getInt("coin2.x");
        y2 = section.getInt("coin2.y");
        z2 = section.getInt("coin2.z");
        configuree = true;
    }

    private void sauvegarder() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("arene");
        if (section == null) {
            section = plugin.getConfig().createSection("arene");
        }
        section.set("monde", monde.getName());
        section.set("coin1.x", x1);
        section.set("coin1.y", y1);
        section.set("coin1.z", z1);
        section.set("coin2.x", x2);
        section.set("coin2.y", y2);
        section.set("coin2.z", z2);
        plugin.saveConfig();
    }

    /**
     * Renvoie le bloc regardé par le joueur (portée 100 blocs), ou null si aucun.
     */
    private Block blocRegarde(Player player) {
        RayTraceResult resultat = player.rayTraceBlocks(100);
        if (resultat == null || resultat.getHitBlock() == null) {
            return null;
        }
        return resultat.getHitBlock();
    }

    public boolean definirPos1DepuisRegard(Player player) {
        Block bloc = blocRegarde(player);
        if (bloc == null) return false;
        monde = bloc.getWorld();
        x1 = bloc.getX();
        y1 = bloc.getY();
        z1 = bloc.getZ();
        if (monde.equals(coherentMonde())) {
            configuree = true;
            sauvegarder();
        }
        return true;
    }

    public boolean definirPos2DepuisRegard(Player player) {
        Block bloc = blocRegarde(player);
        if (bloc == null) return false;
        World mondePos1 = monde;
        x2 = bloc.getX();
        y2 = bloc.getY();
        z2 = bloc.getZ();
        monde = bloc.getWorld();
        if (mondePos1 != null && !mondePos1.equals(monde)) {
            // les deux coins doivent être dans le même monde : on garde le nouveau monde pour les deux
        }
        configuree = true;
        sauvegarder();
        return true;
    }

    private World coherentMonde() {
        return monde;
    }

    public boolean estConfiguree() {
        return configuree;
    }

    public boolean estDansArene(Location loc) {
        if (!configuree || loc.getWorld() == null || !loc.getWorld().equals(monde)) {
            return false;
        }
        double x = loc.getX(), y = loc.getY(), z = loc.getZ();
        return x >= Math.min(x1, x2) && x <= Math.max(x1, x2) + 1
                && y >= Math.min(y1, y2) && y <= Math.max(y1, y2) + 1
                && z >= Math.min(z1, z2) && z <= Math.max(z1, z2) + 1;
    }

    public World getMonde() {
        return monde;
    }

    public String describeCoins() {
        if (!configuree) return "Arène non configurée.";
        return "Coin 1 : (" + x1 + ", " + y1 + ", " + z1 + ") — Coin 2 : (" + x2 + ", " + y2 + ", " + z2 + ") — Monde : " + monde.getName();
    }
}
