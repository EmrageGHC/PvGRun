package org.emrage.pvgrun.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.*;

public class TeamManager {
    public static class Team {
        private final UUID player1;
        private final UUID player2;
        private String name;
        private String dimension; // "overworld" oder "nether"
        private final Set<UUID> alive;
        private final List<UUID> members;
        private final Map<UUID, String> playerDimension = new HashMap<>();

        public Team(UUID p1, UUID p2, String name) {
            this.player1 = p1;
            this.player2 = p2;
            this.name = name;
            this.dimension = null;
            this.alive = new HashSet<>(Arrays.asList(p1, p2));
            this.members = Arrays.asList(p1, p2);
        }
        public List<UUID> getMembers() { return members; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDimension() { return dimension; }
        public void setDimension(String dim) { this.dimension = dim; }
        public boolean isAlive(UUID uuid) { return alive.contains(uuid); }
        public void setDead(UUID uuid) { alive.remove(uuid); }
        public boolean isTeamAlive() { return !alive.isEmpty(); }
        public void setPlayerDimension(UUID uuid, String dim) { playerDimension.put(uuid, dim); }
        public String getPlayerDimension(UUID uuid) { return playerDimension.getOrDefault(uuid, null); }
    }

    private final Map<UUID, Team> playerToTeam = new HashMap<>();
    private final List<Team> teams = new ArrayList<>();
    private final Map<UUID, UUID> pendingRequests = new HashMap<>(); // anfragender -> angefragter

    public boolean hasTeam(Player p) { return playerToTeam.containsKey(p.getUniqueId()); }
    public Team getTeam(Player p) { return playerToTeam.get(p.getUniqueId()); }
    public List<Team> getTeams() { return teams; }
    public void sendTeamRequest(Player from, Player to) { pendingRequests.put(from.getUniqueId(), to.getUniqueId()); }
    public boolean hasPendingRequest(Player from, Player to) { return pendingRequests.getOrDefault(from.getUniqueId(), null) != null && pendingRequests.get(from.getUniqueId()).equals(to.getUniqueId()); }
    public void removePendingRequest(Player from) { pendingRequests.remove(from.getUniqueId()); }
    public void acceptTeamRequest(Player from, Player to) {
        if (!hasPendingRequest(from, to)) return;
        String teamName = generateTeamName(from.getName(), to.getName());
        Team team = new Team(from.getUniqueId(), to.getUniqueId(), teamName);
        teams.add(team);
        playerToTeam.put(from.getUniqueId(), team);
        playerToTeam.put(to.getUniqueId(), team);
        removePendingRequest(from);
    }
    public void declineTeamRequest(Player from, Player to) { removePendingRequest(from); }
    public void removeTeam(Team team) {
        for (UUID uuid : team.getMembers()) playerToTeam.remove(uuid);
        teams.remove(team);
    }
    public void removeTeamByPlayer(Player p) {
        Team t = getTeam(p);
        if (t != null) removeTeam(t);
    }
    public static String generateTeamName(String n1, String n2) {
        String part1 = n1.length() >= 3 ? n1.substring(0, 3) : n1;
        String part2 = n2.length() >= 3 ? n2.substring(n2.length() - 3) : n2;
        return part1 + part2;
    }
    public Map<UUID, Team> getPlayerToTeamMap() { return playerToTeam; }
}
