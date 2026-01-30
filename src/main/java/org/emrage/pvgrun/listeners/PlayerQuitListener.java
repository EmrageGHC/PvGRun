package org.emrage.pvgrun.listeners;

import net.kyori.adventure. text.Component;
import org.bukkit.Bukkit;
import org. bukkit.entity.Player;
import org.bukkit.event. EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event. player.PlayerQuitEvent;
import org.emrage.pvgrun.Main;

import static org.emrage.pvgrun.util.MessageUtils.c;

public class PlayerQuitListener implements Listener {

    private final Main plugin;

    public PlayerQuitListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        var gm = plugin.getGameManager();

        // Remove scoreboard for quitting player
        plugin.getScoreboardManager().removePlayer(p);

        // Remove ghost border displays for this player
        plugin.getBorderManager().removeDisplaysFor(p);

        Bukkit.getOnlinePlayers().forEach(plugin::updateVisibility);

        // Clear tablist header/footer
        try {
            p.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
        } catch (Exception ignored) {}

        // Handle quit message based on game state
        if (gm == null || gm.getState() == org.emrage.pvgrun.enums.GameState. LOBBY) {
            return;
        }

        if (gm.getState() == org.emrage.pvgrun.enums.GameState. RUNNING) {
            // Participant leaving during game - silent quit, score remains
            if (gm.wasParticipant(p. getUniqueId())) {
                // Update scoreboards for remaining players (score stays visible)
                plugin.getScoreboardManager().updateAllForActive();
            }
            return;
        }

        // All other states - silent quit

        // Team-Item entfernen, falls vorhanden
        if (plugin.getConfigManager().getGameMode().equals("dimension")) {
            var inv = p.getInventory();
            if (inv.getItem(4) != null && inv.getItem(4).getType() == org.bukkit.Material.STICK) {
                inv.clear(4);
            }
        }
    }
}