package org. emrage.pvgrun.listeners;

import org.bukkit. Location;
import org.bukkit. event.EventHandler;
import org. bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit. event.player.PlayerTeleportEvent;
import org. bukkit.entity.Player;
import org.bukkit.block.Block;
import org.emrage.pvgrun.Main;

import static org.emrage.pvgrun.managers.BorderManager.RADIUS;
import static org.emrage.pvgrun.util.MessageUtils.c;

public class BorderProtectionListener implements Listener {

    private final Main plugin;

    public BorderProtectionListener(Main plugin) { this.plugin = plugin; }

    private boolean isAtBorderX(double x) {
        int bx = (int) Math.round(x);
        return bx == RADIUS || bx == -RADIUS;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        if (plugin.getGameManager().getState() != org.emrage.pvgrun.enums.GameState. RUNNING) return;
        Player p = e.getPlayer();
        if (plugin.getPlayerDataManager().isExcluded(p) || plugin.getPlayerDataManager().isDead(p)) return;
        Block b = e.getBlockPlaced();
        if (isAtBorderX(b.getX())) {
            e.setCancelled(true);
            p.sendActionBar(c("<#FF5555>Baue nicht an der Border. "));
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (plugin.getGameManager().getState() != org.emrage.pvgrun.enums.GameState.RUNNING) return;
        Player p = e.getPlayer();
        if (plugin.getPlayerDataManager().isExcluded(p) || plugin.getPlayerDataManager().isDead(p)) return;
        Block b = e.getBlock();
        if (isAtBorderX(b. getX())) {
            e.setCancelled(true);
            p.sendActionBar(c("<#FF5555>Abbau an der Border ist deaktiviert."));
        }
    }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent e) {
        for (Block moved : e.getBlocks()) {
            if (isAtBorderX(moved.getX())) {
                e. setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent e) {
        for (Block moved : e.getBlocks()) {
            if (isAtBorderX(moved.getX())) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent e) {
        if (plugin.getGameManager().getState() != org.emrage.pvgrun. enums.GameState.RUNNING) return;
        Player p = e.getPlayer();
        if (plugin.getPlayerDataManager().isExcluded(p) || plugin.getPlayerDataManager().isDead(p)) return;
        Location to = e.getTo();
        if (to != null) {
            double rx = RADIUS;
            if (to.getX() > rx || to.getX() < -rx) {
                e.setCancelled(true);
                p. sendActionBar(c("<#FF5555>Teleport über die Border ist nicht erlaubt."));
            }
        }
    }
}