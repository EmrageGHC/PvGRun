package org.emrage.pvgrun.managers;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.function.Consumer;

import static org.emrage.pvgrun.util.MessageUtils.c;

public class DimensionSelectionMenu {
    private final Player player;
    private final Consumer<String> onSelect;
    private Inventory inv;

    public DimensionSelectionMenu(Player player, Consumer<String> onSelect) {
        this.player = player;
        this.onSelect = onSelect;
    }

    public void open() {
        inv = Bukkit.createInventory(null, 27, c("<#00d4ff><bold>Dimension wählen</bold>"));
        // decorative panes
        ItemStack pane = new ItemStack(Material.BLUE_STAINED_GLASS_PANE);
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);

        // Overworld (Left)
        ItemStack overworld = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta metaO = (SkullMeta) overworld.getItemMeta();
        // Use plain strings for item meta to remain compatible across server API versions
        metaO.setDisplayName("Overworld");
        metaO.setLore(java.util.List.of(
                "Sanfte Ebenen, Bäume & Schnee",
                "Klicke um Overworld zu wählen"
        ));
        // set a sample owner for a grass-like head (optional)
        metaO.setOwningPlayer(Bukkit.getOfflinePlayer("Notch"));
        overworld.setItemMeta(metaO);
        inv.setItem(10, overworld);

        // Nether (Right)
        ItemStack nether = new ItemStack(Material.NETHER_BRICK);
        org.bukkit.inventory.meta.ItemMeta metaN = nether.getItemMeta();
        metaN.setDisplayName("Nether");
        metaN.setLore(java.util.List.of(
                "Feurige Höhlen, Lava & Herausforderung",
                "Klicke um Nether zu wählen"
        ));
        nether.setItemMeta(metaN);
        inv.setItem(16, nether);

        // Back button
        ItemStack back = new ItemStack(Material.ARROW);
        org.bukkit.inventory.meta.ItemMeta backM = back.getItemMeta();
        backM.setDisplayName("Zurück");
        back.setItemMeta(backM);
        inv.setItem(22, back);

        player.openInventory(inv);
    }

    public void handleClick(InventoryClickEvent event) {
        String title = event.getView().title().toString();
        if (!title.contains("Dimension wählen")) return;
        event.setCancelled(true);
        int slot = event.getSlot();
        if (slot == 10) onSelect.accept("overworld");
        else if (slot == 16) onSelect.accept("nether");
        else if (slot == 22) {
            // close
        }
        player.closeInventory();
    }
}
