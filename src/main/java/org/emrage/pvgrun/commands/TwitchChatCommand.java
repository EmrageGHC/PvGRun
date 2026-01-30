package org.emrage.pvgrun.commands;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.emrage.pvgrun.Main;

import static org.emrage.pvgrun.util.MessageUtils.c;

public class TwitchChatCommand implements CommandExecutor {

    private final Main plugin;

    public TwitchChatCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(c("<red>Nur Spieler können diesen Befehl verwenden!"));
            return true;
        }

        if (!player.hasPermission("pvgrun.twitchchat")) {
            player.sendMessage(c("<red>Du hast keine Berechtigung für diesen Befehl!"));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(c("<gold>╔══════════════════════════════╗"));
            player.sendMessage(c("<gold>║ <white><bold>Twitch Chat Befehle</bold>         <gold>║"));
            player.sendMessage(c("<gold>╠══════════════════════════════╣"));
            player.sendMessage(c("<gold>║ <yellow>/twitchchat start <channel> <gold>║"));
            player.sendMessage(c("<gold>║ <gray>- Chat an deiner Position    <gold>║"));
            player.sendMessage(c("<gold>║                              <gold>║"));
            player.sendMessage(c("<gold>║ <yellow>/twitchchat track           <gold>║"));
            player.sendMessage(c("<gold>║ <gray>- Chat folgt dir             <gold>║"));
            player.sendMessage(c("<gold>║                              <gold>║"));
            player.sendMessage(c("<gold>║ <yellow>/twitchchat stop            <gold>║"));
            player.sendMessage(c("<gold>║ <gray>- Chat Display beenden       <gold>║"));
            player.sendMessage(c("<gold>║                              <gold>║"));
            player.sendMessage(c("<gold>║ <yellow>/twitchchat move            <gold>║"));
            player.sendMessage(c("<gold>║ <gray>- Chat zu dir verschieben    <gold>║"));
            player.sendMessage(c("<gold>║ <yellow>/twitchchat set <channel>    <gold>║"));
            player.sendMessage(c("<gold>║ <gray>- Ändert den Channel im laufenden Betrieb <gold>║"));
            player.sendMessage(c("<gold>╚══════════════════════════════╝"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start" -> {
                if (args.length < 2) {
                    player.sendMessage(c("<red>Verwendung: /twitchchat start <channel>"));
                    return true;
                }

                String channel = args[1];
                Location loc = player.getLocation().add(0, 3, 0);

                plugin.getTwitchChatManager().start(channel, loc);
                player.sendMessage(c("<green>Twitch Chat Display gestartet für: <white>" + channel));
                player.sendMessage(c("<gray>Position: " + formatLocation(loc)));
            }

            case "stop" -> {
                if (!plugin.getTwitchChatManager().isRunning()) {
                    player.sendMessage(c("<red>Kein Chat Display aktiv!"));
                    return true;
                }

                plugin.getTwitchChatManager().stop();
                player.sendMessage(c("<green>Twitch Chat Display gestoppt!"));
            }

            case "track" -> {
                if (!plugin.getTwitchChatManager().isRunning()) {
                    player.sendMessage(c("<red>Kein Chat Display aktiv!"));
                    return true;
                }

                plugin.getTwitchChatManager().setTrackedPlayer(player.getName());
                player.sendMessage(c("<green>Chat Display folgt dir jetzt!"));
                player.sendMessage(c("<gray>Tipp: Nutze /twitchchat move um das Tracking zu deaktivieren."));
            }

            case "move" -> {
                if (!plugin.getTwitchChatManager().isRunning()) {
                    player.sendMessage(c("<red>Kein Chat Display aktiv!"));
                    return true;
                }

                Location loc = player.getLocation().add(0, 3, 0);
                plugin.getTwitchChatManager().setLocation(loc);
                player.sendMessage(c("<green>Chat Display verschoben!"));
                player.sendMessage(c("<gray>Neue Position: " + formatLocation(loc)));
            }

            case "set" -> {
                if (args.length < 2) {
                    player.sendMessage(c("<red>Verwendung: /twitchchat set <channel>"));
                    return true;
                }
                String channel = args[1];
                if (!plugin.getTwitchChatManager().isRunning()) {
                    player.sendMessage(c("<red>Kein Chat Display aktiv!"));
                    return true;
                }
                plugin.getTwitchChatManager().changeChannel(channel);
                player.sendMessage(c("<green>Twitch Chat Channel geändert zu: <white>" + channel));
            }

            default -> {
                player.sendMessage(c("<red>Unbekannter Befehl! Verwende /twitchchat für Hilfe."));
            }
        }

        return true;
    }

    private String formatLocation(Location loc) {
        return String.format("%.1f, %.1f, %.1f", loc.getX(), loc.getY(), loc.getZ());
    }
}

