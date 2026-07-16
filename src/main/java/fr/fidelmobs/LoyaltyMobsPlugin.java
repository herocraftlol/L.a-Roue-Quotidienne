package fr.fidelmobs;

import fr.fidelmobs.commands.ArmeeCommand;
import fr.fidelmobs.commands.InvoquerCommand;
import fr.fidelmobs.commands.RoueCommand;
import fr.fidelmobs.commands.StreakCommand;
import fr.fidelmobs.data.PlayerDataManager;
import fr.fidelmobs.listeners.AllyListener;
import fr.fidelmobs.listeners.LoginListener;
import org.bukkit.plugin.java.JavaPlugin;

public class LoyaltyMobsPlugin extends JavaPlugin {

    private PlayerDataManager playerDataManager;
    private AllyListener allyListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.playerDataManager = new PlayerDataManager(this);
        this.allyListener = new AllyListener(this);

        getServer().getPluginManager().registerEvents(new LoginListener(this), this);
        getServer().getPluginManager().registerEvents(allyListener, this);

        getCommand("streak").setExecutor(new StreakCommand(this));
        getCommand("roue").setExecutor(new RoueCommand(this));
        getCommand("armee").setExecutor(new ArmeeCommand(this));
        InvoquerCommand invoquerCommand = new InvoquerCommand(this, allyListener);
        getCommand("invoquer").setExecutor(invoquerCommand);
        getCommand("invoquer").setTabCompleter(invoquerCommand);

        getLogger().info("LoyaltyMobs activé.");
    }

    @Override
    public void onDisable() {
        if (playerDataManager != null) {
            // rien de spécifique à vider, les sauvegardes se font au fil de l'eau
        }
        if (allyListener != null) {
            allyListener.nettoyerToutesLesAlliees();
        }
        getLogger().info("LoyaltyMobs désactivé.");
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public AllyListener getAllyListener() {
        return allyListener;
    }
}
