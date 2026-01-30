package org.emrage.pvgrun.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.emrage.pvgrun.Main;

import static org.emrage.pvgrun.util.MessageUtils.c;

public class AdminPauseCommand implements CommandExecutor {
    private final Main plugin;
    public AdminPauseCommand(Main plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (! (sender instanceof Player p)) { sender.sendMessage(c("<red>Nur Spieler.")); return true; }
        if (!p.hasPermission("pvgrun.adminpause")) { p.sendMessage(c("<red>Keine Berechtigung.")); return true; }
        if (args.length < 1) { p.sendMessage(c("<gray>/adminpause <seconds>")); return true; }
        try {
            int seconds = Integer.parseInt(args[0]);
            plugin.getGameManager().startPause(seconds);
            p.sendMessage(c("<green>Test-Pause gestartet ("+seconds+"s)."));
        } catch (NumberFormatException e) {
            p.sendMessage(c("<red>Ungültige Zahl."));
        }
        return true;
    }
}
