package org.emrage.pvgrun.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.emrage.pvgrun.Main;

public class BlockListener implements Listener {

    private final Main plugin;
    public BlockListener(Main plugin) { this.plugin = plugin; }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if ((plugin.getGameManager().getState() == org.emrage.pvgrun.enums.GameState.LOBBY || plugin.getGameManager().isPauseActive()) && !event.getPlayer().isOp()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if ((plugin.getGameManager().getState() == org.emrage.pvgrun.enums.GameState.LOBBY || plugin.getGameManager().isPauseActive()) && !event.getPlayer().isOp()) {
            event.setCancelled(true);
        }
    }
}