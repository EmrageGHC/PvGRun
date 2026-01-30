package org.emrage.pvgrun.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.emrage.pvgrun.Main;
import static org.emrage.pvgrun.util.MessageUtils.c;

public class ScorePreviewCommand implements CommandExecutor {
    private final Main plugin;
    public ScorePreviewCommand(Main plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage(c("<red>Nur Spieler!")); return true; }
        if (!p.hasPermission("pvgrun.scorepreview")) { p.sendMessage(c("<red>Keine Berechtigung.")); return true; }
        plugin.getScoreboardManager().togglePreview(p);
        p.sendMessage(c("<green>Scoreboard-Preview " + (plugin.getScoreboardManager().isPreviewing(p) ? "aktiviert" : "deaktiviert")));
        return true;
    }
}

