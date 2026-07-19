package fr.fidelmobs;

import fr.fidelmobs.arena.ArenaManager;
import fr.fidelmobs.commands.AcheterTicketCommand;
import fr.fidelmobs.commands.ArenePvpCommand;
import fr.fidelmobs.commands.ArmeeCommand;
import fr.fidelmobs.commands.BlocCommand;
import fr.fidelmobs.commands.ClassementCommand;
import fr.fidelmobs.commands.EquipementCommand;
import fr.fidelmobs.commands.InvoquerCommand;
import fr.fidelmobs.commands.PointsCommand;
import fr.fidelmobs.commands.RoueCommand;
import fr.fidelmobs.commands.StreakCommand;
import fr.fidelmobs.data.PlayerDataManager;
import fr.fidelmobs.database.DatabaseManager;
import fr.fidelmobs.database.TicketSyncTask;
import fr.fidelmobs.listeners.AllyListener;
import fr.fidelmobs.listeners.ArenaProtectionListener;
import fr.fidelmobs.listeners.LoginListener;
import fr.fidelmobs.managers.ArenaScoreboardManager;
import fr.fidelmobs.managers.BlockSelectorManager;
import fr.fidelmobs.managers.BuildBlockManager;
import fr.fidelmobs.managers.HologramManager;
import fr.fidelmobs.managers.InvocationManager;
import fr.fidelmobs.managers.KitManager;
import org.bukkit.plugin.java.JavaPlugin;

public class LoyaltyMobsPlugin extends JavaPlugin {

    private PlayerDataManager playerDataManager;
    private AllyListener allyListener;
    private ArenaManager arenaManager;
    private KitManager kitManager;
    private BuildBlockManager buildBlockManager;
    private ArenaScoreboardManager scoreboardManager;
    private ArenaProtectionListener arenaProtectionListener;
    private HologramManager hologramManager;
    private InvocationManager invocationManager;
    private BlockSelectorManager blockSelectorManager;
    private DatabaseManager databaseManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.playerDataManager = new PlayerDataManager(this);
        this.allyListener = new AllyListener(this);
        this.arenaManager = new ArenaManager(this);
        this.kitManager = new KitManager(this);
        this.buildBlockManager = new BuildBlockManager(this);
        this.scoreboardManager = new ArenaScoreboardManager(this);
        this.arenaProtectionListener = new ArenaProtectionListener(this);
        this.hologramManager = new HologramManager(this);
        this.invocationManager = new InvocationManager(this);
        this.blockSelectorManager = new BlockSelectorManager(this);

        getServer().getPluginManager().registerEvents(new LoginListener(this), this);
        getServer().getPluginManager().registerEvents(allyListener, this);
        getServer().getPluginManager().registerEvents(arenaProtectionListener, this);

        getCommand("streak").setExecutor(new StreakCommand(this));
        getCommand("roue").setExecutor(new RoueCommand(this));
        getCommand("armee").setExecutor(new ArmeeCommand(this));
        getCommand("arenepvp").setExecutor(new ArenePvpCommand(this));
        getCommand("equipement").setExecutor(new EquipementCommand(this));
        getCommand("classement").setExecutor(new ClassementCommand(this));
        getCommand("points").setExecutor(new PointsCommand(this));
        getCommand("acheterticket").setExecutor(new AcheterTicketCommand(this));

        InvoquerCommand invoquerCommand = new InvoquerCommand(this);
        getCommand("invoquer").setExecutor(invoquerCommand);
        getCommand("invoquer").setTabCompleter(invoquerCommand);

        BlocCommand blocCommand = new BlocCommand(this);
        getCommand("bloc").setExecutor(blocCommand);
        getCommand("bloc").setTabCompleter(blocCommand);

        // Boutique en argent réel (achat de tickets sur le site web) : entièrement optionnelle,
        // le reste du plugin fonctionne sans MySQL. Ne s'active que si explicitement demandé.
        if (getConfig().getBoolean("boutique.enabled", false)) {
            this.databaseManager = new DatabaseManager(this);
            try {
                databaseManager.connect();
                long intervalle = Math.max(5, getConfig().getInt("boutique.sync-interval-seconds", 15)) * 20L;
                getServer().getScheduler().runTaskTimerAsynchronously(this,
                        new TicketSyncTask(this, databaseManager), intervalle, intervalle);
                getLogger().info("Boutique de tickets (MySQL) activée.");
            } catch (Exception e) {
                getLogger().severe("Échec de connexion MySQL pour la boutique : " + e.getMessage()
                        + " — la boutique en argent réel est désactivée pour cette session.");
                databaseManager = null;
            }
        }

        getLogger().info("LoyaltyMobs activé.");
    }

    @Override
    public void onDisable() {
        if (allyListener != null) {
            allyListener.nettoyerToutesLesAlliees();
        }
        if (buildBlockManager != null) {
            buildBlockManager.arreterTout();
        }
        if (hologramManager != null) {
            hologramManager.retirer();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("LoyaltyMobs désactivé.");
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public AllyListener getAllyListener() {
        return allyListener;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public KitManager getKitManager() {
        return kitManager;
    }

    public BuildBlockManager getBuildBlockManager() {
        return buildBlockManager;
    }

    public ArenaScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public ArenaProtectionListener getArenaProtectionListener() {
        return arenaProtectionListener;
    }

    public HologramManager getHologramManager() {
        return hologramManager;
    }

    public InvocationManager getInvocationManager() {
        return invocationManager;
    }

    public BlockSelectorManager getBlockSelectorManager() {
        return blockSelectorManager;
    }
}
