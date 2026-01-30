package org.emrage.pvgrun.managers;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.emrage.pvgrun.Main;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

import static org.emrage.pvgrun.util.MessageUtils.c;

public class BossBarManager {

    private final Main plugin;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, String> last = new HashMap<>();

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

    public void addAllForActive() {
        plugin.getGameManager().activePlayers().forEach(this::addFor);
        plugin.getGameManager().excludedPlayers().forEach(this::addForExcluded);
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
    }

    public void updateAll() {
        plugin.getGameManager().activePlayers().forEach(this::updateFor);
        plugin.getGameManager().excludedPlayers().forEach(this::updateForExcluded);
    }

    public void updateFor(Player p) {
        double own = plugin.getPlayerDataManager().getStoredDistance(p.getName());
        int heartsLost = plugin.getPlayerDataManager().totalHeartsLost();
        int dead = plugin.getPlayerDataManager().deadCount();
        int online = plugin.getServer().getOnlinePlayers().size();
        double totalDist = plugin.getPlayerDataManager().getAllStoredDistancesSum();
        int remaining = Math.max(0, plugin.getGameManager().getMaxSeconds() - plugin.getGameManager().getSecondsElapsed());
        String time = String.format("%02d:%02d:%02d", remaining / 3600, (remaining % 3600) / 60, remaining % 60);

        Component composed = Component.empty()
                .append(c(String.format("<gradient:#0080ff:#00d4ff>⏱ <bold>%s</bold>", time)))
                .append(Component.space())
                .append(c(String.format("<#55FF55>⬆ <bold>%s</bold>m", String.format("%.0f", own))))
                .append(Component.space())
                .append(c(String.format("<#FF5555>❤ <bold>%d</bold>", heartsLost)))
                .append(Component.space())
                .append(c(String.format("<#FFFFFF>👥 <bold>%d</bold>", online)))
                .append(Component.space())
                .append(c(String.format("<#FFD700>Σ <bold>%s</bold>m", String.format("%.0f", totalDist))));

        BossBar bar = bars.get(p.getUniqueId());
        if (bar != null) {
            String key = time + ":" + own + ":" + heartsLost + ":" + dead + ":" + online + ":" + totalDist;
            String prev = last.get(p.getUniqueId());
            if (!key.equals(prev)) {
                bar.name(composed);
                last.put(p.getUniqueId(), key);
            }
            float progress = Math.max(0f, Math.min(1f, (float) remaining / plugin.getGameManager().getMaxSeconds()));
            bar.progress(progress);
        }
    }

    public void updateForExcluded(Player p) {
        int remaining = Math.max(0, plugin.getGameManager().getMaxSeconds() - plugin.getGameManager().getSecondsElapsed());
        String time = String.format("%02d:%02d:%02d", remaining / 3600, (remaining % 3600) / 60, remaining % 60);
        Component composed = c(String.format("<#AA00FF>⏱ <bold>%s</bold>", time));

        BossBar bar = bars.get(p.getUniqueId());
        if (bar != null) {
            String prev = last.get(p.getUniqueId());
            if (!time.equals(prev)) {
                bar.name(composed);
                last.put(p.getUniqueId(), time);
            }
            float progress = Math.max(0f, Math.min(1f, (float) remaining / plugin.getGameManager().getMaxSeconds()));
            bar.progress(progress);
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
        JoinBarEntry entry = joinQueue.poll();
        if (entry == null) {
            joinBarActive = false;
            return;
        }
        joinBarActive = true;
        plugin.getLogger().info("[BossBar] showing join bar for " + entry.playerName);
        Component msg = c("<white><bold>" + entry.playerName + "</bold> <gray>ist beigetreten <white>(<#ff69b4>" + entry.online + "</#ff69b4><gray>/</gray><#ff69b4>" + entry.max + "</#ff69b4><white>)");
        for (Player pl : Bukkit.getOnlinePlayers()) {
            BossBar bar = bars.get(pl.getUniqueId());
            if (bar != null) {
                bar.name(msg);
                bar.color(BossBar.Color.WHITE);
                bar.overlay(BossBar.Overlay.PROGRESS);
                bar.progress(1f);
            }
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                showNextJoinBar();
            }
        }.runTaskLater(plugin, 100L); // 5 Sekunden
    }
}