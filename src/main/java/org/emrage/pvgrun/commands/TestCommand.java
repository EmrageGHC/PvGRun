package org.emrage.pvgrun.commands;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.emrage.pvgrun.Main;
import org.emrage.pvgrun.util.HeadUtil;

import java.time.Duration;

import static org.emrage.pvgrun.util.MessageUtils.c;

public class TestCommand implements CommandExecutor {

    private final Main plugin;

    public TestCommand(Main plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Only in-game.");
            return true;
        }

        Title.Times times = Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(200));

        for (Player target : Bukkit.getOnlinePlayers()) {
            var headComp = HeadUtil.getHead(target);
            var subtitle = c("<gray>Resourcepack aktiv?</gray>");
            target.showTitle(Title.title(headComp, subtitle, times));
        }
        p.sendMessage(c("<#55FF55>Test-Titles an alle gesendet."));
        return true;
    }
}