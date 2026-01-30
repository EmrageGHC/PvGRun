package org.emrage.pvgrun.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.emrage.pvgrun.Main;
import org.emrage.pvgrun.enums.GameState;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static org.emrage.pvgrun.util.MessageUtils.c;
import org.emrage.pvgrun.util.HeadUtil;

public class GameManager {

    private final Main plugin;
    private volatile GameState state = GameState.LOBBY;
    private int secondsElapsed = 0;
    private int MAX_SECONDS = 3600;
    private int MOB_SPAWN_SECONDS = 600;

    private BukkitRunnable gameTask;
    private BukkitRunnable hostileMobTask;
    private BukkitRunnable lobbyActionBarTask;
    private BukkitRunnable runningActionBarTask;
    private boolean hostileActivated = false;
    private int pausePreLeftSeconds = 0; // pre-pause countdown left seconds

    private final Set<UUID> participants = Collections.synchronizedSet(new LinkedHashSet<>());

    private static final double LOBBY_WORLDBORDER_SIZE = 100.0;
    private static final double GAME_WORLDBORDER_SIZE = 600_000.0;

    private int countdownSeconds = 60;
    private boolean pauseActive = false;
    private int pauseSeconds = 0;
    // Flag that a pre-pause title-countdown is running (pause not yet active)
    private boolean pauseStarting = false;
    // Pause countdown values exposed for BossBar/other UI
    private int pauseLeftSeconds = 0;
    private int pauseTotalSeconds = 0;

    private boolean titlePhaseStarted = false;

    public GameManager(Main plugin) {
        this.plugin = plugin;
        loadConfigValues();
        applyWorldRules();
        startLobbyActionBar();
    }

    private void loadConfigValues() {
        MAX_SECONDS = plugin.getConfigManager().getGameDurationSeconds();
        MOB_SPAWN_SECONDS = plugin.getConfigManager().getMobSpawnDelaySeconds();
    }

    private void applyWorldRules() {
        World world = Bukkit.getWorld("world");
        if (world != null) {
            world.setGameRule(GameRule.NATURAL_REGENERATION, false);
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            world.setSpawnFlags(false, false); // Mobs komplett deaktivieren
            world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
            world.setSpawnLocation(0, world.getHighestBlockYAt(0,0), 0);
            world.setDifficulty(plugin.getConfigManager().getDifficulty());
            WorldBorder wb = world.getWorldBorder();
            wb.setCenter(0,0);
            wb.setSize(LOBBY_WORLDBORDER_SIZE);
            wb.setWarningDistance(5);
            wb.setDamageAmount(0);
        }
        World nether = Bukkit.getWorld("world_nether");
        if (nether != null) {
            nether.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            nether.setSpawnFlags(false, false); // Nether-Mobs komplett deaktivieren
            // Ensure a visible lobby worldborder in the nether as well during PREPARING/LOBBY
            WorldBorder nb = nether.getWorldBorder();
            nb.setCenter(0,0);
            nb.setSize(LOBBY_WORLDBORDER_SIZE);
            nb.setWarningDistance(5);
            nb.setDamageAmount(0);
        }
    }

    public Location getSpawnLocation() {
        World world = Bukkit.getWorld("world");
        int y = world == null ? 64 : world.getHighestBlockYAt(0,0) + 1;
        return new Location(world, 0.5, y, 0.5);
    }

    private void startLobbyActionBar() {
        if (lobbyActionBarTask != null) lobbyActionBarTask.cancel();
        lobbyActionBarTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != GameState.LOBBY) return;

