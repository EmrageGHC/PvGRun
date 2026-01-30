package org.emrage.pvgrun.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.emrage.pvgrun.Main;

import static org.emrage.pvgrun.util.MessageUtils.c;

public class SettingsCommand implements CommandExecutor {
    private final Main plugin;
    public SettingsCommand(Main plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(c("<red>Nur Spieler können diesen Befehl nutzen."));
            return true;
        }
        if (!p.hasPermission("pvgrun.settings")) {
            p.sendMessage(c("<red>Keine Berechtigung."));
            return true;
        }

        if (plugin.getGameManager().getState() != org.emrage.pvgrun.enums.GameState.LOBBY) {
            p.sendMessage(c("<red>Einstellungen können nur in der Lobby geändert werden."));
            return true;
        }

        if (args.length == 0) {
            // Open GUI
            new org.emrage.pvgrun.managers.ConfigGUI(plugin, p).open();
            return true;
        }

        if (args[0].equalsIgnoreCase("mode") && args.length >= 2) {
            String mode = args[1].toLowerCase();
            if (!mode.equals("normal") && !mode.equals("dimension")) {
                p.sendMessage(c("<red>Ungültiger Modus. Verwende normal oder dimension."));
                return true;
            }
            plugin.getConfigManager().setGameMode(mode);
            p.sendMessage(c("<green>Modus gesetzt auf: <white>" + mode));

            // Give or remove team selector items for all current players in lobby
            for (Player pl : plugin.getServer().getOnlinePlayers()) {
                if (plugin.getPlayerDataManager().isExcluded(pl)) continue;
                if (mode.equals("dimension")) {
                    if (!plugin.getTeamManager().hasTeam(pl)) {
                        org.emrage.pvgrun.listeners.TeamRequestListener.giveTeamInviteItem(pl);
                    } else {
                        // players already in team should get the dimension selector
                        org.emrage.pvgrun.listeners.TeamRequestListener.giveDimensionSelectItem(pl);
                    }
                } else {
                    // Remove both items from slot 4 if present
                    pl.getInventory().setItem(4, null);
                }
            }
            return true;
        }

        p.sendMessage(c("<red>Unbekannter Subbefehl."));
        return true;
    }
}
