package org.emrage.pvgrun.managers;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class TeamBackpackHolder implements InventoryHolder {
    private final String teamName;
    private Inventory inventory;

    public TeamBackpackHolder(String teamName) {
        this.teamName = teamName;
    }

    public String getTeamName() { return teamName; }

    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
