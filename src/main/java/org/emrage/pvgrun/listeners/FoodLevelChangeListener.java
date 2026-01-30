package org.emrage.pvgrun.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.entity.Player;
import org.emrage.pvgrun.Main;

/**
 * Prevent hunger loss in the Lobby phase.
 */
public class FoodLevelChangeListener implements Listener {

    private final Main plugin;
    public FoodLevelChangeListener(Main plugin) { this.plugin = plugin; }

    @EventHandler
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player p = (Player) event.getEntity();
        if (plugin.getGameManager() == null) return;
        if (plugin.getGameManager().getState() == org.emrage.pvgrun.enums.GameState.LOBBY) {
            // cancel hunger change in lobby (keeps players at full food)
            event.setCancelled(true);
            p.setFoodLevel(20);
        }
    }
}