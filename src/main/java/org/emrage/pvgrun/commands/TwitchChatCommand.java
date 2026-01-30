package org.emrage.pvgrun.commands;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.emrage.pvgrun.Main;
import org.emrage.pvgrun.twitch.TwitchChatDisplayManager;

import static org.emrage.pvgrun.util.MessageUtils.c;

public class TwitchChatCommand implements CommandExecutor {

    private final Main plugin;

    // wir merken uns Channel & Tracking im Command
    private String currentChannel = null;
    private String trackedPlayer = null;
    private Location currentLocation = null;

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

        TwitchChatDisplayManager manager = plugin.getTwitchChatManager();

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
            player.sendMessage(c("<gold>║ <yellow>/twitchchat move            <gold>║"));
            player.sendMessage(c("<gold>║ <gray>- Chat frei platzieren       <gold>║"));
            player.sendMessage(c("<gold>║                              <gold>║"));
            player.sendMessage(c("<gold>║ <yellow>/twitchchat set <channel>   <gold>║"));
            player.sendMessage(c("<gold>║ <gray>- Channel wechseln           <gold>║"));
            player.sendMessage(c("<gold>║                              <gold>║"));
            player.sendMessage(c("<gold>║ <yellow>/twitchchat stop            <gold>║"));
            player.sendMessage(c("<gold>║ <gray>- Chat Display beenden       <gold>║"));
            player.sendMessage(c("<gold>╚══════════════════════════════╝"));
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "start" -> {
                if (args.length < 2) {
                    player.sendMessage(c("<red>Verwendung: /twitchchat start <channel>"));
                    return true;
                }

                currentChannel = args[1];
                trackedPlayer = null;
                currentLocation = player.getLocation().add(0, 3, 0);

                manager.start(currentChannel, currentLocation, null);

                player.sendMessage(c("<green>Twitch Chat gestartet für: <white>" + currentChannel));
            }

            case "track" -> {
                if (currentChannel == null) {
                    player.sendMessage(c("<red>Starte zuerst einen Twitch Chat!"));
                    return true;
                }

                trackedPlayer = player.getName();
                currentLocation = player.getLocation().add(0, 3, 0);

                manager.start(currentChannel, currentLocation, trackedPlayer);

                player.sendMessage(c("<green>Chat folgt dir jetzt!"));
            }

            case "move" -> {
                if (currentChannel == null) {
                    player.sendMessage(c("<red>Kein Chat aktiv!"));
                    return true;
                }

                trackedPlayer = null;
                currentLocation = player.getLocation().add(0, 3, 0);

                manager.start(currentChannel, currentLocation, null);

                player.sendMessage(c("<green>Chat Display verschoben!"));
            }

            case "set" -> {
                if (args.length < 2) {
                    player.sendMessage(c("<red>Verwendung: /twitchchat set <channel>"));
                    return true;
                }

                currentChannel = args[1];
                if (currentLocation == null) {
                    currentLocation = player.getLocation().add(0, 3, 0);
                }

                manager.start(currentChannel, currentLocation, trackedPlayer);

                player.sendMessage(c("<green>Twitch Channel geändert zu: <white>" + currentChannel));
            }

            case "stop" -> {
                manager.stop();
                currentChannel = null;
                trackedPlayer = null;
                currentLocation = null;

                player.sendMessage(c("<green>Twitch Chat Display gestoppt!"));
            }

            default -> player.sendMessage(
                    c("<red>Unbekannter Befehl! Verwende <white>/twitchchat</white> für Hilfe.")
            );
        }

        return true;
    }
}
