package org.emrage.pvgrun.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.emrage.pvgrun.Main;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class TeamBackpackManager {
    private final Map<String, Inventory> teamBackpacks = new HashMap<>(); // Teamname -> Backpack
    private final Main plugin;

    public TeamBackpackManager(Main plugin) {
        this.plugin = plugin;
    }

    public Inventory getBackpack(String teamName) {
        return teamBackpacks.computeIfAbsent(teamName, k -> {
            TeamBackpackHolder holder = new TeamBackpackHolder(teamName);
            Inventory inv = Bukkit.createInventory(holder, 27, "Team-Backpack: " + teamName);
            holder.setInventory(inv);
            return inv;
        });
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
            try {
                if (p.getOpenInventory() == null) { p.openInventory(inv); continue; }
                if (p.getOpenInventory().getTopInventory() == inv) continue; // already open
            } catch (Exception ignored) {}
            // If the player doesn't already have this inventory open, open it
            try { p.openInventory(inv); } catch (Exception ignored) {}
        }
    }

    public void removeBackpack(String teamName) {
        teamBackpacks.remove(teamName);
    }

    public void clear() {
        teamBackpacks.clear();
    }

    // Persistence
    private File backpackDir() {
        File d = new File(plugin.getDataFolder(), "backpacks");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public void saveBackpack(String teamName) {
        Inventory inv = teamBackpacks.get(teamName);
        if (inv == null) return;
        ItemStack[] contents = inv.getContents();
        File out = new File(backpackDir(), teamName + ".dat");
        try (FileOutputStream fos = new FileOutputStream(out);
             BufferedOutputStream bos = new BufferedOutputStream(fos);
             org.bukkit.util.io.BukkitObjectOutputStream boos = new org.bukkit.util.io.BukkitObjectOutputStream(bos)) {
            boos.writeInt(contents.length);
            for (ItemStack is : contents) {
                boos.writeObject(is);
            }
            plugin.getLogger().info("Saved backpack for team " + teamName + " (" + contents.length + " slots)");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save backpack for " + teamName + ": " + e.getMessage());
        }
    }

    public void loadBackpack(String teamName) {
        File in = new File(backpackDir(), teamName + ".dat");
        if (!in.exists()) return;
        try (FileInputStream fis = new FileInputStream(in);
             BufferedInputStream bis = new BufferedInputStream(fis);
             org.bukkit.util.io.BukkitObjectInputStream bois = new org.bukkit.util.io.BukkitObjectInputStream(bis)) {
            int len = bois.readInt();
            ItemStack[] items = new ItemStack[len];
            for (int i = 0; i < len; i++) {
                Object o = bois.readObject();
                if (o instanceof ItemStack) items[i] = (ItemStack)o; else items[i] = null;
            }
            Inventory inv = getBackpack(teamName);
            inv.setContents(items);
            plugin.getLogger().info("Loaded backpack for team " + teamName + " (" + len + " slots)");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load backpack for " + teamName + ": " + e.getMessage());
        }
    }

    public void saveAll() {
        for (String t : teamBackpacks.keySet()) saveBackpack(t);
    }
    public void loadAll() {
        File d = backpackDir();
        File[] files = d.listFiles((f, n) -> n.endsWith(".dat"));
        if (files == null) return;
        for (File f : files) {
            String name = f.getName();
            if (name.endsWith(".dat")) {
                String team = name.substring(0, name.length() - 4);
                loadBackpack(team);
            }
        }
    }
}
