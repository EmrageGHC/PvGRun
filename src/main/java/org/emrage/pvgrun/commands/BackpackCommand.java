package org.emrage.pvgrun.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.emrage.pvgrun.Main;

public class BackpackCommand implements CommandExecutor {
    private final Main plugin;
    public BackpackCommand(Main plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return false;
        Player p = (Player) sender;
        if (!plugin.getConfigManager().getGameMode().equals("dimension")) {
            p.sendMessage("§cNur im Dimension-Modus verfügbar!");
            return true;
        }
        var team = plugin.getTeamManager().getTeam(p);
        if (team == null) {
            p.sendMessage("§cDu bist in keinem Team!");
            return true;
        }
        plugin.getTeamBackpackManager().openBackpack(p, team.getName());
        return true;
    }
}

