package fr.fidelmobs;

import fr.fidelmobs.arena.ArenaManager;
import fr.fidelmobs.commands.AcheterTicketCommand;
import fr.fidelmobs.commands.AdminTicketCommand;
import fr.fidelmobs.commands.ArenePvpCommand;
import fr.fidelmobs.commands.ArmeeCommand;
import fr.fidelmobs.commands.BlocCommand;
import fr.fidelmobs.commands.ClassementCommand;
import fr.fidelmobs.commands.EquipementCommand;
import fr.fidelmobs.commands.InvoquerCommand;
import fr.fidelmobs.commands.LierCommand;
import fr.fidelmobs.commands.PointsCommand;
import fr.fidelmobs.commands.RoueCommand;
import fr.fidelmobs.commands.StreakCommand;
import fr.fidelmobs.data.PlayerDataManager;
import fr.fidelmobs.database.ArmySyncTask;
import fr.fidelmobs.database.DatabaseManager;
import fr.fidelmobs.database.TicketSyncTask;
import fr.fidelmobs.listeners.AllyListener;
import fr.fidelmobs.listeners.ArenaProtectionListener;
import fr.fidelmobs.listeners.ArmySyncListener;
import fr.fidelmobs.listeners.LoginListener;
import fr.fidelmobs.managers.ArenaScoreboardManager;
import fr.fidelmobs.managers.ArrowManager;
import fr.fidelmobs.managers.BlockSelectorManager;
import fr.fidelmobs.managers.BuildBlockManager;
import fr.fidelmobs.managers.GearSelectorManager;
import fr.fidelmobs.managers.HologramManager;
import fr.fidelmobs.managers.InvocationManager;
import fr.fidelmobs.managers.KitManager;
import fr.fidelmobs.managers.PowerSelectorManager;
import fr.fidelmobs.managers.PowerUseManager;
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
    private GearSelectorManager gearSelectorManager;
    private ArrowManager arrowManager;
    private PowerSelectorManager powerSelectorManager;
    private PowerUseManager powerUseManager;
    private DatabaseManager databaseManager;
    private ArmySyncTask armySyncTask;

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
        this.gearSelectorManager = new GearSelectorManager(this);
        this.arrowManager = new ArrowManager(this);
        this.powerSelectorManager = new PowerSelectorManager(this);
        this.powerUseManager = new PowerUseManager(this);

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

        AdminTicketCommand adminTicketCommand = new AdminTicketCommand(this);
        getCommand("adminticket").setExecutor(adminTicketCommand);
        getCommand("adminticket").setTabCompleter(adminTicketCommand);

        InvoquerCommand invoquerCommand = new InvoquerCommand(this);
        getCommand("invoquer").setExecutor(invoquerCommand);
        getCommand("invoquer").setTabCompleter(invoquerCommand);

        BlocCommand blocCommand = new BlocCommand(this);
        getCommand("bloc").setExecutor(blocCommand);
        getCommand("bloc").setTabCompleter(blocCommand);

        getCommand("lier").setExecutor(new LierCommand(this));

        // La base MySQL partagée est utilisée par DEUX fonctionnalités optionnelles
        // indépendantes : la boutique de tickets et l'arène de stratégie web. On se connecte
        // dès que l'une des deux est activée ; le reste du plugin fonctionne sans MySQL.
        boolean boutiqueActive = getConfig().getBoolean("boutique.enabled", false);
        boolean strategieWebActive = getConfig().getBoolean("strategie-web.enabled", false);

        if (boutiqueActive || strategieWebActive) {
            this.databaseManager = new DatabaseManager(this);
            try {
                databaseManager.connect();
                getLogger().info("Connexion MySQL partagée établie.");
            } catch (Exception e) {
                getLogger().severe("Échec de connexion MySQL : " + e.getMessage()
                        + " — boutique et arène de stratégie web désactivées pour cette session.");
                databaseManager = null;
            }
        }

        if (databaseManager != null && boutiqueActive) {
            long intervalle = Math.max(5, getConfig().getInt("boutique.sync-interval-seconds", 15)) * 20L;
            getServer().getScheduler().runTaskTimerAsynchronously(this,
                    new TicketSyncTask(this, databaseManager), intervalle, intervalle);
            getLogger().info("Boutique de tickets (MySQL) activée.");
        }

        if (databaseManager != null && strategieWebActive) {
            this.armySyncTask = new ArmySyncTask(this, databaseManager);
            long intervalle = Math.max(5, getConfig().getInt("strategie-web.sync-interval-seconds", 20)) * 20L;
            getServer().getScheduler().runTaskTimerAsynchronously(this, armySyncTask, intervalle, intervalle);
            getServer().getPluginManager().registerEvents(new ArmySyncListener(this), this);
            getLogger().info("Arène de stratégie web (MySQL) activée.");
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

    public GearSelectorManager getGearSelectorManager() {
        return gearSelectorManager;
    }

    public ArrowManager getArrowManager() {
        return arrowManager;
    }

    public PowerSelectorManager getPowerSelectorManager() {
        return powerSelectorManager;
    }

    public PowerUseManager getPowerUseManager() {
        return powerUseManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    /** Peut être {@code null} si {@code strategie-web.enabled} est désactivé dans config.yml. */
    public ArmySyncTask getArmySyncTask() {
        return armySyncTask;
    }
}
