package org.emrage.pvgrun.managers;

import net.kyori.adventure.text.Component;
import org.bukkit. Bukkit;
import org.bukkit.Material;
import org. bukkit.entity.Player;
import org.bukkit. inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit. NamespacedKey;
import org.emrage.pvgrun.Main;

import java.util.List;

import static org.emrage.pvgrun.util.MessageUtils.c;

public class ConfigItemManager {

    private final Main plugin;
    private final NamespacedKey configItemKey;
    private Player configHolder = null;

    public ConfigItemManager(Main plugin) {
        this.plugin = plugin;
        this.configItemKey = new NamespacedKey(plugin, "config_item");
    }

    public ItemStack createConfigItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(c("<#FFD700><bold>⚙ Deathrun Konfiguration</bold>"));
            meta.lore(List.of(
                c("<#AAAAAA>Rechtsklick zum Öffnen"),
                c("<#888888>Nur für den Host")
            ));
            meta.getPersistentDataContainer().set(configItemKey, PersistentDataType. BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isConfigItem(ItemStack item) {
        if (item == null || ! item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta. getPersistentDataContainer().has(configItemKey, PersistentDataType.BYTE);
    }

    public void giveToFirstPlayer(Player player) {
        if (configHolder != null) return; // already given
        configHolder = player;
        player.getInventory().setItem(4, createConfigItem());
        player.sendMessage(c("<#55FF55>Du bist der Host!  Nutze das Konfigurations-Item zum Einstellen. "));
    }

    public void removeFromAll() {
        for (Player p : Bukkit. getOnlinePlayers()) {
            for (int i = 0; i < p.getInventory().getSize(); i++) {
                ItemStack item = p.getInventory().getItem(i);
                if (isConfigItem(item)) {
                    p.getInventory().setItem(i, null);
                }
            }
        }
        configHolder = null;
    }

    public boolean isConfigHolder(Player player) {
        return configHolder != null && configHolder. getUniqueId().equals(player.getUniqueId());
    }

    public Player getConfigHolder() {
        return configHolder;
    }

    public void clearConfigHolder() {
        configHolder = null;
    }
}