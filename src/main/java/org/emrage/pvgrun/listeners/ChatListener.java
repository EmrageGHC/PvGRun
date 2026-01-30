package org.emrage.pvgrun.listeners;

import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.entity.Player;
import org.emrage.pvgrun.Main;

public class ChatListener implements Listener {
    private final Main plugin;
    public ChatListener(Main plugin) { this.plugin = plugin; }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player p = event.getPlayer();
        String format;
        if (plugin.getConfigManager().getGameMode().equals("dimension") && plugin.getTeamManager().hasTeam(p)) {
            var team = plugin.getTeamManager().getTeam(p);
            String prefix = "§7[§a§l" + team.getName() + "§7]§r ";
            format = prefix + "%1$s: %2$s";
        } else {
            format = "%1$s: %2$s";
        }
        event.setFormat(format);
    }
}

