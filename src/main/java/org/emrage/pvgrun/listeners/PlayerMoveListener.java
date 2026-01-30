package org. emrage.pvgrun.listeners;

import org.bukkit. Location;
import org.bukkit. Sound;
import org.bukkit. World;
import org.bukkit. entity.Player;
import org. bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org. bukkit.event.player.PlayerMoveEvent;
import org. bukkit.util.Vector;
import org.emrage.pvgrun.Main;

public class PlayerMoveListener implements Listener {

    private final Main plugin;
    public PlayerMoveListener(Main plugin) { this.plugin = plugin; }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        var gm = plugin.getGameManager();

        if (gm.getState() != org.emrage.pvgrun. enums.GameState.RUNNING) return;

        if (plugin.getPlayerDataManager().isExcluded(p) || plugin.getPlayerDataManager().isDead(p)) {
            plugin.getBorderManager().removeDisplaysFor(p);
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        // Check for significant movement (avoid spam for micro-movements like head rotation)
        if (from.distanceSquared(to) < 0.01) return;

        World world = p.getWorld();
        double rx = org.emrage.pvgrun. managers.BorderManager. RADIUS;

        // Check if player tries to cross the border
        boolean crossingEast = from.getX() <= rx && to.getX() > rx;
        boolean crossingWest = from.getX() >= -rx && to.getX() < -rx;

        if (crossingEast || crossingWest) {
            // Cancel movement and apply knockback bounce
            event.setCancelled(true);

            // Calculate bounce direction (away from border)
            Vector vel = p.getVelocity().clone();
            if (crossingEast) {
                vel.setX(-Math.abs(vel.getX()) - 0.5); // bounce west
            } else {
                vel.setX(Math.abs(vel.getX()) + 0.5); // bounce east
            }
            vel.setY(Math.max(0.2, vel.getY())); // small upward boost

            p.setVelocity(vel);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.5f, 1.5f);
            p.sendActionBar(org.emrage.pvgrun. util.MessageUtils.c("<#FF5555><bold>⚠ Border! </bold>"));
            return;
        }

        // Update visual border (only when close enough)
        plugin.getBorderManager().checkPlayer(p);

        // Compute distance to NORTH (spawnZ - playerZ)
        Location spawn = gm.getSpawnLocation();
        double dist = Math.max(0.0, spawn.getZ() - to.getZ());

        double current = plugin.getPlayerDataManager().getStoredDistance(p.getName());
        if (dist > current) plugin.getPlayerDataManager().updateDistance(p, dist);
    }
}