                // LOBBY PHASE in light-blue and bold
                Component bar = c("<#00d4ff><bold>LOBBY PHASE</bold>");
                for (Player p : Bukkit. getOnlinePlayers()) {
                    p.sendActionBar(bar);
                    // Ensure persistent lobby BossBar is visible for each player
                    try { plugin.getBossBarManager().showLobbyBarFor(p); } catch (Exception ignored) {}
                }
            }
        };
        lobbyActionBarTask.runTaskTimer(plugin, 0L, 20L);
    }

    private void startRunningActionBar() {
        if (runningActionBarTask != null) runningActionBarTask.cancel();
        runningActionBarTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != GameState.RUNNING) {
                    cancel();
                    return;
                }
                for (Player p : Bukkit.getOnlinePlayers()) {
                    // If pause active, override actionbar with PAUSE (gold & bold)
                    if (pauseActive) {
                        p.sendActionBar(c("<gold><bold>PAUSE</bold>"));
                        continue;
                    }
                    int place = plugin.getPlayerDataManager().getPlacement(p);
                    // Show placement as pink and bold, keep it persistent
                    p.sendActionBar(c("<#ff00c8><bold>#" + place + "</bold>"));
                }
            }
        };
        runningActionBarTask.runTaskTimer(plugin, 0L, 10L);
        plugin.getLogger().info("[ActionBar] running actionbar task started (shows #placement every 0.5s)");
    }

    // Check for winner condition (exposed for listeners to call after a death)
    public void checkForWinner() {
        if (state != GameState.RUNNING) return;
        Collection<Player> active = activePlayers();
        if (active.isEmpty()) {
            endGame();
        } else if (active.size() == 1) {
            // Optionally, you could declare winner here before ending
            endGame();
        }
    }

    public synchronized void startGame(Player starter, boolean force) {
        if (!starter.isOp()) { starter.sendMessage(c("<#FF5555>Du hast keine Berechtigung!")); return; }
        if (state != GameState.LOBBY) { starter.sendMessage(c("<#FF5555>Das Spiel läuft bereits!")); return; }

        int active = (int) Bukkit.getOnlinePlayers().stream().filter(p -> !plugin.getPlayerDataManager().isExcluded(p)).count();
        if (!force && active < 2) {
            starter.sendMessage(c("<#FF5555>Mindestens 2 Spieler werden benötigt!  Verwende /start force zum Testen. "));
            return;
        }

        state = GameState.PREPARING;
        // Leaving lobby: hide persistent lobby bars
        try { plugin.getBossBarManager().hideAllLobbyBars(); } catch (Exception ignored) {}
        if (lobbyActionBarTask != null) { lobbyActionBarTask.cancel(); lobbyActionBarTask = null; }
        participants.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!plugin.getPlayerDataManager().isExcluded(p)) participants.add(p.getUniqueId());
        }
        loadConfigValues();
        // Add bossbars for all active/excluded now (prepare phase)
        plugin.getBossBarManager().addAllForActive();
        // Ensure preparatory world-borders are present so players see a bounded area before the run starts
        try {
            setWorldBorderPreparing(Bukkit.getWorld("world"));
            setWorldBorderPreparing(Bukkit.getWorld("world_nether"));
        } catch (Exception ignored) {}
        // Dimension Deathrun Logik
        if (plugin.getConfigManager().getGameMode().equals("dimension")) {
            // Ensure nether spawn set before starting
            if (!plugin.getConfigManager().isNetherSpawnSet() && !force) {
                starter.sendMessage(c("<#FF5555>Der Nether-Spawn ist noch nicht gesetzt. Setze ihn mit /setnetherspawn bevor du startest."));
                state = GameState.LOBBY;
                if (lobbyActionBarTask == null) startLobbyActionBar();
                return;
            }
             // Entferne alle Team- und Dimension-Items
             for (Player p : Bukkit.getOnlinePlayers()) {
                 if (p.getInventory().getItem(4) != null) p.getInventory().clear(4);
             }
            // Einzelspieler zu Teams zusammenfassen, falls nötig
            List<Player> soloPlayers = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !plugin.getPlayerDataManager().isExcluded(p))
                .filter(p -> !plugin.getTeamManager().hasTeam(p))
                .collect(Collectors.toList());
            Collections.shuffle(soloPlayers);
            for (int i = 0; i < soloPlayers.size(); i += 2) {
                Player p1 = soloPlayers.get(i);
                Player p2 = (i + 1 < soloPlayers.size()) ? soloPlayers.get(i + 1) : null;
                if (p2 != null) {
                    String teamName = org.emrage.pvgrun.managers.TeamManager.generateTeamName(p1.getName(), p2.getName());
                    org.emrage.pvgrun.managers.TeamManager.Team team = new org.emrage.pvgrun.managers.TeamManager.Team(p1.getUniqueId(), p2.getUniqueId(), teamName);
                    plugin.getTeamManager().getTeams().add(team);
                    plugin.getTeamManager().getPlayerToTeamMap().put(p1.getUniqueId(), team);
                    plugin.getTeamManager().getPlayerToTeamMap().put(p2.getUniqueId(), team);
                } else {
                    String name = p1.getName();
                    org.emrage.pvgrun.managers.TeamManager.Team team = new org.emrage.pvgrun.managers.TeamManager.Team(p1.getUniqueId(), p1.getUniqueId(), name);
                    plugin.getTeamManager().getTeams().add(team);
                    plugin.getTeamManager().getPlayerToTeamMap().put(p1.getUniqueId(), team);
                }
            }
            // Teams verarbeiten
            List<org.emrage.pvgrun.managers.TeamManager.Team> teams = plugin.getTeamManager().getTeams();
            Random rand = new Random();
            for (var team : teams) {
                // Dimensionen zuweisen (falls nicht gewählt, zufällig)
                String dim1 = team.getPlayerDimension(team.getMembers().get(0));
                String dim2 = team.getMembers().size() > 1 ? team.getPlayerDimension(team.getMembers().get(1)) : null;
                if (dim1 == null && (dim2 == null || team.getMembers().size() == 1)) {
                    if (rand.nextBoolean()) {
                        team.setPlayerDimension(team.getMembers().get(0), "overworld");
                        if (team.getMembers().size() > 1) team.setPlayerDimension(team.getMembers().get(1), "nether");
                    } else {
                        team.setPlayerDimension(team.getMembers().get(0), "nether");
                        if (team.getMembers().size() > 1) team.setPlayerDimension(team.getMembers().get(1), "overworld");
                    }
                } else if (dim1 != null && dim2 == null && team.getMembers().size() > 1) {
                    team.setPlayerDimension(team.getMembers().get(1), dim1.equals("overworld") ? "nether" : "overworld");
                } else if (dim2 != null && dim1 == null && team.getMembers().size() > 1) {
                    team.setPlayerDimension(team.getMembers().getFirst(), dim2.equals("overworld") ? "nether" : "overworld");
                }
                // Teleportiere nur die Spieler, deren assigned dimension nether ist. Overworld-Spieler bleiben an ihrem Platz.
                for (UUID uuid : team.getMembers()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null) continue;
                    String dim = team.getPlayerDimension(uuid);
                    if (dim != null && dim.equals("nether")) {
                        World world = Bukkit.getWorld("world_nether");
                        Location spawn = getSafeSpawn(world, "nether");
                        p.teleport(spawn);
                    }
                }
            }
            // Backpacks für alle Teams initialisieren
            for (var team : teams) {
                plugin.getTeamBackpackManager().getBackpack(team.getName());
            }
        }
        startCountdown();
    }

    private void setWorldBorderPreparing(World world) {
        if (world == null) return;
        WorldBorder wb = world.getWorldBorder();
        wb.setCenter(0, 0);
        // Set a visible lobby border so players see the bounded area during prepare
        wb.setSize(LOBBY_WORLDBORDER_SIZE);
        wb.setWarningDistance(0);
        wb.setDamageAmount(0);
    }

    private Location getSafeSpawn(World world, String dim) {
        if (world == null) return new Location(Bukkit.getWorld("world"), 0.5, 100, 0.5);
        if (dim.equals("nether")) {
            for (int tries = 0; tries < 100; tries++) {
                int x = (int) (Math.random() * 40 - 20);
                int z = (int) (Math.random() * 40 - 20);
                int y = world.getHighestBlockYAt(x, z);
                if (y >= 128) continue; // Netherdecke vermeiden
                Location loc = new Location(world, x + 0.5, y + 1, z + 0.5);
                Material below = world.getBlockAt(x, y, z).getType();
                Material at = world.getBlockAt(x, y + 1, z).getType();
                Material above = world.getBlockAt(x, y + 2, z).getType();
                Biome biome = world.getBiome(x, y, z);
                if (biome != Biome.BASALT_DELTAS) continue; // Nur Basalt-Delta
                if (below != Material.LAVA && below != Material.AIR && below != Material.FIRE
                    && at == Material.AIR && above == Material.AIR) {
                    return loc;
                }
            }
            // Fallback: Spawn in Basalt-Delta auf y<128
            for (int x = -20; x <= 20; x++) {
                for (int z = -20; z <= 20; z++) {
                    int y = world.getHighestBlockYAt(x, z);
                    if (y >= 128) continue;
                    Biome biome = world.getBiome(x, y, z);
                    Material below = world.getBlockAt(x, y, z).getType();
                    Material at = world.getBlockAt(x, y + 1, z).getType();
                    Material above = world.getBlockAt(x, y + 2, z).getType();
                    if (biome == Biome.BASALT_DELTAS) {
                        if (below != Material.LAVA && below != Material.AIR && below != Material.FIRE
                            && at == Material.AIR && above == Material.AIR) {
                            return new Location(world, x + 0.5, y + 1, z + 0.5);
                        }
                    }
                }
            }
            return new Location(world, 0.5, 70, 0.5); // Sicherer Default
        } else {
            // Overworld: Nur Snowy Taiga
            for (int tries = 0; tries < 100; tries++) {
                int x = (int) (Math.random() * 40 - 20);
                int z = (int) (Math.random() * 40 - 20);
                int y = world.getHighestBlockYAt(x, z);
                Biome biome = world.getBiome(x, y, z);
                Material below = world.getBlockAt(x, y, z).getType();
                Material at = world.getBlockAt(x, y + 1, z).getType();
                Material above = world.getBlockAt(x, y + 2, z).getType();
                if (biome != Biome.SNOWY_TAIGA) continue;
                if (below != Material.LAVA && below != Material.AIR && below != Material.FIRE
                    && at == Material.AIR && above == Material.AIR) {
                    return new Location(world, x + 0.5, y + 1, z + 0.5);
                }
            }
            // Fallback: Spawn in Snowy Taiga
            for (int x = -20; x <= 20; x++) {
                for (int z = -20; z <= 20; z++) {
                    int y = world.getHighestBlockYAt(x, z);
                    Biome biome = world.getBiome(x, y, z);
                    Material below = world.getBlockAt(x, y, z).getType();
                    Material at = world.getBlockAt(x, y + 1, z).getType();
                    Material above = world.getBlockAt(x, y + 2, z).getType();
                    if (biome == Biome.SNOWY_TAIGA) {
                        if (below != Material.LAVA && below != Material.AIR && below != Material.FIRE
                            && at == Material.AIR && above == Material.AIR) {
                            return new Location(world, x + 0.5, y + 1, z + 0.5);
                        }
                    }
                }
            }
            return new Location(world, 0.5, 70, 0.5); // Sicherer Default
        }
    }

    // private void setWorldBorder(World world) { ... } // Methode bleibt, Warnung ignorieren

    private void startTitlePhase() {
        if (titlePhaseStarted) return;
        titlePhaseStarted = true;

        new BukkitRunnable() {
            int seconds = 5;

            @Override
            public void run() {

                // START
                if (seconds == 0) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.showTitle(Title.title(
                                c("<bold><light_purple>GO!</light_purple></bold>"),
                                c("<gray>Laufe in Richtung <light_purple>Norden!</light_purple>")
                        ));
                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                    }

                    if (plugin.getBorderManager() != null) {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            plugin.getBorderManager().checkPlayer(p);
                        }
                    }

                    beginRunning();
                    cancel();
                    return;
                }

                // Farbe je nach Sekunde
                String color;
                switch (seconds) {
                    case 5 -> color = "red";
                    case 4 -> color = "gold";
                    case 3, 2 -> color = "yellow";
                    case 1 -> color = "green";
                    default -> color = "white";
                }

                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.showTitle(Title.title(
                            c("<bold><" + color + ">" + seconds + "</" + color + "></bold>"),
                            c("<gray>Laufe in Richtung <light_purple>Norden!</light_purple>")
                    ));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                }

                try {
                    plugin.getBossBarManager().updateAll();
                } catch (Exception ignored) {}

                seconds--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }


    private void startCountdown() {
        countdownSeconds = 60;
        titlePhaseStarted = false;
        new BukkitRunnable() {
            @Override
            public void run() {
                if (countdownSeconds <= 0) {
                    // If not already started by reaching 5, start title phase now
                    if (!titlePhaseStarted) startTitlePhase();
                    cancel();
                    return;
                }
                // If countdown reaches 5 seconds, start the title phase (but keep outer countdown running)
                if (countdownSeconds == 5 && !titlePhaseStarted) {
                    startTitlePhase();
                }
                // Actionbar: Event startet in Kürze (light-blue, bold)
                Component bar = c("<#00d4ff><bold>DAS EVENT STARTET IN KÜRZE</bold>");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendActionBar(bar);
                    p.setLevel(countdownSeconds);
                }
                // Update bossbars during preparing countdown as well
                try { plugin.getBossBarManager().updateAll(); } catch (Exception ignored) {}
                countdownSeconds--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void beginRunning() {
        state = GameState.RUNNING;
        secondsElapsed = 0;

        // Stop Twitch chat display when deathrun starts
        if (plugin.getTwitchChatManager() != null && plugin.getTwitchChatManager().isRunning()) {
            plugin.getTwitchChatManager().stop();
        }

        // Ensure any persistent lobby bossbars are hidden when the run begins
        try { plugin.getBossBarManager().hideAllLobbyBars(); } catch (Exception ignored) {}
        // Remove any existing bossbars and recreate timer bossbars so they are shown cleanly
        try { plugin.getBossBarManager().removeAll(); } catch (Exception ignored) {}
        try { plugin.getBossBarManager().addAllForActive(); } catch (Exception ignored) {}
        // Force an immediate bossbar update so the timer shows right away
        try { plugin.getBossBarManager().updateAll(); } catch (Exception ignored) {}

        for (UUID u : participants) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && plugin.getPlayerDataManager().isExcluded(p)) p.setGameMode(GameMode.SPECTATOR);
        }

        World world = Bukkit.getWorld("world");
        if (world != null) {
            // Border jetzt groß setzen!
            WorldBorder wb = world.getWorldBorder();
            wb.setCenter(0,0);
            wb.setSize(GAME_WORLDBORDER_SIZE);
            wb.setWarningDistance(0);
            wb.setDamageAmount(0);
            world.setGameRule(GameRule.DO_MOB_SPAWNING, true);
            world.setSpawnFlags(true, false); // Nur friedliche Mobs ab Start
        }
        World nether = Bukkit.getWorld("world_nether");
        if (nether != null) {
            WorldBorder wb = nether.getWorldBorder();
            wb.setCenter(0,0);
            wb.setSize(GAME_WORLDBORDER_SIZE);
            wb.setWarningDistance(0);
            wb.setDamageAmount(0);
            nether.setGameRule(GameRule.DO_MOB_SPAWNING, true);
            nether.setSpawnFlags(true, false); // Nur friedliche Mobs ab Start
        }

        plugin.getScoreboardManager().updateAllForActive();
        startRunningActionBar();

        Title goTitle = Title.title(c("<#55FF55><bold>GO!</bold>"), c("<#AAAAAA>Viel Erfolg!"), Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(800), Duration.ofMillis(400)));
        Bukkit.getOnlinePlayers().forEach(p -> {
            p.showTitle(goTitle);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 1.0f, 1.5f);
        });

        gameTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Only advance main timer when not in pre-pause or active pause
                if (!pauseActive && !pauseStarting) {
                    secondsElapsed++;
                }

                // Pausenmechanik trigger: use secondsElapsed so it's paused when pauseActive/starting
                if (MAX_SECONDS >= 7200 && secondsElapsed == 3600) {
                    startPause();
                }

                // Check mob spawn activation bound to the main timer (ensures pause affects it)
                if (!hostileActivated && secondsElapsed >= MOB_SPAWN_SECONDS) {
                    hostileActivated = true;
                    // activate hostile mobs now
                    activateHostileSpawning();
                }

                // Always update bossbars (so PAUSE/Timer updates are visible). Only update scoreboards when not paused.
                try { plugin.getBossBarManager().updateAll(); } catch (Exception ignored) {}
                if (!pauseActive) {
                    try { plugin.getScoreboardManager().updateAllForActive(); } catch (Exception ignored) {}
                }
                if (secondsElapsed >= MAX_SECONDS) endGame();
            }
        };
        gameTask.runTaskTimer(plugin, 20L, 20L);
    }

    private void activateHostileSpawning() {
        World w = Bukkit.getWorld("world");
        if (w != null) {
            w.setGameRule(GameRule.DO_MOB_SPAWNING, true);
            w.setSpawnFlags(true, true); // Jetzt auch feindliche Mobs
        }
        World n = Bukkit.getWorld("world_nether");
        if (n != null) {
            n.setGameRule(GameRule.DO_MOB_SPAWNING, true);
            n.setSpawnFlags(true, true); // Jetzt auch feindliche Mobs
        }

        // Show a title and play a sound alerting players
        HeadUtil.HeadImage hi = null;
        String[] tryUrls = new String[] {
                "https://mc-heads.net/avatar/creeper",
                "https://mc-heads.net/head/creeper",
                "https://mc-heads.net/avatars/creeper"
        };
        for (String u : tryUrls) {
            try {
                hi = HeadUtil.getHeadFromUrl(u);
                String[] pix = hi.pixels();
                if (pix != null && pix.length == 64 && !("#FF00FF".equals(pix[0]) && "#000000".equals(pix[1]))) {
                    break;
                }
            } catch (Exception ignored) {}
        }
        Component headComp;
        if (hi == null) {
            plugin.getLogger().warning("Failed to fetch creeper head images from mc-heads.net; using text fallback.");
            headComp = c("<#55AA00><bold>CREEPER</bold>");
        } else {
            String[] pix = hi.pixels();
            boolean checker = pix == null || pix.length != 64 || ("#FF00FF".equals(pix[0]) && "#000000".equals(pix[1]));
            if (checker) {
                plugin.getLogger().warning("Creeper head fetch returned checkerboard fallback; using text fallback.");
                headComp = c("<#55AA00><bold>CREEPER</bold>");
            } else {
                headComp = hi.rotate(-90).asComponent();
            }
        }

        Component subtitle = c("<red>Das Mob-Spawning ist nun aktiv");
        Title.Times times = Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(300));
        Title headTitle = Title.title(headComp, subtitle, times);

        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                p.showTitle(headTitle);
                try { p.playSound(p.getLocation(), Sound.ENTITY_CREEPER_PRIMED, SoundCategory.MASTER, 1.0f, 1.0f); } catch (Exception e) {
                    try { p.playSound(p.getLocation(), Sound.ENTITY_CREEPER_HURT, SoundCategory.MASTER, 1.0f, 1.0f); } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        }

        Bukkit.broadcast(c("<#FF5555><bold>⚠ Achtung!</bold> Feindliche Mobs spawnen jetzt! "));
    }

    // expose a public method for admin/testing to start a pause with custom seconds
    public void startPause(int seconds) {
        this.pauseSeconds = seconds;
        startPause();
    }

    private void startPause() {
        // Prevent overlapping starts
        if (pauseActive || pauseStarting) return;

        // If pauseSeconds hasn't been set by caller (<=0), apply default 10 minutes
        if (pauseSeconds <= 0) pauseSeconds = 600;

        pauseStarting = true;
        // set total for display
        pauseTotalSeconds = pauseSeconds;

        // Pre-pause countdown (5s) visual. Note: pauseActive stays false until the pre-countdown finished.
        new BukkitRunnable() {
            int pre = 5;
            @Override
            public void run() {
                if (pre > 0) {
                    // update pre-countdown left seconds for bossbar
                    pausePreLeftSeconds = pre;
                    Component title = c("<yellow><bold>Pause in " + pre + " Sekunden!</bold>");
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.showTitle(Title.title(title, Component.empty(), Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(700), Duration.ofMillis(150))));
                        try { p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 1f, 1f); } catch (Exception ignored) {}
                    }
                    try { plugin.getBossBarManager().updateAll(); } catch (Exception ignored) {}
                    pre--;
                    return;
                }

                // Now actually start the pause
                pauseStarting = false;
                pauseActive = true;
                pauseLeftSeconds = pauseTotalSeconds;

                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendActionBar(c("<yellow><bold>PAUSE</bold>"));
                    p.setWalkSpeed(0f);
                    p.setFlySpeed(0f);
                    p.setInvulnerable(true);
                }
                try { plugin.getBossBarManager().updateAll(); } catch (Exception ignored) {}

                // Run the main pause timer with 5s end countdown
                new BukkitRunnable() {
                    int left = pauseSeconds;
                    @Override
                    public void run() {
                        // update exposed counter
                        pauseLeftSeconds = left;

                        if (left <= 5 && left > 0) {
                            Component title = c("<green><bold>Pause endet in " + left + " Sekunden!</bold>");
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                p.showTitle(Title.title(title, Component.empty(), Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(700), Duration.ofMillis(150))));
                                try { p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 1f, 1f); } catch (Exception ignored) {}
                            }
                        }
                        try { plugin.getBossBarManager().updateAll(); } catch (Exception ignored) {}
                        if (left <= 0) {
                            // End pause
                            pauseActive = false;
                            pauseLeftSeconds = 0;
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                p.sendActionBar(c("<green><bold>Timer läuft weiter!</bold>"));
                                p.setWalkSpeed(0.2f);
                                p.setFlySpeed(0.1f);
                                p.setInvulnerable(false);
                                try { p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.MASTER, 1.0f, 1.0f); } catch (Exception ignored) {}
                            }
                            try { plugin.getBossBarManager().updateAll(); } catch (Exception ignored) {}
                            cancel();
                            return;
                        }
                        left--;
                    }
                }.runTaskTimer(plugin, 0L, 20L);

                cancel();
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public int getPausePreLeftSeconds() { return pausePreLeftSeconds; }

    // Expose pause info for UI
    public int getPauseLeftSeconds() { return pauseLeftSeconds; }
    public int getPauseTotalSeconds() { return pauseTotalSeconds; }
    public int getCountdownSeconds() { return countdownSeconds; }
    public boolean isPauseActive() { return pauseActive; }
    public boolean isPauseStarting() { return pauseStarting; }

    // Basic getters used across the codebase
    public GameState getState() { return state; }
    public int getSecondsElapsed() { return secondsElapsed; }
    public int getMaxSeconds() { return MAX_SECONDS; }
    public String getFormattedTime() {
        int remaining = Math.max(0, MAX_SECONDS - secondsElapsed);
        int hours = remaining / 3600;
        int minutes = (remaining % 3600) / 60;
        int seconds = remaining % 60;
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    // Return currently active participants (online, not excluded, not dead)
    public Collection<Player> activePlayers() {
        List<Player> out = new ArrayList<>();
        for (UUID u : participants) {
            Player p = Bukkit.getPlayer(u);
            if (p == null) continue;
            if (plugin.getPlayerDataManager().isExcluded(p)) continue;
            if (plugin.getPlayerDataManager().isDead(p)) continue;
            out.add(p);
        }
        return out;
    }
    // Return players currently excluded (online)
    public Collection<Player> excludedPlayers() {
        List<Player> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.getPlayerDataManager().isExcluded(p)) out.add(p);
        }
        return out;
    }

    // Check whether a UUID was a participant in this run
    public boolean wasParticipant(UUID u) {
        return u != null && participants.contains(u);
    }

    // Returns true when the configured gamemode is the dimension/duo mode
    public boolean isDuoRun() {
        return plugin.getConfigManager() != null && "dimension".equals(plugin.getConfigManager().getGameMode());
    }

    // Return teams sorted by team distance descending
    public List<org.emrage.pvgrun.managers.TeamManager.Team> getTeamsSorted() {
        List<org.emrage.pvgrun.managers.TeamManager.Team> teams = new ArrayList<>(plugin.getTeamManager().getTeams());
        teams.sort(Comparator.comparingDouble((org.emrage.pvgrun.managers.TeamManager.Team t) -> -getTeamDistance(t)));
        return teams;
    }

    // Compute team score as sum of stored distances of online members (fallback includes offline stored distances)
    public double getTeamDistance(org.emrage.pvgrun.managers.TeamManager.Team team) {
        double sum = 0.0;
        for (UUID u : team.getMembers()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                sum += plugin.getPlayerDataManager().getStoredDistance(p.getName());
            } else {
                // try to use stored name mapping via PlayerDataManager if available
                // best-effort: iterate stored distances map (not ideal but safe)
                // skip for now
            }
        }
        return sum;
    }

    // Return solo players (active participants) sorted by stored distance desc
    public List<Player> getSoloSorted() {
        List<Player> list = new ArrayList<>(activePlayers());
        list.sort(Comparator.comparingDouble((Player p) -> plugin.getPlayerDataManager().getStoredDistance(p.getName())).reversed());
        return list;
    }

    public synchronized void endGame() {
        if (state != GameState.RUNNING) return;
        state = GameState.ENDING;

        if (gameTask != null) { gameTask.cancel(); gameTask = null; }
        if (hostileMobTask != null) { hostileMobTask.cancel(); hostileMobTask = null; }
        if (runningActionBarTask != null) { runningActionBarTask.cancel(); runningActionBarTask = null; }

        // Build leaderboard (top 10) depending on game mode (solo vs team)
        boolean duo = isDuoRun();
        final int MAX_TOP = 10;

        // Leader entry representation (simple POJO for compatibility)
        class Leader { String displayName; double score; Leader(String displayName, double score){ this.displayName = displayName; this.score = score; } }

        List<Leader> leaders = new ArrayList<>();
        if (duo) {
            // teams
            List<org.emrage.pvgrun.managers.TeamManager.Team> teams = getTeamsSorted();
            for (org.emrage.pvgrun.managers.TeamManager.Team t : teams) {
                double s = getTeamDistance(t);
                leaders.add(new Leader(t.getName(), s));
            }
        } else {
            // solo players
            List<Player> solos = getSoloSorted();
            for (Player pl : solos) {
                double s = plugin.getPlayerDataManager().getStoredDistance(pl.getName());
                leaders.add(new Leader(pl.getName(), s));
            }
        }

        // Limit to MAX_TOP
        List<Leader> top = leaders.subList(0, Math.min(MAX_TOP, leaders.size()));

        // Broadcast a short server-wide Top header
        Bukkit.broadcast(c("\n<#FFD700><bold>━━━ DEATHRUN TOP " + Math.min(MAX_TOP, Math.max(1, leaders.size())) + " ━━━</bold>"));
        for (int i = 0; i < top.size(); i++) {
            Leader l = top.get(i);
            String num = "#" + (i+1);
            String placeColor = (i < 3) ? "<#55FF55>" : "<#FFFFFF>"; // top3 light green
            String line = placeColor + "<bold>" + num + "</bold> " + c(l.displayName).toString();
            // broadcast a simple text line (avoid huge components here)
            Bukkit.broadcast(c(placeColor + (i==0?"👑 ": i==1?"🥈 ": i==2?"🥉 ": "◆ ") + "<bold>#" + (i+1) + "</bold> " + l.displayName + " <#AAAAAA>" + String.format("%dm", (int)Math.round(l.score))));
        }
        Bukkit.broadcast(c("<#FFD700><bold>━━━━━━━━━━━━━━━━━━━</bold>\n"));

        // Kick all participants with a personalized TOP message (shows own placement and the TOP list)
        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID u : new HashSet<>(participants)) {
                    Player p = Bukkit.getPlayer(u);

                    // Determine player's own placement and score
                    int ownPlace = -1;
                    double ownScore = 0;
                    String ownName = "";
                    if (duo) {
                        var team = plugin.getTeamManager().getTeam(p);
                        if (team != null) {
                            ownName = team.getName();
                            ownScore = getTeamDistance(team);
                            // find index
                            for (int i = 0; i < leaders.size(); i++) {
                                if (leaders.get(i).displayName.equalsIgnoreCase(ownName)) { ownPlace = i + 1; break; }
                            }
                        } else {
                            // not in team -> show individual (fallback)
                            if (p != null) { ownName = p.getName(); ownScore = plugin.getPlayerDataManager().getStoredDistance(p.getName()); ownPlace = plugin.getPlayerDataManager().getPlacement(p); }
                        }
                    } else {
                        if (p != null) {
                            ownName = p.getName();
                            ownScore = plugin.getPlayerDataManager().getStoredDistance(p.getName());
                            ownPlace = plugin.getPlayerDataManager().getPlacement(p);
                        }
                    }

                    if (ownPlace <= 0) ownPlace = leaders.size() + 1;

                    // Build the kick message using MiniMessage-ish markup (MessageUtils.c)
                    StringBuilder sb = new StringBuilder();
                    sb.append("<#FFD700><bold>━━━━━━━━━━━━━━━━━━━━━━━━</bold>\n");
                    sb.append("<#FFD700><bold>TOP " + Math.min(MAX_TOP, Math.max(1, leaders.size())) + "</bold>\n\n");
                    // Own placement header
                    sb.append("<#FFFFFF>Deine Platzierung: <gold><bold>#" + ownPlace + "</bold> <#55FF55>" + ownName + "</#55FF55> <#AAAAAA>" + String.format("%dm", (int)Math.round(ownScore)) + "\n\n");

                    // Top list
                    for (int i = 0; i < top.size(); i++) {
                        Leader l = top.get(i);
                        String num = "#" + (i+1);
                        if (i < 3) {
                            // top3: light green + bold
                            sb.append("<#55FF55><bold>" + num + " " + l.displayName + "</bold></#55FF55> <#AAAAAA>" + String.format("%dm", (int)Math.round(l.score)) + "\n");
                        } else {
                            sb.append("<#FFFFFF>" + num + " " + l.displayName + " <#AAAAAA>" + String.format("%dm", (int)Math.round(l.score)) + "\n");
                        }
                    }

                    sb.append("\n<#FFD700><bold>━━━━━━━━━━━━━━━━━━━━━━━━</bold>\n");
                    sb.append("<#AAAAAA>Danke fürs Teilnehmen!\n");

                    String reason = sb.toString();
                    if (p != null && p.isOnline()) {
                        try { p.kick(c(reason)); } catch (Exception ignored) {}
                    }
                }
                resetGame();
            }
        }.runTaskLater(plugin, 20L * 5);
    }

    // Reset player data completely so run-bans are lifted and players can rejoin after the game ends
    private void resetGame() {
        participants.clear();
        state = GameState.LOBBY;
        secondsElapsed = 0;
        hostileActivated = false;
        pauseActive = false;
        pauseStarting = false;
        pauseLeftSeconds = 0;
        pauseTotalSeconds = 0;
        pausePreLeftSeconds = 0;
        pauseSeconds = 0;

        // Reset player data completely so run-bans are lifted and players can rejoin after the game ends
        plugin.getPlayerDataManager().reset();
    }

    public void shutdown() {
        if (gameTask != null) { gameTask.cancel(); gameTask = null; }
        if (hostileMobTask != null) { hostileMobTask.cancel(); hostileMobTask = null; }
        if (lobbyActionBarTask != null) { lobbyActionBarTask.cancel(); lobbyActionBarTask = null; }
        if (runningActionBarTask != null) { runningActionBarTask.cancel(); runningActionBarTask = null; }
        // Ensure players are unfrozen if plugin shuts down during pause
        if (pauseActive) {
            pauseActive = false;
            for (Player p : Bukkit.getOnlinePlayers()) {
                try {
                    p.setWalkSpeed(0.2f);
                    p.setFlySpeed(0.1f);
                    p.setInvulnerable(false);
                    p.sendActionBar(c("<green><bold>Timer läuft weiter!</bold>"));
                } catch (Exception ignored) {}
            }
        }
        try { plugin.getBossBarManager().removeAll(); } catch (Exception ignored) {}
    }
}
