package org.emrage.pvgrun.managers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.Material;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BorderDisplayManager {

    private final Map<UUID, Set<BlockKey>> shown = new ConcurrentHashMap<>();

    public record BlockKey(String world, int x, int y, int z) {
        public static BlockKey of(Location loc) {
            return new BlockKey(Objects.requireNonNull(loc.getWorld()).getName(),
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        }

        public Location toLocation() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z);
        }
    }

    /**
     * Show the given block positions as BARRIER to the specific player.
     * Any previously shown blocks that are not in `positions` will be reverted.
     */
    public void showBlocksFor(Player player, Collection<Location> positions) {
        if (player == null || !player.isOnline()) return;
        UUID uuid = player.getUniqueId();

        Set<BlockKey> newSet = new HashSet<>();
        for (Location loc : positions) {
            if (loc == null || loc.getWorld() == null) continue;
            newSet.add(BlockKey.of(loc));
        }

        Set<BlockKey> oldSet = shown.getOrDefault(uuid, Collections.emptySet());

        // toRemove = old - new
        Set<BlockKey> toRemove = new HashSet<>(oldSet);
        toRemove.removeAll(newSet);

        // toAdd = new - old
        Set<BlockKey> toAdd = new HashSet<>(newSet);
        toAdd.removeAll(oldSet);

        // Revert removed
        for (BlockKey bk : toRemove) {
            revertBlockForPlayer(player, bk);
        }

        // Add new (send barrier)
        BlockData barrier = Bukkit.createBlockData(Material.BARRIER);
        for (BlockKey bk : toAdd) {
            Location loc = bk.toLocation();
            if (loc == null) continue;
            try {
                player.sendBlockChange(loc, barrier);
            } catch (Exception ignored) {}
        }

        if (newSet.isEmpty()) shown.remove(uuid);
        else shown.put(uuid, newSet);
    }

    /**
     * Revert all shown blocks for a player and remove its entry.
     */
    public void removeDisplaysFor(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        Set<BlockKey> old = shown.remove(uuid);
        if (old == null || old.isEmpty()) return;
        for (BlockKey bk : old) {
            revertBlockForPlayer(player, bk);
        }
    }

    /**
     * Revert everything for all players (used on reset/disable).
     */
    public void removeAll() {
        for (Map.Entry<UUID, Set<BlockKey>> e : new HashMap<>(shown).entrySet()) {
            UUID uuid = e.getKey();
            Player p = Bukkit.getPlayer(uuid);
            Set<BlockKey> set = e.getValue();
            if (p != null && p.isOnline()) {
                for (BlockKey bk : set) revertBlockForPlayer(p, bk);
            } else {
                // If player offline, nothing to revert clientside
            }
        }
        shown.clear();
    }

    private void revertBlockForPlayer(Player player, BlockKey bk) {
        Location loc = bk.toLocation();
        if (loc == null) return;
        World world = loc.getWorld();
        if (world == null) return;
        try {
            BlockData real = world.getBlockAt(loc).getBlockData();
            player.sendBlockChange(loc, real);
        } catch (Exception ignored) {}
    }
}