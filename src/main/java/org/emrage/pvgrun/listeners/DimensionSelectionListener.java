package org.emrage.pvgrun.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.entity.Player;
import org.emrage.pvgrun.Main;

public class DimensionSelectionListener implements Listener {
    private final Main plugin;
    public DimensionSelectionListener(Main plugin) { this.plugin = plugin; }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        String title = event.getView().title().toString();
        if (!title.contains("Dimension wählen")) return;

        event.setCancelled(true);
        int slot = event.getSlot();
        var team = plugin.getTeamManager().getTeam(p);
        if (team == null) { p.closeInventory(); return; }
        if (slot == 10) {
            team.setPlayerDimension(p.getUniqueId(), "overworld");
        } else if (slot == 16) {
            team.setPlayerDimension(p.getUniqueId(), "nether");
        }
        p.closeInventory();
    }
}
