package org.emrage.pvgrun.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import java.util.*;

public class TeamBackpackManager {
    private final Map<String, Inventory> teamBackpacks = new HashMap<>(); // Teamname -> Backpack

    public Inventory getBackpack(String teamName) {
        return teamBackpacks.computeIfAbsent(teamName, k -> Bukkit.createInventory(null, 27, "Team-Backpack: " + teamName));
    }
    public boolean hasBackpack(String teamName) {
        return teamBackpacks.containsKey(teamName);
    }

    public void openBackpack(Player p, String teamName) {
        Inventory inv = getBackpack(teamName);
        p.openInventory(inv);
    }

    public void openBackpackForTeam(String teamName) {
        Inventory inv = getBackpack(teamName);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isOnline() && p.getOpenInventory().getTitle().equals("Team-Backpack: " + teamName)) continue;
            if (p.getInventory().contains(inv.getItem(0))) {
                p.openInventory(inv);
            }
        }
    }

    public void removeBackpack(String teamName) {
        teamBackpacks.remove(teamName);
    }

    public void clear() {
        teamBackpacks.clear();
    }
}
