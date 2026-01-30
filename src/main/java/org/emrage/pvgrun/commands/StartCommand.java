package org.emrage.pvgrun.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.emrage.pvgrun.Main;

public class StartCommand implements CommandExecutor {

    private final Main plugin;
    public StartCommand(Main plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        boolean force = args.length > 0 && args[0].equalsIgnoreCase("force");
        plugin.getGameManager().startGame(p, force);
        return true;
    }
}