package org.emrage.pvgrun.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.entity.Player;
import org.emrage.pvgrun.Main;

import static org.emrage.pvgrun.util.MessageUtils.c;

public class PlayerDeathFallbackListener implements Listener {
    private final Main plugin;
    public PlayerDeathFallbackListener(Main plugin) { this.plugin = plugin; }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        var gm = plugin.getGameManager();
        if (gm == null) return;
        if (gm.getState() != org.emrage.pvgrun.enums.GameState.RUNNING) return;

        // If already marked dead, ignore
        if (plugin.getPlayerDataManager().isDead(p)) return;

        // compute distance similar to damage listener
        double dist = 0;
        var spawn = gm.getSpawnLocation();
        if (spawn != null) dist = Math.max(0.0, spawn.getZ() - p.getLocation().getZ());

        plugin.getPlayerDataManager().updateDistance(p, dist);
        int placement = plugin.getPlayerDataManager().getPlacement(p);
        plugin.getPlayerDataManager().markDead(p);
        plugin.getPlayerDataManager().addRunBan(p.getName());

        String distStr = String.format("%.0f", dist);
        String placeStr = (placement <= 0) ? "—" : ("#"+placement);
        String reason = String.join("\n",
                "<#FFD700><bold>━━━━━━━━━━━━━━━━━</bold>",
                "<#FFD700><bold>DEATHRUN</bold>",
                "",
                "<#55FF55>Danke fürs Teilnehmen! ",
                "",
                "<#AAAAAA>Deine Distanz: <#55FF55><bold>" + distStr + "m</bold>",
                "<#AAAAAA>Dein Rang: <#FFD700><bold>" + placeStr + "</bold>",
                "",
                "<#888888>Du wirst entbannt, sobald das Spiel vorbei ist.",
                "",
                "<#FFD700><bold>━━━━━━━━━━━━━━━━━</bold>"
        );

        try { if (p.isOnline()) p.kick(c(reason)); } catch (Exception ignored) {}

        // update boards and check winner
        plugin.getScoreboardManager().updateAllForActive();
        plugin.getGameManager().checkForWinner();
    }
}
