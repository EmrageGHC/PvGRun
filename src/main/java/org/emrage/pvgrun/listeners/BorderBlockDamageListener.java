package org.emrage.pvgrun.listeners;

import org.bukkit.Location;
import org.bukkit. entity.Player;
import org. bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event. Listener;
import org.bukkit.event.block.BlockDamageEvent;
import org.emrage.pvgrun. Main;

public class BorderBlockDamageListener implements Listener {

    private final Main plugin;

    public BorderBlockDamageListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        Location loc = event.getBlock().getLocation();

        // If player is damaging a ghost border block, cancel it
        if (plugin.getBorderManager().isLocationShownForPlayer(player, loc)) {
            event.setCancelled(true);
            // Resend to make sure it stays
            plugin.getBorderManager().resendBarrierAt(player, loc);
        }
    }
}