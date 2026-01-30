package org.emrage.pvgrun.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;
import org.emrage.pvgrun.Main;
import org.emrage.pvgrun.managers.TeamBackpackHolder;

public class BackpackListener implements Listener {
    private final Main plugin;
    public BackpackListener(Main plugin) { this.plugin = plugin; }

    private boolean isBackpackInventory(InventoryHolder holder) {
        return holder instanceof TeamBackpackHolder;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        final Player actor = (Player) event.getWhoClicked();
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!isBackpackInventory(holder)) return;

        // Allow clicks (do not cancel). After click, refresh other team members' open inventory views.
        // Schedule a tick later to ensure the click has been applied to the inventory.
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other == actor) continue;
                try {
                    if (other.getOpenInventory() != null && other.getOpenInventory().getTopInventory().getHolder() instanceof TeamBackpackHolder) {
                        TeamBackpackHolder h = (TeamBackpackHolder) other.getOpenInventory().getTopInventory().getHolder();
                        TeamBackpackHolder act = (TeamBackpackHolder) holder;
                        if (h.getTeamName().equals(act.getTeamName())) other.updateInventory();
                    }
                } catch (Exception ignored) {}
            }
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        final Player actor = (Player) event.getWhoClicked();
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!isBackpackInventory(holder)) return;

        // Allow drag; schedule update for others
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other == actor) continue;
                try {
                    if (other.getOpenInventory() != null && other.getOpenInventory().getTopInventory().getHolder() instanceof TeamBackpackHolder) {
                        TeamBackpackHolder h = (TeamBackpackHolder) other.getOpenInventory().getTopInventory().getHolder();
                        TeamBackpackHolder act = (TeamBackpackHolder) holder;
                        if (h.getTeamName().equals(act.getTeamName())) other.updateInventory();
                    }
                } catch (Exception ignored) {}
            }
        });
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        // nothing special for close
    }
}
