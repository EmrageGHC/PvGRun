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

    private final Set<UUID> participants = Collections.synchronizedSet(new LinkedHashSet<>());

    private static final double LOBBY_WORLDBORDER_SIZE = 100.0;
    private static final double GAME_WORLDBORDER_SIZE = 600_000.0;

    private int countdownSeconds = 60;
    private boolean pauseActive = false;
    private int pauseSeconds = 0;
    private int pauseCountdown = 0;

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

                // Simple gray text, no animation
                Component bar = c("<gray>Warte auf weitere Spieler... </gray>");
                for (Player p : Bukkit. getOnlinePlayers()) {
                    p.sendActionBar(bar);
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
                    int place = plugin.getPlayerDataManager().getPlacement(p);
                    p.sendActionBar(c("<#0080ff>#" + place));
                }
            }
        };
        runningActionBarTask.runTaskTimer(plugin, 0L, 10L);
        plugin.getLogger().info("[ActionBar] running actionbar task started (shows #placement every 0.5s)");
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
        if (lobbyActionBarTask != null) { lobbyActionBarTask.cancel(); lobbyActionBarTask = null; }
        participants.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!plugin.getPlayerDataManager().isExcluded(p)) participants.add(p.getUniqueId());
        }
        loadConfigValues();
        // Add bossbars for all active/excluded now (prepare phase)
        plugin.getBossBarManager().addAllForActive();
        // Dimension Deathrun Logik
        if (plugin.getConfigManager().getGameMode().equals("dimension")) {
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
                // Teleportiere alle Spieler in ihre Dimension
                for (UUID uuid : team.getMembers()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null) continue;
                    String dim = team.getPlayerDimension(uuid);
                    World world = Bukkit.getWorld(dim != null && dim.equals("nether") ? "world_nether" : "world");
                    Location spawn = getSafeSpawn(world, dim != null ? dim : "overworld");
                    p.teleport(spawn);
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
        wb.setSize(10000000.0); // Sehr groß, damit keine Begrenzung
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
            int titleSeconds = 5;
            @Override
            public void run() {
                if (titleSeconds == 0) {
                    // Visuelle Borders aktivieren (Ost/West) nach Start
                    if (plugin.getBorderManager() != null) {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            plugin.getBorderManager().checkPlayer(p);
                        }
                    }
                    beginRunning();
                    cancel();
                    return;
                }
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.showTitle(Title.title(
                        c("<yellow>Start in " + titleSeconds),
                        Component.empty(),
                        Title.Times.times(Duration.ofMillis(0), Duration.ofMillis(1000), Duration.ofMillis(0))
                    ));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
                }
                // Update bossbars as title counts down so players see the timer in the bossbar too
                try { plugin.getBossBarManager().updateAll(); } catch (Exception ignored) {}
                titleSeconds--;
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
                // Actionbar: Event startet in Kürze
                Component bar = c("<green><bold>DAS EVENT STARTET IN KÜRZE</bold>");
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
                secondsElapsed++;
                // Pausenmechanik
                if (MAX_SECONDS >= 7200 && secondsElapsed == 3600) {
                    startPause();
                }
                if (!pauseActive) {
                    try {
                        plugin.getScoreboardManager().updateAllForActive();
                    } catch (Exception ignored) {}
                    // Update bossbars each tick second as well
                    try {
                        plugin.getBossBarManager().updateAll();
                    } catch (Exception ignored) {}
                }
                if (secondsElapsed >= MAX_SECONDS) endGame();
            }
        };
        gameTask.runTaskTimer(plugin, 20L, 20L);

        hostileMobTask = new BukkitRunnable() {
            @Override
            public void run() {
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
                Bukkit.broadcast(c("<#FF5555><bold>⚠ Achtung!</bold> Feindliche Mobs spawnen jetzt! "));
            }
        };
        hostileMobTask.runTaskLater(plugin, 20L * MOB_SPAWN_SECONDS);
    }

    private void startPause() {
        pauseActive = true;
        pauseSeconds = 600;
        pauseCountdown = 5;
        new BukkitRunnable() {
            @Override
            public void run() {
                if (pauseCountdown > 0) {
                    Component title = c("<yellow><bold>Pause in " + pauseCountdown + " Sekunden!");
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.showTitle(Title.title(title, Component.empty(), Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(900), Duration.ofMillis(300))));
                    }
                    pauseCountdown--;
                } else {
                    // Pause beginnt
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendActionBar(c("<yellow><bold>PAUSE</bold>"));
                        p.setWalkSpeed(0f);
                        p.setFlySpeed(0f);
                        p.setInvulnerable(true);
                    }
                    new BukkitRunnable() {
                        int pauseLeft = pauseSeconds;
                        int endCountdown = 10;
                        boolean endPhase = false;
                        @Override
                        public void run() {
                            if (!endPhase && pauseLeft <= 10) {
                                endPhase = true;
                            }
                            if (endPhase && endCountdown > 0) {
                                Component title = c("<green><bold>Pause endet in " + endCountdown + " Sekunden!");
                                for (Player p : Bukkit.getOnlinePlayers()) {
                                    p.showTitle(Title.title(title, Component.empty(), Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(900), Duration.ofMillis(300))));
                                }
                                endCountdown--;
                            } else if (pauseLeft <= 0) {
                                // Pause vorbei
                                pauseActive = false;
                                for (Player p : Bukkit.getOnlinePlayers()) {
                                    p.sendActionBar(c("<green><bold>Timer läuft weiter!</bold>"));
                                    p.setWalkSpeed(0.2f);
                                    p.setFlySpeed(0.1f);
                                    p.setInvulnerable(false);
                                }
                                cancel();
                                return;
                            }
                            pauseLeft--;
                        }
                    }.runTaskTimer(plugin, 0L, 20L);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public synchronized void endGame() {
        if (state != GameState.RUNNING) return;
        state = GameState.ENDING;

        if (gameTask != null) { gameTask.cancel(); gameTask = null; }
        if (hostileMobTask != null) { hostileMobTask.cancel(); hostileMobTask = null; }
        if (runningActionBarTask != null) { runningActionBarTask.cancel(); runningActionBarTask = null; }

        List<String> top = plugin.getPlayerDataManager().topNames(5);
        Bukkit.broadcast(c("\n<#FFD700><bold>━━━ DEATHRUN TOP 5 ━━━</bold>"));
        for (int i = 0; i < top.size(); i++) {
            String icon = i == 0 ? "👑" : i == 1 ? "🥈" : i == 2 ?  "🥉" : "◆";
            String color = i == 0 ? "<#FFD700>" : i == 1 ? "<#C0C0C0>" : i == 2 ? "<#CD7F32>" : "<#FFFFFF>";
            Bukkit. broadcast(c(color + icon + " <bold>#" + (i+1) + "</bold> " + top.get(i)));
        }
        Bukkit.broadcast(c("<#FFD700><bold>━━━━━━━━━━━━━━━━━━━</bold>\n"));

        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID u : new HashSet<>(participants)) {
                    Player p = Bukkit. getPlayer(u);
                    double dist = 0;
                    if (p != null) dist = plugin.getPlayerDataManager().getStoredDistance(p. getName());
                    int distInt = (int) Math.round(dist);
                    String reason = "<#FFD700><bold>Der Deathrun ist vorbei!</bold>\n<#AAAAAA>Dein Score:  <#55FF55>" + distInt + "m";
                    if (p != null && p.isOnline()) p.kick(c(reason));
                }
                resetGame();
            }
        }.runTaskLater(plugin, 20L * 5);
    }

    private void resetGame() {
        state = GameState.LOBBY;
        secondsElapsed = 0;
        participants.clear();
        plugin.getPlayerDataManager().resetExceptRunBans();
        plugin.getScoreboardManager().removeAll();
        plugin.getBorderManager().removeAllDisplays();
        plugin.getTeamManager().getTeams().clear();
        plugin.getTeamManager().getPlayerToTeamMap().clear();
        plugin.getTeamBackpackManager().clear();
        // remove bossbars too
        plugin.getBossBarManager().removeAll();
        startLobbyActionBar();

        // Restart Twitch chat display in lobby
        if (plugin.getTwitchChatManager() != null && !plugin.getTwitchChatManager().isRunning()) {
            World world = Bukkit.getWorld("world");
            if (world != null) {
                Location loc = new Location(world, 0, 100, 0);
                plugin.getTwitchChatManager().start("HardcorePvG", loc);
            }
        }

        World world = Bukkit.getWorld("world");
        if (world != null) {
            world. setGameRule(GameRule.DO_MOB_SPAWNING, false);
            WorldBorder wb = world.getWorldBorder();
            wb.setCenter(0,0);
            wb.setSize(LOBBY_WORLDBORDER_SIZE);
            wb.setWarningDistance(5);
            wb.setDamageAmount(0);
        }
    }

    public void shutdown() {
        if (gameTask != null) gameTask.cancel();
        if (hostileMobTask != null) hostileMobTask.cancel();
        if (lobbyActionBarTask != null) lobbyActionBarTask.cancel();
        if (runningActionBarTask != null) runningActionBarTask.cancel();
        plugin.getScoreboardManager().removeAll();
    }

    public List<Player> activePlayers() {
        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> !plugin.getPlayerDataManager().isExcluded(p))
                .filter(p -> ! plugin.getPlayerDataManager().isDead(p))
                .collect(Collectors.toList());
    }

    public List<Player> excludedPlayers() {
        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> plugin.getPlayerDataManager().isExcluded(p))
                .collect(Collectors.toList());
    }

    public boolean wasParticipant(UUID uuid) {
        return participants.contains(uuid);
    }

    public void checkForWinner() {
        if (state != GameState.RUNNING) return;
        List<Player> active = activePlayers();
        if (active.isEmpty()) {
            endGame();
        }
    }

    public GameState getState() { return state; }
    public int getSecondsElapsed() { return secondsElapsed; }
    public int getMaxSeconds() { return MAX_SECONDS; }
    public int getCountdownSeconds() { return countdownSeconds; }
    public boolean isPauseActive() { return pauseActive; }
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
}

