package org.emrage.pvgrun.managers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.function.Consumer;

public class DimensionSelectionMenu {
    private final Player player;
    private final Consumer<String> onSelect;
    private Inventory inv;

    public DimensionSelectionMenu(Player player, Consumer<String> onSelect) {
        this.player = player;
        this.onSelect = onSelect;
    }

    public void open() {
        inv = Bukkit.createInventory(null, 9, "Dimension wählen");
        ItemStack overworld = new ItemStack(Material.GRASS_BLOCK);
        ItemMeta metaO = overworld.getItemMeta();
        metaO.setDisplayName("§aOverworld");
        overworld.setItemMeta(metaO);
        inv.setItem(3, overworld);
        ItemStack nether = new ItemStack(Material.NETHERRACK);
        ItemMeta metaN = nether.getItemMeta();
        metaN.setDisplayName("§cNether");
        nether.setItemMeta(metaN);
        inv.setItem(5, nether);
        player.openInventory(inv);
    }

    public void handleClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("Dimension wählen")) return;
        event.setCancelled(true);
        if (event.getSlot() == 3) onSelect.accept("overworld");
        if (event.getSlot() == 5) onSelect.accept("nether");
        player.closeInventory();
    }
}

