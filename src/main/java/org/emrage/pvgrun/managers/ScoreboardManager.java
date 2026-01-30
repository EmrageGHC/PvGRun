package org.emrage.pvgrun.managers;

import fr.mrmicky.fastboard.adventure.FastBoard;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.emrage.pvgrun.Main;
import org.emrage.pvgrun.enums.GameState;
import org.emrage.pvgrun.util.HeadUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ScoreboardManager {

    private final Main plugin;
    private final Map<UUID, FastBoard> boards = new ConcurrentHashMap<>();

    public ScoreboardManager(Main plugin) {
        this.plugin = plugin;
    }

    /* -------------------------------------------------- */
    /*  Update                                            */
    /* -------------------------------------------------- */

    public void updateAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.getGameManager().getState() == GameState.RUNNING) {
                updateFor(p);
            } else {
                remove(p);
            }
        }
    }

    public void updateFor(Player viewer) {
        FastBoard board = boards.computeIfAbsent(viewer.getUniqueId(), id -> {
            FastBoard b = new FastBoard(viewer);
            b.updateTitle(
                    Component.text("DEATHRUN", NamedTextColor.GRAY)
                            .decorate(TextDecoration.BOLD)
            );
            return b;
        });

        board.updateLines(buildLines(viewer));
    }

    public void remove(Player p) {
        FastBoard board = boards.remove(p.getUniqueId());
        if (board != null) board.delete();
    }

    /* -------------------------------------------------- */
    /*  Lines                                             */
    /* -------------------------------------------------- */

    private List<Component> buildLines(Player viewer) {
        List<Component> lines = new ArrayList<>();

        boolean duo = plugin.getGameManager().isDuoRun();

        lines.add(Component.empty());

        lines.add(
                Component.text(duo ? "ODD BIOME" : "DEATHRUN", NamedTextColor.GREEN)
                        .decorate(TextDecoration.BOLD)
        );

        lines.add(Component.empty());

        if (duo) {
            buildDuo(lines, viewer);
        } else {
            buildSolo(lines, viewer);
        }

        return lines;
    }

    /* -------------------------------------------------- */
    /*  SOLO                                              */
    /* -------------------------------------------------- */

    private void buildSolo(List<Component> lines, Player viewer) {
        List<Player> ranking = plugin.getGameManager().getSoloSorted();
        int limit = Math.min(5, ranking.size());

        for (int i = 0; i < limit; i++) {
            Player p = ranking.get(i);
            lines.add(renderSoloEntry(i, p, p.equals(viewer)));
        }
    }

    private Component renderSoloEntry(int index, Player p, boolean own) {
        int place = index + 1;
        long score = Math.round(plugin.getGameManager().getDistance(p));

        NamedTextColor color;
        boolean bold;

        if (own) {
            color = NamedTextColor.WHITE;
            bold = true;
        } else if (place <= 3) {
            color = NamedTextColor.GREEN;
            bold = true;
        } else {
            color = NamedTextColor.GRAY;
            bold = false;
        }

        Component line = Component.text(place + ". ", NamedTextColor.WHITE)
                .append(HeadUtil.getHead(p))
                .append(Component.space())
                .append(
                        Component.text(p.getName(), color)
                                .decorate(bold ? TextDecoration.BOLD : TextDecoration.NONE)
                )
                .append(Component.text("        "))
                .append(
                        Component.text(String.valueOf(score), color)
                                .decorate(bold ? TextDecoration.BOLD : TextDecoration.NONE)
                );

        return line;
    }

    /* -------------------------------------------------- */
    /*  DUO / DIMENSION                                   */
    /* -------------------------------------------------- */

    private void buildDuo(List<Component> lines, Player viewer) {
        List<Team> teams = plugin.getGameManager().getTeamsSorted();
        Team ownTeam = plugin.getGameManager().getTeam(viewer);
        int limit = Math.min(5, teams.size());

        for (int i = 0; i < limit; i++) {
            Team team = teams.get(i);
            lines.add(renderTeamEntry(i, team, team.equals(ownTeam)));
        }
    }

    private Component renderTeamEntry(int index, Team team, boolean own) {
        int place = index + 1;
        long score = Math.round(plugin.getGameManager().getTeamDistance(team));

        NamedTextColor color;
        boolean bold;

        if (own) {
            color = NamedTextColor.WHITE;
            bold = true;
        } else if (place <= 3) {
            color = NamedTextColor.GREEN;
            bold = true;
        } else {
            color = NamedTextColor.GRAY;
            bold = false;
        }

        Component line = Component.text(place + ". ", NamedTextColor.WHITE);

        for (Player p : team.getPlayers()) {
            line = line.append(HeadUtil.getHead(p));
        }

        line = line.append(Component.space())
                .append(
                        Component.text(team.getName(), color)
                                .decorate(bold ? TextDecoration.BOLD : TextDecoration.NONE)
                )
                .append(Component.text("        "))
                .append(
                        Component.text(String.valueOf(score), color)
                                .decorate(bold ? TextDecoration.BOLD : TextDecoration.NONE)
                );

        return line;
    }
}
