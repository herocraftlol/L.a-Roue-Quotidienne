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
import fr.fidelmobs.listeners.CrossServerSyncListener;
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

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // MySQL est nécessaire pour la boutique en argent réel ET/OU pour partager les
        // données joueur (tickets, collection, points...) entre tous les serveurs Paper de
        // la structure Velocity. On l'active dès que l'un des deux est demandé, et le
        // PlayerDataManager passe en mode partagé UNIQUEMENT si multi-serveur.enabled est
        // vrai (la boutique seule ne change pas où sont stockées les données joueur).
        boolean boutiqueActivee = getConfig().getBoolean("boutique.enabled", false);
        boolean multiServeurDemande = getConfig().getBoolean("multi-serveur.enabled", false);

        if (boutiqueActivee || multiServeurDemande) {
            this.databaseManager = new DatabaseManager(this);
            try {
                databaseManager.connect();
                getLogger().info("Connexion MySQL établie.");
            } catch (Exception e) {
                getLogger().severe("Échec de connexion MySQL : " + e.getMessage()
                        + " — boutique et/ou mode multi-serveur désactivés pour cette session"
                        + " (repli sur les données joueur locales).");
                databaseManager = null;
            }
        }

        // Le PlayerDataManager ne passe en mode "partagé" que si multi-serveur.enabled est
        // vrai ET que la connexion MySQL a réussi ; sinon comportement historique (YAML local),
        // même si la boutique tourne par ailleurs sur sa propre connexion MySQL.
        boolean modePartageEffectif = multiServeurDemande && databaseManager != null;
        this.playerDataManager = modePartageEffectif
                ? new PlayerDataManager(this, databaseManager)
                : new PlayerDataManager(this);
        if (multiServeurDemande && !modePartageEffectif) {
            getLogger().warning("multi-serveur.enabled est à true mais la connexion MySQL a échoué : "
                    + "les données joueur restent locales à ce serveur pour l'instant.");
        } else if (modePartageEffectif) {
            getLogger().info("Mode multi-serveur activé : données joueur partagées via MySQL.");
        }

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
        if (modePartageEffectif) {
            getServer().getPluginManager().registerEvents(new CrossServerSyncListener(this), this);
        }

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

        // Boutique en argent réel (achat de tickets sur le site web) : entièrement optionnelle.
        // Réutilise la connexion MySQL déjà établie plus haut (partagée avec le mode
        // multi-serveur si les deux sont actifs en même temps).
        if (boutiqueActivee && databaseManager != null) {
            long intervalle = Math.max(5, getConfig().getInt("boutique.sync-interval-seconds", 15)) * 20L;
            getServer().getScheduler().runTaskTimerAsynchronously(this,
                    new TicketSyncTask(this, databaseManager), intervalle, intervalle);
            getLogger().info("Boutique de tickets (MySQL) activée.");
        } else if (boutiqueActivee) {
            getLogger().warning("boutique.enabled est à true mais la connexion MySQL a échoué : "
                    + "la boutique en argent réel est désactivée pour cette session.");
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
}
