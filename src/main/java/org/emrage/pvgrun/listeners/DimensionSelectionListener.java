 package org.emrage.pvgrun.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.entity.Player;
import org.emrage.pvgrun.managers.DimensionSelectionMenu;
import org.emrage.pvgrun.Main;

public class DimensionSelectionListener implements Listener {
    private final Main plugin;
    public DimensionSelectionListener(Main plugin) { this.plugin = plugin; }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player p = (Player) event.getWhoClicked();
        if (event.getView().getTitle().equals("Dimension wählen")) {
            // Hier müsste die aktuelle Menu-Instanz gefunden werden, ggf. über Mapping
            // Für Demo: Einfach Auswahl Overworld/Nether setzen
            if (event.getSlot() == 3) {
                plugin.getTeamManager().getTeam(p).setDimension("overworld");
            } else if (event.getSlot() == 5) {
                plugin.getTeamManager().getTeam(p).setDimension("nether");
            }
            p.closeInventory();
            event.setCancelled(true);
        }
    }
}

