package org.emrage.pvgrun.managers;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.emrage.pvgrun.Main;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.emrage.pvgrun.util.MessageUtils.c;

public class TablistManager {

    private final Main plugin;
    private BukkitRunnable updateTask;

    // Store previous playerlist names so we can restore them
    private final Map<UUID, String> savedListNames = new HashMap<>();

    public TablistManager(Main plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (updateTask != null) updateTask.cancel();
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    updateFor(p);
                }
            }
        };
        updateTask.runTaskTimer(plugin, 0L, 40L); // update every 2 seconds
    }

    public void updateFor(Player player) {

        // Simple solid colors, no gradient
        var header = c("\n<#FFD700><bold>DEATHRUN</bold>\n ");
        var footer = c("\n<#888888>#GHC\n ");
        player.sendPlayerListHeaderAndFooter(header, footer);

        try {
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (target.getGameMode() == GameMode.SPECTATOR) {
                    removeFromTablist(target);
                } else {
                    // Team-Prefix für Dimension-Modus
                    String displayName = target.getName();
                    if (plugin.getConfigManager().getGameMode().equals("dimension") && plugin.getTeamManager().hasTeam(target)) {
                        var team = plugin.getTeamManager().getTeam(target);
                        String prefix = "§7[§a§l" + team.getName() + "§7]§r ";
                        displayName = prefix + target.getName();
                    }
                    target.setPlayerListName(displayName);
                }
            }
        } catch (Exception ignored) {
            // prevent task cancellation on unexpected errors
        }
    }

    private void removeFromTablist(Player target) {
        UUID id = target.getUniqueId();
        if (!savedListNames.containsKey(id)) {
            try {
                savedListNames.put(id, target.getPlayerListName());
            } catch (Exception e) {
                // Some implementations may throw; fallback to storing the real name
                savedListNames.put(id, target.getName());
            }
        }
        try {
            target.setPlayerListName("");
        } catch (Exception ignored) {
        }
    }

    private void restoreToTablist(Player target) {
        UUID id = target.getUniqueId();
        if (!savedListNames.containsKey(id)) return;
        String prev = savedListNames.remove(id);
        try {
            if (prev == null || prev.isEmpty()) {
                target.setPlayerListName(target.getName());
            } else {
                target.setPlayerListName(prev);
            }
        } catch (Exception ignored) {
        }
    }

    public void shutdown() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        // Restore all saved list names
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                if (savedListNames.containsKey(p.getUniqueId())) {
                    String prev = savedListNames.remove(p.getUniqueId());
                    if (prev == null || prev.isEmpty()) p.setPlayerListName(p.getName());
                    else p.setPlayerListName(prev);
                }
                p.sendPlayerListHeaderAndFooter(c(""), c(""));
            } catch (Exception ignored) {}
        }
        savedListNames.clear();
    }
}