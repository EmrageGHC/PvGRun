package org.emrage.pvgrun.listeners;

import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

import static org.emrage.pvgrun.util.MessageUtils.c;

public class ServerListPingListener implements Listener {

    @EventHandler
    public void onPing(ServerListPingEvent event) {
        Component motd = c("<reset>          <bold><dark_purple>DEATHRUN</dark_purple></bold> <gray>-</gray> <yellow>Laufe nach</yellow> <bold><red>NORDEN</red></bold>\n<reset>                  <aqua>1.21.4</aqua> <gray>|</gray> <red>Emrage-Run</red>");
        event.motd(motd);
    }
}