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
    // Players who opted into scoreboard preview (via command)
    private final Set<UUID> previewing = ConcurrentHashMap.newKeySet();

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

    // Compatibility wrapper used by GameManager
    public void updateAllForActive() {
        for (Player p : plugin.getGameManager().activePlayers()) {
            updateFor(p);
        }
    }

    // Compatibility wrapper used by GameManager
    public void removeAll() {
        for (UUID id : new ArrayList<>(boards.keySet())) {
            FastBoard b = boards.remove(id);
            if (b != null) b.delete();
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

    public void addForPlayer(Player p) {
        boards.computeIfAbsent(p.getUniqueId(), id -> {
            FastBoard b = new FastBoard(p);
            b.updateTitle(
                    Component.text("DEATHRUN", NamedTextColor.GRAY)
                            .decorate(TextDecoration.BOLD)
            );
            return b;
        });
        // Update lines now (no-op if not applicable)
        try { updateFor(p); } catch (Exception ignored) {}
    }

    // Toggle preview for a player (command): when enabled, show the scoreboard regardless of game state
    public void togglePreview(Player p) {
        UUID id = p.getUniqueId();
        if (previewing.contains(id)) {
            previewing.remove(id);
            // hide scoreboard if game not running
            if (plugin.getGameManager() == null || plugin.getGameManager().getState() != org.emrage.pvgrun.enums.GameState.RUNNING) {
                remove(p);
            } else {
                updateFor(p);
            }
        } else {
            previewing.add(id);
            addForPlayer(p);
        }
    }

    public boolean isPreviewing(Player p) {
        return previewing.contains(p.getUniqueId());
    }

    // Backwards-compatible wrapper used by listeners/commands
    public void removePlayer(Player p) {
        remove(p);
    }

    /* -------------------------------------------------- */
    /*  Lines                                             */
    /* -------------------------------------------------- */

    private List<Component> buildLines(Player viewer) {
        List<Component> lines = new ArrayList<>();

        // Determine duo (dimension) mode directly from config to avoid GameManager wrapper issues
        boolean duo = plugin.getConfigManager().getGameMode().equals("dimension");

        lines.add(Component.empty());

        // Title is already set on the board; avoid repeating it here.
        // If desired, additional subtitle could be shown here.

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
        // get active players and sort by stored distance descending
        List<Player> ranking = new ArrayList<>(plugin.getGameManager().activePlayers());
        ranking.sort(Comparator.comparingDouble((Player p) -> plugin.getPlayerDataManager().getStoredDistance(p.getName())).reversed());
        int limit = Math.min(5, ranking.size());

        // Top 5 block
        for (int i = 0; i < limit; i++) {
            Player p = ranking.get(i);
            lines.add(renderSoloEntryTop(i, p, p.equals(viewer)));
        }

        // Leerzeile
        lines.add(Component.empty());

        // Nachbarschaft: Platz vor einem, eigener Platz, Platz nach einem
        int viewerIndex = ranking.indexOf(viewer);
        if (viewerIndex != -1) {
            if (viewerIndex - 1 >= 0) {
                Player before = ranking.get(viewerIndex - 1);
                lines.add(renderSoloEntryBelow(viewerIndex - 1, before, false, viewer));
            }

            Player self = ranking.get(viewerIndex);
            lines.add(renderSoloEntryBelow(viewerIndex, self, true, viewer));

            if (viewerIndex + 1 < ranking.size()) {
                Player after = ranking.get(viewerIndex + 1);
                lines.add(renderSoloEntryBelow(viewerIndex + 1, after, false, viewer));
            }
        }
    }

    private Component renderSoloEntryTop(int index, Player p, boolean own) {
        int place = index + 1;
        long score = Math.round(plugin.getPlayerDataManager().getStoredDistance(p.getName()));

        NamedTextColor nameColor;
        NamedTextColor scoreColor = NamedTextColor.LIGHT_PURPLE; // scores should be pink but not bold
        boolean boldName = false;

        if (own) {
            nameColor = NamedTextColor.WHITE;
            boldName = true;
        } else if (place <= 3) {
            nameColor = NamedTextColor.GREEN; // hellgrün-ähnlich
            boldName = true;
        } else {
            nameColor = NamedTextColor.GRAY;
        }

        Component nameComp = Component.text(p.getName(), nameColor);
        if (boldName) nameComp = nameComp.decorate(TextDecoration.BOLD);

        // Scores should not be bold per request
        Component scoreComp = Component.text(String.valueOf(score), scoreColor);

        Component line = Component.text(place + ". ", NamedTextColor.WHITE)
                .append(HeadUtil.getHead(p, plugin.getPlayerDataManager().isDead(p)))
                .append(Component.space())
                .append(nameComp)
                .append(Component.text("        "))
                .append(scoreComp);

        return line;
    }

    private Component renderSoloEntryBelow(int index, Player p, boolean isOwnEntry, Player viewer) {
        int place = index + 1;
        long score = Math.round(plugin.getPlayerDataManager().getStoredDistance(p.getName()));

        Component nameComp;
        Component scoreComp;

        if (isOwnEntry) {
            nameComp = Component.text(p.getName(), NamedTextColor.WHITE).decorate(TextDecoration.BOLD);
            scoreComp = Component.text(String.valueOf(score), NamedTextColor.LIGHT_PURPLE); // not bold
        } else {
            nameComp = Component.text(p.getName(), NamedTextColor.GRAY).decorate(TextDecoration.ITALIC);
            // Other players' names stay gray italic, but scores should be pink (not bold)
            scoreComp = Component.text(String.valueOf(score), NamedTextColor.LIGHT_PURPLE);
        }

        Component line = Component.text(place + ". ", NamedTextColor.WHITE)
                .append(HeadUtil.getHead(p, plugin.getPlayerDataManager().isDead(p)))
                .append(Component.space())
                .append(nameComp)
                .append(Component.text("        "))
                .append(scoreComp);

        return line;
    }

    /* -------------------------------------------------- */
    /*  DUO / DIMENSION                                   */
    /* -------------------------------------------------- */

    private void buildDuo(List<Component> lines, Player viewer) {
        List<org.emrage.pvgrun.managers.TeamManager.Team> teams = new ArrayList<>(plugin.getTeamManager().getTeams());
        // sort by team distance descending
        teams.sort(Comparator.comparingDouble((org.emrage.pvgrun.managers.TeamManager.Team t) -> -getTeamDistance(t)));

        org.emrage.pvgrun.managers.TeamManager.Team ownTeam = plugin.getTeamManager().getTeam(viewer);

        int limit = Math.min(5, teams.size());

        // Top 5 block
        for (int i = 0; i < limit; i++) {
            org.emrage.pvgrun.managers.TeamManager.Team team = teams.get(i);
            lines.add(renderTeamEntry(i, team, ownTeam != null && team.equals(ownTeam)));
        }

        // Leerzeile
        lines.add(Component.empty());

        // Neighborhood: show previous team, own team, next team (if viewer is in a team)
        if (ownTeam != null) {
            int idx = -1;
            for (int i = 0; i < teams.size(); i++) if (teams.get(i).equals(ownTeam)) { idx = i; break; }
            if (idx != -1) {
                if (idx - 1 >= 0) lines.add(renderTeamEntry(idx - 1, teams.get(idx - 1), false));
                lines.add(renderTeamEntry(idx, teams.get(idx), true));
                if (idx + 1 < teams.size()) lines.add(renderTeamEntry(idx + 1, teams.get(idx + 1), false));
            }
        }
    }

    private Component renderTeamEntry(int index, org.emrage.pvgrun.managers.TeamManager.Team team, boolean own) {
        int place = index + 1;
        long score = Math.round(getTeamDistance(team));

        NamedTextColor nameColor;
        NamedTextColor scoreColor = NamedTextColor.LIGHT_PURPLE;
        boolean boldName = false;

        if (own) {
            nameColor = NamedTextColor.WHITE;
            boldName = true;
        } else if (place <= 3) {
            nameColor = NamedTextColor.GREEN;
            boldName = true;
        } else {
            nameColor = NamedTextColor.GRAY;
        }

        // Build line: place + heads (up to 2) + space + TeamName + spacer + Score
        Component line = Component.text(place + ". ", NamedTextColor.WHITE);

        // Append up to two player heads (or 1). Keep order as in team members.
        List<Component> headComps = new ArrayList<>();
        for (UUID u : team.getMembers()) {
            if (headComps.size() >= 2) break;
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                headComps.add(HeadUtil.getHead(p, plugin.getPlayerDataManager().isDead(p)));
            }
        }
        // Build head area with separator ("/") if two heads, or single head with padding
        if (headComps.size() == 2) {
            line = line.append(headComps.get(0))
                    .append(Component.space())
                    .append(Component.text("/", NamedTextColor.GRAY))
                    .append(Component.space())
                    .append(headComps.get(1));
        } else if (headComps.size() == 1) {
            line = line.append(headComps.get(0)).append(Component.space()).append(Component.text(" "));
        } else {
            // No players online in this team - add placeholders
            line = line.append(Component.text("[—] "));
        }

        Component nameComp = Component.text(team.getName(), nameColor);
        if (boldName) nameComp = nameComp.decorate(TextDecoration.BOLD);

        // Team scores should not be bold
        Component scoreComp = Component.text(String.valueOf(score), scoreColor);

        line = line.append(Component.space())
                .append(nameComp)
                .append(Component.text("        "))
                .append(scoreComp);

        return line;
    }

    private double getTeamDistance(org.emrage.pvgrun.managers.TeamManager.Team team) {
        double sum = 0;
        for (UUID u : team.getMembers()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) sum += plugin.getPlayerDataManager().getStoredDistance(p.getName());
        }
        return sum;
    }
}
