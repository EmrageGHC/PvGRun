package org.emrage.pvgrun.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.emrage.pvgrun.Main;
import org.emrage.pvgrun.managers.TeamManager;
import static org.emrage.pvgrun.util.MessageUtils.c;

public class TeamCommand implements CommandExecutor {
    private final Main plugin;
    public TeamCommand(Main plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return false;
        Player p = (Player) sender;
        TeamManager tm = plugin.getTeamManager();
        if (args.length == 0) {
            p.sendMessage(c("<gray>/team accept | /team deny | /team name <Name> | /team leave"));
            return true;
        }
        if (args[0].equalsIgnoreCase("accept")) {
            // Suche offene Anfrage
            for (var entry : tm.getTeams()) {
                // handled by listener
            }
            // handled by listener
            p.sendMessage(c("<gray>Schlage den Spieler zurück oder warte auf eine Anfrage!"));
            return true;
        }
        if (args[0].equalsIgnoreCase("deny")) {
            // handled by listener
            p.sendMessage(c("<gray>Anfrage abgelehnt."));
            return true;
        }
        if (args[0].equalsIgnoreCase("name") && args.length > 1) {
            TeamManager.Team team = tm.getTeam(p);
            if (team == null) {
                p.sendMessage(c("<red>Du bist in keinem Team!"));
                return true;
            }
            String newName = args[1];
            team.setName(newName);
            p.sendMessage(c("<green>Teamname geändert zu <white>" + newName));
            return true;
        }
        if (args[0].equalsIgnoreCase("leave")) {
            tm.removeTeamByPlayer(p);
            p.sendMessage(c("<red>Du hast dein Team verlassen."));
            return true;
        }
        return false;
    }
}
