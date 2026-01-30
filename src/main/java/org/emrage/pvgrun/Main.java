package org.emrage.pvgrun;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.emrage.pvgrun.managers.*;
import org.emrage.pvgrun.listeners.*;
import org.emrage.pvgrun.commands.*;
import org.emrage.pvgrun.twitch.TwitchChatDisplayManager;
import org.emrage.pvgrun.util.MessageUtils;
import org.bukkit.World;
import org.bukkit.WorldCreator;

public class Main extends JavaPlugin {

    private GameManager gameManager;
    private BorderManager borderManager;
    private PlayerDataManager playerDataManager;
    private ScoreboardManager scoreboardManager;
    private BanManager banManager;
    private ConfigManager configManager;
    private TablistManager tablistManager;
    private TwitchChatDisplayManager twitchChatManager;
    private TeamManager teamManager;
    private TeamBackpackManager teamBackpackManager;
    private BossBarManager bossBarManager;

    @Override
    public void onEnable() {
        MessageUtils.init();
        this.configManager = new ConfigManager(this);
        this.playerDataManager = new PlayerDataManager(this);
        this.borderManager = new BorderManager(this);
        this.scoreboardManager = new ScoreboardManager(this);
        this.tablistManager = new TablistManager(this);
        this.gameManager = new GameManager(this);
        this.teamManager = new TeamManager();
        this.banManager = new BanManager();
        this.twitchChatManager = new TwitchChatDisplayManager(this);
        this.teamBackpackManager = new TeamBackpackManager();
        this.bossBarManager = new BossBarManager(this);

        // Welten mit echtem Singlebiom generieren, falls Dimension-Modus
        if (getConfigManager().getGameMode().equals("dimension")) {
            if (Bukkit.getWorld("world") == null) {
                WorldCreator overworld = new WorldCreator("world");
                overworld.environment(World.Environment.NORMAL);
                Bukkit.createWorld(overworld);
            }
            if (Bukkit.getWorld("world_nether") == null) {
                WorldCreator nether = new WorldCreator("world_nether");
                nether.environment(World.Environment.NETHER);
                Bukkit.createWorld(nether);
            }
        }

        // Start tablist updates
        tablistManager.start();

        // listeners
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerMoveListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerDamageListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockListener(this), this);
        getServer().getPluginManager().registerEvents(new ItemListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerKickListener(this), this);
        getServer().getPluginManager().registerEvents(new ServerListPingListener(), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new DimensionSelectionListener(this), this);
        getServer().getPluginManager().registerEvents(new TeamRequestListener(this), this);

        // protection & misc listeners
        getServer().getPluginManager().registerEvents(new BorderProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new BorderInteractionListener(this), this);
        getServer().getPluginManager().registerEvents(new BorderBlockDamageListener(this), this);
        getServer().getPluginManager().registerEvents(new HealthRegenBlockerListener(), this);
        getServer().getPluginManager().registerEvents(new FoodLevelChangeListener(this), this);

        // commands
        if (getCommand("start") != null) getCommand("start").setExecutor(new StartCommand(this));
        if (getCommand("exclude") != null) getCommand("exclude").setExecutor(new ExcludeCommand(this));
        if (getCommand("test") != null) getCommand("test").setExecutor(new TestCommand(this));
        if (getCommand("team") != null) getCommand("team").setExecutor(new org.emrage.pvgrun.commands.TeamCommand(this));
        if (getCommand("backpack") != null) getCommand("backpack").setExecutor(new org.emrage.pvgrun.commands.BackpackCommand(this));
        if (getCommand("setnetherspawn") != null) getCommand("setnetherspawn").setExecutor(new SetNetherSpawnCommand(this));
        if (getCommand("scorepreview") != null) getCommand("scorepreview").setExecutor(new ScorePreviewCommand(this));

        // Start Twitch chat display
        org.bukkit.Bukkit.getScheduler().runTaskLater(this, () -> {
            org.bukkit.World world = org.bukkit.Bukkit.getWorld("world");
            if (world != null) {
                org.bukkit.Location loc = new org.bukkit.Location(world, 0, 100, 0);
                twitchChatManager.start("HardcorePvG", loc);
            }
        }, 40L); // Wait 2 seconds after server start

        getLogger().info("PvG-Run enabled");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) gameManager.shutdown();
        if (borderManager != null) borderManager.shutdown();
        if (scoreboardManager != null) scoreboardManager.removeAll();
        if (tablistManager != null) tablistManager.shutdown();
        if (twitchChatManager != null && twitchChatManager.isRunning()) {
            twitchChatManager.stop();
        }
        getLogger().info("PvG-Run disabled");
    }

    public void updateVisibility(Player viewer) {
        for (Player target : Bukkit.getOnlinePlayers()) {

            boolean shouldHide =
                    this.getPlayerDataManager().isDead(target)
                            || this.getPlayerDataManager().isExcluded(target)
                            || target.getGameMode() == GameMode.SPECTATOR;

            if (shouldHide) {
                viewer.hidePlayer(this, target);
            } else {
                viewer.showPlayer(this, target);
            }
        }
    }

    public GameManager getGameManager() { return gameManager; }
    public BorderManager getBorderManager() { return borderManager; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public ScoreboardManager getScoreboardManager() { return scoreboardManager; }
    public BanManager getBanManager() { return banManager; }
    public ConfigManager getConfigManager() { return configManager; }
    public TablistManager getTablistManager() { return tablistManager; }
    public TwitchChatDisplayManager getTwitchChatManager() { return twitchChatManager; }
    public TeamManager getTeamManager() { return teamManager; }
    public TeamBackpackManager getTeamBackpackManager() { return teamBackpackManager; }
    public BossBarManager getBossBarManager() { return bossBarManager; }
}