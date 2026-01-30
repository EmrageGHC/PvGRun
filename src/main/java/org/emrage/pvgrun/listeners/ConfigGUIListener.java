package org.emrage.pvgrun. listeners;

import org.bukkit. Bukkit;
import org.bukkit. Difficulty;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event. Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta. SkullMeta;
import org.emrage.pvgrun. Main;
import org.emrage.pvgrun.managers.ConfigGUI;
import org.emrage.pvgrun.managers.ExcludePlayersGUI;

import static org.emrage.pvgrun.util.MessageUtils.c;

public class ConfigGUIListener implements Listener {

    private final Main plugin;

    public ConfigGUIListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player p = (Player) event.getWhoClicked();
        String title = event.getView().title().toString();

        // Config GUI
        if (title.contains("Deathrun Konfiguration")) {
            event.setCancelled(true);
            int slot = event.getSlot();
            ClickType click = event.getClick();
            var config = plugin.getConfigManager();

            // Border Radius (slot 10)
            if (slot == 10) {
                int current = config.getBorderRadius();
                int change = 0;
                if (click == ClickType.LEFT) change = -10;
                else if (click == ClickType. RIGHT) change = 10;
                else if (click == ClickType. SHIFT_LEFT) change = -50;
                else if (click == ClickType.SHIFT_RIGHT) change = 50;
                
                int newVal = Math.max(50, Math.min(500, current + change));
                config.setBorderRadius(newVal);
                new ConfigGUI(plugin, p).open();
            }

            // Game Duration (slot 12)
            else if (slot == 12) {
                int current = config.getGameDurationSeconds();
                int change = 0;
                if (click == ClickType.LEFT) change = -5 * 60;
                else if (click == ClickType.RIGHT) change = 5 * 60;
                else if (click == ClickType.SHIFT_LEFT) change = -15 * 60;
                else if (click == ClickType.SHIFT_RIGHT) change = 15 * 60;
                
                int newVal = Math.max(60, Math.min(7200, current + change));
                config.setGameDurationSeconds(newVal);
                new ConfigGUI(plugin, p).open();
            }

            // Mob Spawn Delay (slot 14)
            else if (slot == 14) {
                int current = config.getMobSpawnDelaySeconds();
                int change = 0;
                if (click == ClickType.LEFT) change = -60;
                else if (click == ClickType.RIGHT) change = 60;
                else if (click == ClickType.SHIFT_LEFT) change = -5 * 60;
                else if (click == ClickType. SHIFT_RIGHT) change = 5 * 60;
                
                int newVal = Math. max(0, Math.min(3600, current + change));
                config.setMobSpawnDelaySeconds(newVal);
                new ConfigGUI(plugin, p).open();
            }

            // Ultra Hardcore (slot 16)
            else if (slot == 16) {
                config.setUltraHardcore(! config.isUltraHardcore());
                new ConfigGUI(plugin, p).open();
            }

            // Difficulty (slot 28)
            else if (slot == 28) {
                Difficulty current = config.getDifficulty();
                Difficulty[] values = Difficulty.values();
                int idx = 0;
                for (int i = 0; i < values. length; i++) {
                    if (values[i] == current) { idx = i; break; }
                }
                idx = (idx + 1) % values.length;
                config. setDifficulty(values[idx]);
                new ConfigGUI(plugin, p).open();
            }

            // Exclude Players (slot 30)
            else if (slot == 30) {
                new ExcludePlayersGUI(plugin, p).open();
            }

            // Start Button (slot 49)
            else if (slot == 49) {
                p.closeInventory();
                plugin.getGameManager().startGame(p, false);
            }

            // Game Mode Auswahl (slot 20)
            else if (slot == 20) {
                String current = config.getGameMode();
                String next = current.equals("dimension") ? "normal" : "dimension";
                config.setGameMode(next);
                // Hier ggf. Welten pre-rendern, falls nötig (später implementieren)
                new ConfigGUI(plugin, p).open();
            }
        }

        // Exclude Players GUI
        else if (title.contains("Spieler Ausschließen")) {
            event.setCancelled(true);
            int slot = event.getSlot();
            ItemStack item = event.getCurrentItem();
            
            if (item == null) return;

            // Back button
            if (slot == 49) {
                new ConfigGUI(plugin, p).open();
                return;
            }

            // Player head clicked
            if (item.getItemMeta() instanceof SkullMeta) {
                SkullMeta meta = (SkullMeta) item.getItemMeta();
                if (meta.getOwningPlayer() != null) {
                    String name = meta.getOwningPlayer().getName();
                    var config = plugin.getConfigManager();
                    var excluded = config.getExcludedPlayers();
                    
                    if (excluded.contains(name)) {
                        config.removeExcludedPlayer(name);
                        plugin.getPlayerDataManager().unexclude(Bukkit.getPlayerExact(name));
                    } else {
                        config. addExcludedPlayer(name);
                        Player target = Bukkit.getPlayerExact(name);
                        if (target != null) plugin.getPlayerDataManager().exclude(target);
                    }
                    
                    new ExcludePlayersGUI(plugin, p).open();
                }
            }
        }
    }
}