package org.emrage.pvgrun.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.entity.Player;

public class HealthRegenBlockerListener implements Listener {

    @EventHandler
    public void onRegain(EntityRegainHealthEvent event) {
        // block all automatic / potion / golden apple health regains for players
        if (event.getEntity() instanceof Player) {
            event.setCancelled(true);
        }
    }
}