package org.emrage.pvgrun.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.emrage.pvgrun.Main;

import static org.emrage.pvgrun.util.MessageUtils.c;

public class ExcludeCommand implements CommandExecutor {

    private final Main plugin;
    public ExcludeCommand(Main plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (!p.isOp()) { p.sendMessage(c("<#FF5555>Nur OPs können Spieler ausschließen.")); return true; }
        if (args.length == 0) { p.sendMessage(c("<#FF5555>Usage: /exclude <player>")); return true; }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) { p.sendMessage(c("<#FF5555>Spieler nicht gefunden.")); return true; }
        if (plugin.getGameManager().getState() != org.emrage.pvgrun.enums.GameState.LOBBY) {
            p.sendMessage(c("<#FF5555>Exclude nur in Lobby möglich.")); return true;
        }
        plugin.getPlayerDataManager().exclude(target);
        target.setGameMode(org.bukkit.GameMode.SPECTATOR);
        plugin.getScoreboardManager().addForPlayer(target);
        p.sendMessage(c(String.format("<#55FF55>%s wurde ausgeschlossen.", target.getName())));
        return true;
    }
}