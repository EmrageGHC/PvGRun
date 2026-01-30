package org.emrage.pvgrun.managers;

import net. kyori.adventure.text.Component;
import org.bukkit. Bukkit;
import org. bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org. bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.emrage. pvgrun.Main;

import java.util.ArrayList;
import java.util.List;

import static org.emrage.pvgrun.util.MessageUtils.c;

public class ExcludePlayersGUI {

    private final Main plugin;
    private final Player viewer;
    private Inventory inv;

    public ExcludePlayersGUI(Main plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
    }

    public void open() {
        inv = Bukkit.createInventory(null, 54, c("<#FFD700>Spieler Ausschließen"));
        buildItems();
        viewer.openInventory(inv);
    }

    private void buildItems() {
        List<String> excluded = plugin.getConfigManager().getExcludedPlayers();
        
        int slot = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (slot >= 45) break; // max 45 players shown
            
            boolean isExcluded = excluded.contains(online.getName());
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            meta.setOwningPlayer(online);
            
            String statusColor = isExcluded ? "<#FF5555>" : "<#55FF55>";
            String statusText = isExcluded ? "AUSGESCHLOSSEN" : "TEILNEHMER";
            
            meta.displayName(c("<#FFFFFF><bold>" + online.getName() + "</bold>"));
            meta.lore(List.of(
                c(""),
                c(statusColor + "<bold>" + statusText + "</bold>"),
                c(""),
                c("<#AAAAAA>Klicken zum Umschalten")
            ));
            skull.setItemMeta(meta);
            inv.setItem(slot, skull);
            slot++;
        }

        // Back button
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(c("<#AA00FF>← Zurück"));
        back.setItemMeta(backMeta);
        inv.setItem(49, back);

        // Fill empty with glass
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component. empty());
        filler.setItemMeta(fillerMeta);
        for (int i = 45; i < 54; i++) {
            if (i != 49 && inv.getItem(i) == null) inv.setItem(i, filler);
        }
    }

    public void refresh() {
        buildItems();
    }
}