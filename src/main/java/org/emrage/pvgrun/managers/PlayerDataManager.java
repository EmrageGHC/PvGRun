package org.emrage.pvgrun.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.emrage.pvgrun.Main;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager {

    private final Main plugin;

    public PlayerDataManager(Main plugin) {
        this.plugin=plugin;
    }

    private final Set<String> excludedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<String> deadPlayers = ConcurrentHashMap.newKeySet();
    private final Set<String> runBannedPlayers = ConcurrentHashMap.newKeySet();
    private final Map<String, Double> bestDistances = new ConcurrentHashMap<>();
    private final Map<String, Integer> heartsLostByPlayer = new ConcurrentHashMap<>();
    private int totalHeartsLost = 0;

    public void exclude(Player p) { excludedPlayers.add(p.getName());Bukkit.getOnlinePlayers().forEach(plugin::updateVisibility); }
    public void unexclude(Player p) { excludedPlayers.remove(p.getName());Bukkit.getOnlinePlayers().forEach(plugin::updateVisibility); }
    public boolean isExcluded(Player p) { return excludedPlayers.contains(p.getName()); }
    public Set<String> getExcludedPlayers() { return new HashSet<>(excludedPlayers); }

    public void markDead(Player p) { deadPlayers.add(p.getName());Bukkit.getOnlinePlayers().forEach(plugin::updateVisibility); }
    public boolean isDead(Player p) { return deadPlayers.contains(p.getName()); }
    public Set<String> getDeadPlayers() { return new HashSet<>(deadPlayers); }

    public void addRunBan(String name) { if (name != null) runBannedPlayers.add(name);Bukkit.getOnlinePlayers().forEach(plugin::updateVisibility); }
    public boolean isRunBanned(String name) { return name != null && runBannedPlayers.contains(name); }
    public void removeRunBan(String name) { if (name != null) runBannedPlayers.remove(name);Bukkit.getOnlinePlayers().forEach(plugin::updateVisibility); }
    public void unbanAllRunBans() { runBannedPlayers.clear(); }
    public Set<String> getRunBannedPlayers() { return new HashSet<>(runBannedPlayers); }

    public void addHeartsLost(String playerName, int hearts) {
        if (hearts <= 0) return;
        heartsLostByPlayer.merge(playerName, hearts, Integer::sum);
        totalHeartsLost += hearts;
    }
    public int getHeartsLost(String playerName) { return heartsLostByPlayer.getOrDefault(playerName, 0); }
    public int totalHeartsLost() { return totalHeartsLost; }

    public int deadCount() { return deadPlayers.size(); }
    public int aliveCount() {
        return (int) org.bukkit.Bukkit.getOnlinePlayers().stream()
                .filter(p -> !isExcluded(p))
                .filter(p -> !isDead(p))
                .count();
    }

    public void updateDistance(Player p, double dist) {
        if (p == null || dist <= 0) return;
        bestDistances.merge(p.getName(), dist, Math::max);
    }

    public double getStoredDistance(String playerName) { return bestDistances.getOrDefault(playerName, 0.0); }

    public double getAllStoredDistancesSum() {
        return bestDistances.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    /**
     * Return top names by stored distance (includes offline / dead players as long as they have stored distance).
     */
    public List<String> topNames(int n) {
        List<Map.Entry<String, Double>> entries = new ArrayList<>(bestDistances.entrySet());
        entries.sort((a,b) -> Double.compare(b.getValue(), a.getValue()));
        List<String> out = new ArrayList<>();
        if (n <= 0) {
            for (Map.Entry<String, Double> e : entries) out.add(e.getKey());
            return out;
        }
        for (int i = 0; i < Math.min(n, entries.size()); i++) out.add(entries.get(i).getKey());
        return out;
    }

    /**
     * Determine placement among known stored distances.
     * If player not present, return size+1.
     */
    public int getPlacement(Player p) {
        List<Map.Entry<String, Double>> entries = new ArrayList<>(bestDistances.entrySet());
        entries.sort((a,b) -> Double.compare(b.getValue(), a.getValue()));
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).getKey().equals(p.getName())) return i+1;
        }
        return entries.size() + 1;
    }

    public void reset() {
        excludedPlayers.clear();
        deadPlayers.clear();
        bestDistances.clear();
        heartsLostByPlayer.clear();
        totalHeartsLost = 0;
        runBannedPlayers.clear();
    }

    /**
     * Reset game data but keep run-bans (they persist until server restart)
     */
    public void resetExceptRunBans() {
        excludedPlayers.clear();
        deadPlayers.clear();
        bestDistances.clear();
        heartsLostByPlayer.clear();
        totalHeartsLost = 0;
        // keep runBannedPlayers
    }

    public boolean isInGame(Player p) {
        return !isExcluded(p) && !isDead(p);
    }
}