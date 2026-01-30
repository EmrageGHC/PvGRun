package org.emrage.pvgrun.commands;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.emrage.pvgrun.Main;

public class SetNetherSpawnCommand implements CommandExecutor {
    private final Main plugin;
    public SetNetherSpawnCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cNur Spieler können diesen Befehl ausführen.");
            return true;
        }
        if (!p.hasPermission("pvgrun.setnetherspawn")) {
            p.sendMessage("§cKeine Berechtigung.");
            return true;
        }
        World nether = Bukkit.getWorld("world_nether");
        if (nether == null || !p.getWorld().equals(nether)) {
            p.sendMessage("§cDu musst dich im Nether befinden!");
            return true;
        }
        Location loc = p.getLocation();
        nether.setSpawnLocation(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        // Border wie beim Prepare setzen
        WorldBorder wb = nether.getWorldBorder();
        wb.setCenter(loc.getX(), loc.getZ());
        wb.setSize(100.0); // Beispielgröße, ggf. anpassen
        wb.setWarningDistance(5);
        wb.setDamageAmount(0);
        // Markiere im ConfigManager, dass Nether-Spawn gesetzt wurde
        plugin.getConfigManager().setNetherSpawnSet(true);
        p.sendMessage("§aSpawnpunkt und Border im Nether gesetzt!");
        return true;
    }
}
