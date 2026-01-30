package org.emrage.pvgrun.listeners;

import org. bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit. block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event. EventPriority;
import org. bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.emrage.pvgrun.Main;

public class BorderInteractionListener implements Listener {

    private final Main plugin;

    public BorderInteractionListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority. HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();

        // Block all interactions during pause (unless OP)
        if (plugin.getGameManager().isPauseActive() && !event.getPlayer().isOp()) {
            event.setCancelled(true);
            return;
        }

        // Check all interaction types that could affect blocks
        if (action != Action.RIGHT_CLICK_BLOCK &&
                action != Action.LEFT_CLICK_BLOCK &&
                action != Action.PHYSICAL) {
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        Player player = event.getPlayer();
        Location loc = clicked.getLocation();

        // If this location is part of the player's shown ghost-border, cancel & resend barrier
        if (plugin.getBorderManager().isLocationShownForPlayer(player, loc)) {
            event.setCancelled(true);

            // Resend immediately
            plugin.getBorderManager().resendBarrierAt(player, loc);

            // Schedule additional resends to be absolutely sure
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    plugin.getBorderManager().resendBarrierAt(player, loc);
                }
            }, 1L);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    plugin.getBorderManager().resendBarrierAt(player, loc);
                }
            }, 3L);
        }
    }
}