package org.emrage.pvgrun.managers;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class BanManager {

    private final Set<String> banned = Collections.synchronizedSet(new HashSet<>());

    public boolean isBanned(String name) { return banned.contains(name); }
    public void ban(String name) { banned.add(name); }
    public void unban(String name) { banned.remove(name); }
    public void unbanAll() { banned.clear(); }
    public Set<String> getBannedPlayers() { return new HashSet<>(banned); }
}