package org.emrage.pvgrun.managers;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.emrage.pvgrun.Main;

import java.util.*;

import static org.emrage.pvgrun.util.MessageUtils.c;

public class BossBarManager {

    private final Main plugin;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, String> last = new HashMap<>();
    // expiry timestamp (ms) until which a join message should remain visible for a player
    private final Map<UUID, Long> joinMessageExpires = new HashMap<>();

    // Secondary bars used to show the lobby/waiting message underneath join messages.
    private final Map<UUID, BossBar> lobbyBars = new HashMap<>();

    private final Queue<JoinBarEntry> joinQueue = new LinkedList<>();
    private boolean joinBarActive = false;
    private static class JoinBarEntry {
        final String playerName;
        final int online;
        final int max;
        JoinBarEntry(String playerName, int online, int max) {
            this.playerName = playerName;
            this.online = online;
            this.max = max;
        }
    }

    public BossBarManager(Main plugin) {
        this.plugin = plugin;
    }

    private BossBar createLobbyBar() {
        return BossBar.bossBar(c("<gray>Warte auf weitere Spieler...</gray>"), 1f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
    }

    public void showLobbyBarFor(Player p) {
        if (lobbyBars.containsKey(p.getUniqueId())) return;
        BossBar lb = createLobbyBar();
        lobbyBars.put(p.getUniqueId(), lb);
        try { p.showBossBar(lb); } catch (Exception ignored) {}
    }

    public void hideLobbyBarFor(Player p) {
        BossBar lb = lobbyBars.remove(p.getUniqueId());
        if (lb != null) {
            try { p.hideBossBar(lb); } catch (Exception ignored) {}
        }
    }

    public void addAllForActive() {
        // Ensure existing bars are re-shown (e.g., if they were hidden while in LOBBY)
        for (Player p : plugin.getGameManager().activePlayers()) {
            if (bars.containsKey(p.getUniqueId())) {
                BossBar bar = bars.get(p.getUniqueId());
                if (bar != null) p.showBossBar(bar);
                updateFor(p);
            } else {
                addFor(p);
            }
        }
        for (Player p : plugin.getGameManager().excludedPlayers()) {
            if (bars.containsKey(p.getUniqueId())) {
                BossBar bar = bars.get(p.getUniqueId());
                if (bar != null) p.showBossBar(bar);
                updateForExcluded(p);
            } else {
                addForExcluded(p);
            }
        }
    }

    public void addFor(Player p) {
        if (bars.containsKey(p.getUniqueId())) return;
        BossBar bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
        bars.put(p.getUniqueId(), bar);
        p.showBossBar(bar);
        updateFor(p);
        plugin.getLogger().info("[BossBar] added for " + p.getName());
    }

    public void addForExcluded(Player p) {
        if (bars.containsKey(p.getUniqueId())) return;
        BossBar bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
        bars.put(p.getUniqueId(), bar);
        p.showBossBar(bar);
        updateForExcluded(p);
        plugin.getLogger().info("[BossBar] added (excluded) for " + p.getName());
    }

    // Added per request: unified public method used by listeners/main when a player joins
    public void addForPlayer(Player p) {
        if (plugin.getPlayerDataManager().isExcluded(p)) {
            addForExcluded(p);
        } else {
            addFor(p);
        }
    }

    public void removePlayer(Player p) {
        BossBar b = bars.remove(p.getUniqueId());
        last.remove(p.getUniqueId());
        joinMessageExpires.remove(p.getUniqueId());
        if (b != null) {
            p.hideBossBar(b);
            plugin.getLogger().info("[BossBar] removed for " + p.getName());
        }
    }

    public void removeAll() {
        bars.forEach((u, b) -> {
            Player pl = plugin.getServer().getPlayer(u);
            if (pl != null) pl.hideBossBar(b);
        });
        bars.clear();
        last.clear();
        joinMessageExpires.clear();
    }

    // Hide and remove all lobby bars (call when leaving LOBBY/PREPARING)
    public void hideAllLobbyBars() {
        for (UUID u : new java.util.ArrayList<>(lobbyBars.keySet())) {
            Player p = plugin.getServer().getPlayer(u);
            BossBar lb = lobbyBars.remove(u);
            if (p != null && lb != null) {
                try { p.hideBossBar(lb); } catch (Exception ignored) {}
            }
        }
    }


    public void updateAll() {
        plugin.getGameManager().activePlayers().forEach(this::updateFor);
        plugin.getGameManager().excludedPlayers().forEach(this::updateForExcluded);
    }

    private String formatRemaining(int remaining) {
        int hours = remaining / 3600;
        int minutes = (remaining % 3600) / 60;
        int seconds = remaining % 60;
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    public void updateFor(Player p) {
        // If this player currently has a join-message active (not expired), don't overwrite the title
        Long expiry = joinMessageExpires.get(p.getUniqueId());
        boolean joinActive = expiry != null && System.currentTimeMillis() < expiry;

        // Do not show timer in LOBBY/PREPARING state; show only a neutral message instead
         var gm = plugin.getGameManager();
        if (gm != null && (gm.getState() == org.emrage.pvgrun.enums.GameState.LOBBY || gm.getState() == org.emrage.pvgrun.enums.GameState.PREPARING)) {
            // Ensure persistent lobby bar is shown and timer bar is hidden while in LOBBY/PREPARING.
            // Do not hide the lobby bar when join messages start/stop — keep it visible permanently during LOBBY.
            try { showLobbyBarFor(p); } catch (Exception ignored) {}
            // Hide the regular timer bar if present so only lobby bar is visible
            BossBar timerBar = bars.get(p.getUniqueId());
            if (timerBar != null) {
                try { p.hideBossBar(timerBar); } catch (Exception ignored) {}
            }
            return;
        }

        double own = plugin.getPlayerDataManager().getStoredDistance(p.getName());
        int heartsLost = plugin.getPlayerDataManager().totalHeartsLost();
        int dead = plugin.getPlayerDataManager().deadCount();
        int online = plugin.getServer().getOnlinePlayers().size();
        double totalDist = plugin.getPlayerDataManager().getAllStoredDistancesSum();
        int remaining = Math.max(0, plugin.getGameManager().getMaxSeconds() - plugin.getGameManager().getSecondsElapsed());
        String time = formatRemaining(remaining);

        // Title: only timer in the middle, no emojis or extra symbols
        // Use a gradient for the timer to look smooth: #0080ff -> #00d4ff
        Component composedTimer = c(String.format("<gradient:#0080ff:#00d4ff><bold>%s</bold></gradient>", time));

        BossBar bar = bars.get(p.getUniqueId());
        if (bar != null) {
            // If pause pre-starting (countdown to pause), show pre-pause countdown
            if (gm != null && gm.isPauseStarting()) {
                if (!joinActive) {
                    int preLeft = gm.getPausePreLeftSeconds();
                    String t = formatRemaining(preLeft);
                    bar.name(c(String.format("<gold><bold>Pause in %s</bold></gold>", t)));
                    // progress: fraction of 5s elapsed
                    float prog = Math.max(0f, Math.min(1f, (float)preLeft / 5f));
                    bar.progress(prog);
                    bar.color(BossBar.Color.YELLOW);
                }
                return;
            }

            // If pause is active, show PAUSE instead of the timer
            if (plugin.getGameManager().isPauseActive()) {
                // If a join message is active, do not overwrite it
                if (!joinActive) {
                    int left = plugin.getGameManager().getPauseLeftSeconds();
                    int total = plugin.getGameManager().getPauseTotalSeconds();
                    String t = formatRemaining(left);
                    // show countdown as timer instead of plain text
                    bar.name(c(String.format("<gold><bold>%s</bold></gold>", t)));
                    // progress represents fraction of pause remaining
                    if (total > 0) bar.progress(Math.max(0f, Math.min(1f, (float) left / (float) total)));
                    bar.color(BossBar.Color.YELLOW);
                }
            } else {
                String key = time + ":" + own + ":" + heartsLost + ":" + dead + ":" + online + ":" + totalDist;
                String prev = last.get(p.getUniqueId());
                // Only update name if join message not active (or expired) and key changed
                if (!joinActive && !key.equals(prev)) {
                    bar.name(composedTimer);
                    last.put(p.getUniqueId(), key);
                }
                float progress = Math.max(0f, Math.min(1f, (float) remaining / plugin.getGameManager().getMaxSeconds()));
                bar.progress(progress);
                bar.color(BossBar.Color.PURPLE);
            }
        }
    }

    public void updateForExcluded(Player p) {
        Long expiry = joinMessageExpires.get(p.getUniqueId());
        boolean joinActive = expiry != null && System.currentTimeMillis() < expiry;
        var gm = plugin.getGameManager();
        if (gm != null && (gm.getState() == org.emrage.pvgrun.enums.GameState.LOBBY || gm.getState() == org.emrage.pvgrun.enums.GameState.PREPARING)) {
            BossBar barLobby = bars.get(p.getUniqueId());
            if (barLobby != null) {
                if (joinActive) {
                    // keep join message
                } else {
                    try { p.hideBossBar(barLobby); } catch (Exception ignored) {}
                }
            }
            return;
        }
        int remaining = Math.max(0, plugin.getGameManager().getMaxSeconds() - plugin.getGameManager().getSecondsElapsed());
        String time = formatRemaining(remaining);
        Component composedTimer = c(String.format("<gradient:#0080ff:#00d4ff><bold>%s</bold></gradient>", time));
        BossBar bar = bars.get(p.getUniqueId());
        if (bar != null) {
            // pre-pause countdown
            if (gm != null && gm.isPauseStarting()) {
                if (!joinActive) {
                    int preLeft = gm.getPausePreLeftSeconds();
                    String t = formatRemaining(preLeft);
                    bar.name(c(String.format("<gold><bold>Pause in %s</bold></gold>", t)));
                    float prog = Math.max(0f, Math.min(1f, (float)preLeft / 5f));
                    bar.progress(prog);
                    bar.color(BossBar.Color.YELLOW);
                }
                return;
            }

            if (plugin.getGameManager().isPauseActive()) {
                if (!joinActive) {
                    int left = plugin.getGameManager().getPauseLeftSeconds();
                    int total = plugin.getGameManager().getPauseTotalSeconds();
                    String t = formatRemaining(left);
                    bar.name(c(String.format("<gold><bold>%s</bold></gold>", t)));
                    if (total > 0) bar.progress(Math.max(0f, Math.min(1f, (float) left / (float) total)));
                    bar.color(BossBar.Color.YELLOW);
                }
            } else {
                String prev = last.get(p.getUniqueId());
                if (!joinActive && !time.equals(prev)) {
                    bar.name(composedTimer);
                    last.put(p.getUniqueId(), time);
                }
                float progress = Math.max(0f, Math.min(1f, (float) remaining / plugin.getGameManager().getMaxSeconds()));
                bar.progress(progress);
                bar.color(BossBar.Color.PURPLE);
            }
        }
    }

    public void queueJoinMessage(Player p) {
        plugin.getLogger().info("[BossBar] queueJoinMessage for " + p.getName());
        joinQueue.add(new JoinBarEntry(p.getName(), Bukkit.getOnlinePlayers().size(), Bukkit.getMaxPlayers()));
        if (!joinBarActive) {
            showNextJoinBar();
        }
    }
    private void showNextJoinBar() {
        // Do not show join bars if game state is not LOBBY. Clear queue to avoid later display.
        var gm = plugin.getGameManager();
        if (gm == null || gm.getState() != org.emrage.pvgrun.enums.GameState.LOBBY) {
            joinQueue.clear();
            joinBarActive = false;
            return;
        }

        JoinBarEntry entry = joinQueue.poll();
        if (entry == null) {
            joinBarActive = false;
            // No more join bars queued -> remove all lobby bars (they should only be visible during join messages)
            return;
        }
        joinBarActive = true;
        plugin.getLogger().info("[BossBar] showing join bar for " + entry.playerName);
        // Use gradient on player name to match timer gradient and avoid color jumps
        Component msg = c("<white>" + "<gradient:#0080ff:#00d4ff><bold>" + entry.playerName + "</bold></gradient> <gray>ist beigetreten <white>(" + entry.online + "/" + entry.max + ")");
        final long expiry = System.currentTimeMillis() + 5000L; // 5 Sekunden in ms

        // Create temporary per-player join bars and show lobby bars underneath
        Map<UUID, BossBar> joinBars = new HashMap<>();
        for (Player pl : Bukkit.getOnlinePlayers()) {
            BossBar jb = BossBar.bossBar(msg, 1f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
            joinBars.put(pl.getUniqueId(), jb);
            try { pl.showBossBar(jb); } catch (Exception ignored) {}
            // ensure lobby bar underneath while join animation runs
            showLobbyBarFor(pl);
            joinMessageExpires.put(pl.getUniqueId(), expiry);
        }

        // Animate progress from 1.0 -> 0.0 over 100 ticks (5 seconds)
        final int totalTicks = 100;
        BukkitRunnable task = new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                // If the game state changed (e.g., game started) stop the animation and clear queue
                var gm = plugin.getGameManager();
                if (gm == null || gm.getState() != org.emrage.pvgrun.enums.GameState.LOBBY) {
                    // cleanup temp join bars and lobby bars
                    for (UUID u : new java.util.ArrayList<>(joinBars.keySet())) {
                        Player p = Bukkit.getPlayer(u);
                        BossBar jb = joinBars.remove(u);
                        if (p != null && jb != null) try { p.hideBossBar(jb); } catch (Exception ignored) {}
                        joinMessageExpires.remove(u);
                        // Ensure the persistent lobby bar is visible again after hiding the temporary join bar
                        if (p != null) try { showLobbyBarFor(p); } catch (Exception ignored) {}
                    }
                    joinQueue.clear();
                    joinBarActive = false;
                    this.cancel();
                    return;
                }

                tick++;
                float progress = Math.max(0f, Math.min(1f, (float)(totalTicks - tick) / (float) totalTicks));
                for (UUID u : new java.util.ArrayList<>(joinBars.keySet())) {
                    Player pl = Bukkit.getPlayer(u);
                    BossBar jb = joinBars.get(u);
                    if (pl != null && jb != null) {
                        jb.progress(progress);
                    } else {
                        joinBars.remove(u);
                        joinMessageExpires.remove(u);
                    }
                }

                if (tick >= totalTicks) {
                    // Finish: hide join bars and clear join markers, optionally fade into timer or remove lobby bars
                    for (UUID u : new java.util.ArrayList<>(joinBars.keySet())) {
                        Player p = Bukkit.getPlayer(u);
                        BossBar jb = joinBars.remove(u);
                        if (p != null && jb != null) try { p.hideBossBar(jb); } catch (Exception ignored) {}
                        joinMessageExpires.remove(u);
                        // Re-show the persistent lobby bar to avoid client-side flicker
                        if (p != null) try { showLobbyBarFor(p); } catch (Exception ignored) {}
                    }

                    // Advance to next queued join, and if none remain, remove lobby bars
                    this.cancel();
                    showNextJoinBar();
                }
            }
        };
        task.runTaskTimer(plugin, 1L, 1L);
    }
}
