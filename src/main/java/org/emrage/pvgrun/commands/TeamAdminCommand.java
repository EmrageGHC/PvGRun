package org.emrage.pvgrun.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.emrage.pvgrun.Main;
import org.emrage.pvgrun.managers.TeamManager;

import static org.emrage.pvgrun.util.MessageUtils.c;

public class TeamAdminCommand implements CommandExecutor {
    private final Main plugin;
    public TeamAdminCommand(Main plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("pvgrun.teamadmin")) { sender.sendMessage(c("<red>Keine Berechtigung.")); return true; }
        if (args.length == 0) { sender.sendMessage(c("<gray>/teamadmin dissolve <teamname> | rename <teamname> <newname>")); return true; }
        TeamManager tm = plugin.getTeamManager();
        if (args[0].equalsIgnoreCase("dissolve") && args.length >= 2) {
            String tname = args[1];
            TeamManager.Team found = null;
            for (var t : tm.getTeams()) if (t.getName().equalsIgnoreCase(tname)) { found = t; break; }
            if (found == null) { sender.sendMessage(c("<red>Team nicht gefunden.")); return true; }
            // give invite item back to members
            for (var uid : found.getMembers()) {
                Player pl = Bukkit.getPlayer(uid);
                if (pl != null && pl.isOnline()) org.emrage.pvgrun.listeners.TeamRequestListener.giveTeamInviteItem(pl);
            }
            tm.removeTeam(found);
            sender.sendMessage(c("<green>Team aufgelöst: <white>"+tname));
            return true;
        }
        if (args[0].equalsIgnoreCase("rename") && args.length >= 3) {
            String tname = args[1];
            String newname = args[2];
            TeamManager.Team found = null;
            for (var t : tm.getTeams()) if (t.getName().equalsIgnoreCase(tname)) { found = t; break; }
            if (found == null) { sender.sendMessage(c("<red>Team nicht gefunden.")); return true; }
            found.setName(newname);
            sender.sendMessage(c("<green>Team umbenannt: <white>"+newname));
            return true;
        }
        return true;
    }
}

