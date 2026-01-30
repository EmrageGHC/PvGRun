package org.emrage.pvgrun.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Lightweight wrapper used by ScoreboardManager to represent a team of Players
public class Team {
    private final TeamManager.Team inner;

    public Team(TeamManager.Team inner) {
        this.inner = inner;
    }

    public String getName() {
        return inner.getName();
    }

    public List<Player> getPlayers() {
        List<Player> out = new ArrayList<>();
        for (UUID u : inner.getMembers()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) out.add(p);
        }
        return out;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Team team = (Team) o;
        return getName() != null ? getName().equals(team.getName()) : team.getName() == null;
    }

    @Override
    public int hashCode() {
        return getName() != null ? getName().hashCode() : 0;
    }
}

