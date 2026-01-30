package org.emrage.pvgrun.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.emrage.pvgrun.Main;
import org.emrage.pvgrun.managers.DimensionSelectionMenu;

public class ItemListener implements Listener {

    private final Main plugin;
    public ItemListener(Main plugin) { this.plugin = plugin; }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if ((plugin.getGameManager().getState() == org.emrage.pvgrun.enums.GameState.LOBBY || plugin.getGameManager().isPauseActive()) && !event.getPlayer().isOp()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player p = (Player) event.getEntity();
        if ((plugin.getGameManager().getState() == org.emrage.pvgrun.enums.GameState.LOBBY || plugin.getGameManager().isPauseActive()) && !p.isOp()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        // Block interaction during pause (unless OP) to prevent menus/actions
        if (plugin.getGameManager().isPauseActive() && !p.isOp()) {
            event.setCancelled(true);
            return;
        }

        if (plugin.getConfigManager().getGameMode().equals("dimension") && event.getItem() != null && event.getItem().getType() == org.bukkit.Material.BLAZE_ROD) {
            var team = plugin.getTeamManager().getTeam(p);
            if (team != null) {
                new DimensionSelectionMenu(p, dim -> {
                    team.setPlayerDimension(p.getUniqueId(), dim);
                    p.sendMessage("§eDu hast §6" + dim + "§e gewählt.");
                }).open();
                event.setCancelled(true);
            }
        }
    }
}