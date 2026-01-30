package org.emrage.pvgrun.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.emrage.pvgrun.Main;

import static org.emrage.pvgrun.util.MessageUtils.c;

public class PlayerKickListener implements Listener {

    private final Main plugin;

    public PlayerKickListener(Main plugin) { this.plugin = plugin; }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        Player p = event.getPlayer();
        String reason = event.getReason();

        var gm = plugin.getGameManager();
        if (gm != null && gm.getState() == org.emrage.pvgrun.enums.GameState.RUNNING) {
            boolean wasParticipant = gm.wasParticipant(p.getUniqueId());
            // save distance on kick
            Location spawn = gm.getSpawnLocation();
            double dist = Math.max(0.0, spawn.getZ() - p.getLocation().getZ());
            if (dist > 0) plugin.getPlayerDataManager().updateDistance(p, dist);

            if (!wasParticipant) {
                // prevent rejoin attempts: add run-ban
                plugin.getPlayerDataManager().addRunBan(p.getName());
                // avoid calling setLeaveMessage(null) — use empty string safely
                try {
                    event.setLeaveMessage("");
                } catch (Exception ignored) {}
                return;
            }
        }

        // If player was manually run-banned elsewhere (edge-case), convert to excluded (no permanent ban)
        if (plugin.getPlayerDataManager().isRunBanned(p.getName())) {
            plugin.getPlayerDataManager().removeRunBan(p.getName());
            plugin.getPlayerDataManager().exclude(p);
            try {
                event.setLeaveMessage("");
            } catch (Exception ignored) {}
            return;
        }

        // default: clean up scoreboard visuals
        plugin.getScoreboardManager().removePlayer(p);
        Bukkit.getOnlinePlayers().forEach(plugin::updateVisibility);
        try {
            event.setLeaveMessage("");
        } catch (Exception ignored) {}
    }
}