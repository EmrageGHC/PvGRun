package org.emrage.pvgrun.util;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Renders temporary glass (or chosen material) borders near configured axes when players come near.
 */
public class BorderUtil {

    private static final double DEFAULT_RADIUS = 5.0;

    // Tracks fake blocks per-player, keyed by a border identifier (e.g., "X:100:GLASS")
    private final Map<UUID, Map<String, Set<Location>>> fakeBlocksByPlayer = new HashMap<>();



    /**
     * Render a border near the given axis/line with default material (GLASS) and default radius.
     *
     * @param axis   "X", "Y", or "Z"
     * @param line   coordinate value for that axis
     * @param player player to render for
     */
    public void border(String axis, double line, Player player) {
        border(axis, line, player, Material.GLASS, DEFAULT_RADIUS);
    }

    /**
     * Render a border near the given axis/line with a chosen material and default radius.
     *
     * @param axis     "X", "Y", or "Z"
     * @param line     coordinate value for that axis
     * @param player   player to render for
     * @param material material to show (non-collidable recommended)
     */
    public void border(String axis, double line, Player player, Material material) {
        border(axis, line, player, material, DEFAULT_RADIUS);
    }

    /**
     * Render a border near the given axis/line with full control.
     *
     * @param axis     "X", "Y", or "Z"
     * @param line     coordinate value for that axis
     * @param player   player to render for
     * @param material material to show (non-collidable recommended)
     * @param radius   spherical radius to include blocks
     */
    public void border(String axis, double line, Player player, Material material, double radius) {
        if (player == null || material == null || radius <= 0) return;

        Location center = player.getLocation();

        String upperAxis = axis == null ? "" : axis.trim().toUpperCase(Locale.ROOT);
        if (!upperAxis.equals("X") && !upperAxis.equals("Y") && !upperAxis.equals("Z")) return;

        double playerCoord = switch (upperAxis) {
            case "X" -> center.getX();
            case "Y" -> center.getY();
            default -> center.getZ();
        };

        if (Math.abs(playerCoord - line) > radius) {
            clearBorder(player, borderKey(upperAxis, line, material));
            return;
        }

        updateBorder(player, upperAxis, line, center, material, radius);
    }

    private void updateBorder(Player player, String axis, double line, Location center, Material material, double radius) {
        String key = borderKey(axis, line, material);
        Map<String, Set<Location>> perPlayer = fakeBlocksByPlayer.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        Set<Location> current = perPlayer.getOrDefault(key, Collections.emptySet());
        Set<Location> next = new HashSet<>();

        World world = player.getWorld();
        Vector centerVec = center.toVector();

        int maxOffset = (int) Math.ceil(radius);
        double radiusSq = radius * radius;

        for (int dx = -maxOffset; dx <= maxOffset; dx++) {
            for (int dy = -maxOffset; dy <= maxOffset; dy++) {
                for (int dz = -maxOffset; dz <= maxOffset; dz++) {

                    Location loc = center.clone().add(dx, dy, dz).getBlock().getLocation();

                    // Axis filter: keep only blocks close to the target line on the chosen axis
                    boolean onAxis = switch (axis) {
                        case "X" -> Math.abs(loc.getX() - line) <= 0.5;
                        case "Y" -> Math.abs(loc.getY() - line) <= 0.5;
                        default -> Math.abs(loc.getZ() - line) <= 0.5;
                    };
                    if (!onAxis) continue;

                    // Spherical distance to block center
                    Vector blockCenter = loc.toVector().add(new Vector(0.5, 0.5, 0.5));
                    if (centerVec.distanceSquared(blockCenter) > radiusSq) continue;

                    next.add(loc);
                }
            }
        }

        // Diff sets
        Set<Location> toAdd = new HashSet<>(next);
        toAdd.removeAll(current);

        Set<Location> toRemove = new HashSet<>(current);
        toRemove.removeAll(next);

        // Apply additions
        for (Location loc : toAdd) {
            if (!loc.getBlock().getBlockData().getMaterial().isCollidable()) {
                player.sendBlockChange(loc, material.createBlockData());
            }
        }

        // Apply removals
        for (Location loc : toRemove) {
            Block real = world.getBlockAt(loc);
            player.sendBlockChange(loc, real.getBlockData());
        }

        // Store updated set
        perPlayer.put(key, next);
    }

    private void clearBorder(Player player, String key) {
        Map<String, Set<Location>> perPlayer = fakeBlocksByPlayer.get(player.getUniqueId());
        if (perPlayer == null) return;

        Set<Location> set = perPlayer.remove(key);
        if (set == null || set.isEmpty()) return;

        World world = player.getWorld();
        for (Location loc : set) {
            Block real = world.getBlockAt(loc);
            player.sendBlockChange(loc, real.getBlockData());
        }

        if (perPlayer.isEmpty()) {
            fakeBlocksByPlayer.remove(player.getUniqueId());
        }
    }

    private String borderKey(String axis, double line, Material mat) {
        return axis + ":" + line + ":" + mat.name();
    }
}