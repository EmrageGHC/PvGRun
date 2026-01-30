package org.emrage.pvgrun.managers;

import net.kyori.adventure.text. Component;
import org.bukkit.Bukkit;
import org.bukkit. Difficulty;
import org.bukkit.Material;
import org.bukkit. entity.Player;
import org. bukkit.inventory. Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.emrage.pvgrun. Main;

import java.util.ArrayList;
import java.util.List;

import static org.emrage.pvgrun.util.MessageUtils.c;

public class ConfigGUI {

    private final Main plugin;
    private final Player viewer;
    private Inventory inv;

    public ConfigGUI(Main plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
    }

    public void open() {
        inv = Bukkit.createInventory(null, 54, c("<#FFD700>Deathrun Konfiguration"));
        buildItems();
        viewer.openInventory(inv);
    }

    private void buildItems() {
        var config = plugin.getConfigManager();

        // Border Radius
        inv.setItem(10, createItem(Material.BARRIER, 
            "<#AA00FF><bold>Border Radius</bold>",
            "<#FFFFFF>Aktuell: <#55FF55>" + config.getBorderRadius() + " Blöcke",
            "",
            "<#AAAAAA>Links: -10 | Rechts: +10",
            "<#AAAAAA>Shift+Links: -50 | Shift+Rechts: +50"
        ));

        // Game Duration
        int durationMin = config.getGameDurationSeconds() / 60;
        inv.setItem(12, createItem(Material.CLOCK,
            "<#AA00FF><bold>Spiel-Dauer</bold>",
            "<#FFFFFF>Aktuell: <#55FF55>" + durationMin + " Minuten",
            "",
            "<#AAAAAA>Links: -5min | Rechts: +5min",
            "<#AAAAAA>Shift+Links: -15min | Shift+Rechts: +15min"
        ));

        // Mob Spawn Delay
        int mobMin = config.getMobSpawnDelaySeconds() / 60;
        inv.setItem(14, createItem(Material.ZOMBIE_HEAD,
            "<#AA00FF><bold>Feindliche Mobs nach</bold>",
            "<#FFFFFF>Aktuell:  <#55FF55>" + mobMin + " Minuten",
            "",
            "<#AAAAAA>Links: -1min | Rechts: +1min",
            "<#AAAAAA>Shift+Links: -5min | Shift+Rechts: +5min"
        ));

        // Ultra Hardcore
        boolean uhc = config.isUltraHardcore();
        inv.setItem(16, createItem(uhc ? Material.REDSTONE_BLOCK : Material. EMERALD_BLOCK,
            "<#AA00FF><bold>Ultra Hardcore</bold>",
            "<#FFFFFF>Aktuell: " + (uhc ? "<#FF5555>AN" : "<#55FF55>AUS"),
            "",
            "<#AAAAAA>Klicken zum Umschalten",
            "<#888888>(Herzen permanent verloren)"
        ));

        // Difficulty
        Difficulty diff = config.getDifficulty();
        inv.setItem(28, createItem(Material.IRON_SWORD,
            "<#AA00FF><bold>Schwierigkeit</bold>",
            "<#FFFFFF>Aktuell: <#55FF55>" + diff.name(),
            "",
            "<#AAAAAA>Klicken zum Durchschalten"
        ));

        // Game Mode Auswahl
        String mode = config.getGameMode();
        String modeDisplay = mode.equals("dimension") ? "<#55FF55>Dimension Deathrun" : "<#FFD700>Normal";
        inv.setItem(20, createItem(Material.ENDER_EYE,
            "<#AA00FF><bold>Spielmodus</bold>",
            "<#FFFFFF>Aktuell: " + modeDisplay,
            "",
            "<#AAAAAA>Klicken zum Umschalten zwischen Normal und Dimension Deathrun"
        ));

        // Excluded Players
        List<String> excluded = config. getExcludedPlayers();
        List<Component> lore = new ArrayList<>();
        lore.add(c("<#FFFFFF>Ausgeschlossen: <#55FF55>" + excluded.size()));
        lore.add(c(""));
        for (String name : excluded) {
            lore.add(c("<#888888>- " + name));
        }
        lore.add(c(""));
        lore.add(c("<#AAAAAA>Klicken zum Bearbeiten"));

        ItemStack excludeItem = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = excludeItem.getItemMeta();
        meta.displayName(c("<#AA00FF><bold>Spieler Ausschließen</bold>"));
        meta.lore(lore);
        excludeItem.setItemMeta(meta);
        inv.setItem(30, excludeItem);

        // Start Button
        inv. setItem(49, createItem(Material.LIME_CONCRETE,
            "<#55FF55><bold>▶ SPIEL STARTEN</bold>",
            "",
            "<#AAAAAA>Mindestens 2 Spieler erforderlich"
        ));

        // Fill empty slots with glass pane
        ItemStack filler = new ItemStack(Material. GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.empty());
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < 54; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, filler);
        }
    }

    private ItemStack createItem(Material mat, String name, String...  lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(c(name));
        List<Component> loreList = new ArrayList<>();
        for (String line : lore) {
            loreList.add(c(line));
        }
        meta.lore(loreList);
        item.setItemMeta(meta);
        return item;
    }

    public void refresh() {
        buildItems();
    }
}