package org.emrage.pvgrun.managers;

import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.emrage.pvgrun.Main;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BorderManager {

    private final Main plugin;

    public static final int RADIUS = 130;
    private static final double VISIBILITY_DISTANCE = 20.0;
    private static final int MAX_BLOCKS_PER_SIDE = 140;
    private static final double Z_HALF_SPAN = 8.0;
    private static final int Z_STEPS = 20;

    private static final int TOP_OFFSET = 2;
    private static final int BOTTOM_OFFSET = 5;

    private final Map<UUID, Set<BlockKey>> shown = new ConcurrentHashMap<>();
    private BukkitRunnable periodicCleanupTask;
    private BukkitRunnable periodicUpdateTask;

    public BorderManager(Main plugin) {
        this.plugin = plugin;
        restoreLobbyBorder();
        startPeriodicCleanupTask();
        startPeriodicUpdateTask();
    }

    private void startPeriodicCleanupTask() {
        if (periodicCleanupTask != null) periodicCleanupTask.cancel();
        periodicCleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (plugin.getGameManager() == null || plugin.getGameManager().getState() != org.emrage.pvgrun. enums.GameState.RUNNING) {
                    removeAllDisplays();
                    return;
                }
                Iterator<UUID> it = shown.keySet().iterator();
                while (it.hasNext()) {
                    UUID uid = it.next();
                    Player p = Bukkit.getPlayer(uid);
                    if (p == null || !p. isOnline()) {
                        it.remove();
                    }
                }
            }
        };
        periodicCleanupTask.runTaskTimer(plugin, 20L * 20, 20L * 20);
    }

    private void startPeriodicUpdateTask() {
        if (periodicUpdateTask != null) periodicUpdateTask. cancel();
        periodicUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (plugin.getGameManager() == null || plugin.getGameManager().getState() != org.emrage.pvgrun.enums. GameState.RUNNING) {
                    return;
                }
                for (Player p : Bukkit. getOnlinePlayers()) {
                    if (! plugin.getPlayerDataManager().isExcluded(p) && !plugin.getPlayerDataManager().isDead(p)) {
                        checkPlayer(p);
                    }
                }
            }
        };
        periodicUpdateTask.runTaskTimer(plugin, 10L, 10L);
    }

    public void checkPlayer(Player player) {
        if (player == null || !player.isOnline()) return;
        var gm = plugin.getGameManager();
        if (gm == null || gm.getState() != org.emrage.pvgrun.enums.GameState.RUNNING) {
            removeDisplaysFor(player);
            return;
        }
        if (plugin.getPlayerDataManager().isExcluded(player) || plugin.getPlayerDataManager().isDead(player)) {
            removeDisplaysFor(player);
            return;
        }

        Location loc = player.getLocation();
        double px = loc.getX();
        World world = player.getWorld();
        // Im Dimension-Modus: Border auch im Nether anzeigen
        if (plugin.getConfigManager().getGameMode().equals("dimension") && world.getEnvironment() == World.Environment.NETHER) {
            // gleiche Logik wie Overworld
            if (px < -RADIUS) {
                teleportInside(player, -RADIUS + 0.5);
                return;
            }
            if (px > RADIUS) {
                teleportInside(player, RADIUS - 0.5);
                return;
            }
        } else if (!plugin.getConfigManager().getGameMode().equals("dimension") && world.getEnvironment() != World.Environment.NORMAL) {
            // Im normalen Modus keine Border im Nether
            removeDisplaysFor(player);
            return;
        }

        double distToWest = Math.abs(px - (-RADIUS));
        double distToEast = Math.abs(px - (RADIUS));

        boolean showWest = distToWest <= VISIBILITY_DISTANCE;
        boolean showEast = distToEast <= VISIBILITY_DISTANCE;

        if (! showWest && !showEast) {
            removeDisplaysFor(player);
            return;
        }

        List<Location> toShow = new ArrayList<>(MAX_BLOCKS_PER_SIDE * 2);

        double centerZ = loc.getZ();
        int playerBlockY = player.getLocation().getBlockY();

        int topY = playerBlockY + TOP_OFFSET;
        int bottomY = playerBlockY - BOTTOM_OFFSET;
        topY = Math. min(topY, world.getMaxHeight() - 1);
        bottomY = Math.max(bottomY, 1);

        double zCenter = Math.round(centerZ);
        double zStep = Math.max(0.5, (Z_HALF_SPAN * 2) / Math.max(1, Z_STEPS));

        if (showWest) {
            int added = 0;
            for (double z = zCenter - Z_HALF_SPAN; z <= zCenter + Z_HALF_SPAN && added < MAX_BLOCKS_PER_SIDE; z += zStep) {
                for (int y = topY; y >= bottomY && added < MAX_BLOCKS_PER_SIDE; y--) {
                    Location blockLoc = new Location(world, -RADIUS, y, (int) Math.round(z));
                    toShow.add(blockLoc);
                    added++;
                }
            }
        }

        if (showEast) {
            int added = 0;
            for (double z = zCenter - Z_HALF_SPAN; z <= zCenter + Z_HALF_SPAN && added < MAX_BLOCKS_PER_SIDE; z += zStep) {
                for (int y = topY; y >= bottomY && added < MAX_BLOCKS_PER_SIDE; y--) {
                    Location blockLoc = new Location(world, RADIUS, y, (int) Math.round(z));
                    toShow.add(blockLoc);
                    added++;
                }
            }
        }

        updatePlayerBlocks(player, toShow);
    }

    private void updatePlayerBlocks(Player player, Collection<Location> positions) {
        UUID uid = player.getUniqueId();
        Set<BlockKey> newSet = new HashSet<>();
        for (Location loc : positions) {
            if (loc == null || loc.getWorld() == null) continue;
            newSet.add(BlockKey.of(loc));
        }

        Set<BlockKey> oldSet = shown.getOrDefault(uid, Collections.emptySet());

        Set<BlockKey> toRemove = new HashSet<>(oldSet);
        toRemove.removeAll(newSet);

        Set<BlockKey> toAdd = new HashSet<>(newSet);
        toAdd.removeAll(oldSet);

        for (BlockKey bk : toRemove) revertBlockForPlayer(player, bk);

        // Use GRAY_STAINED_GLASS instead of BARRIER
        BlockData glassBlock = Bukkit.createBlockData(Material.GRAY_STAINED_GLASS);
        for (BlockKey bk : toAdd) {
            Location loc = bk.toLocation();
            if (loc == null) continue;
            try {
                player.sendBlockChange(loc, glassBlock);
            } catch (Exception ignored) {}
        }

        if (newSet.isEmpty()) shown.remove(uid);
        else shown.put(uid, newSet);
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

    public boolean isLocationShownForPlayer(Player player, Location loc) {
        if (player == null || loc == null) return false;
        UUID uid = player.getUniqueId();
        Set<BlockKey> set = shown.get(uid);
        if (set == null || set.isEmpty()) return false;
        BlockKey key = BlockKey.of(loc);
        return set.contains(key);
    }

    public void resendBarrierAt(Player player, Location loc) {
        if (player == null || loc == null || loc.getWorld() == null) return;
        try {
            BlockData glassBlock = Bukkit.createBlockData(Material.GRAY_STAINED_GLASS);
            player.sendBlockChange(loc, glassBlock);
        } catch (Exception ignored) {}
    }

    public void removeDisplaysFor(Player player) {
        if (player == null) return;
        UUID uid = player.getUniqueId();
        Set<BlockKey> set = shown. remove(uid);
        if (set == null || set.isEmpty()) return;
        for (BlockKey bk : set) revertBlockForPlayer(player, bk);
    }

    public void removeAllDisplays() {
        for (Map.Entry<UUID, Set<BlockKey>> e : new HashMap<>(shown).entrySet()) {
            UUID uid = e. getKey();
            Player p = Bukkit.getPlayer(uid);
            Set<BlockKey> set = e.getValue();
            if (p != null && p. isOnline()) {
                for (BlockKey bk : set) revertBlockForPlayer(p, bk);
            }
        }
        shown.clear();
    }

    private void teleportInside(Player p, double targetX) {
        World world = p.getWorld();
        double z = p.getLocation().getZ();
        int safeY = world.getHighestBlockYAt((int) Math.floor(targetX), (int) Math.floor(z)) + 1;
        Location tp = new Location(world, targetX, safeY, z, p.getLocation().getYaw(), p.getLocation().getPitch());
        p.teleport(tp);
        p.setFallDistance(0f);
        p.setNoDamageTicks(40);
        p.playSound(p. getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.0f);
    }

    public void restoreLobbyBorder() {
        removeAllDisplays();
        World world = Bukkit.getWorld("world");
        if (world == null) return;
        WorldBorder border = world.getWorldBorder();
        border.setCenter(0, 0);
        border.setSize(100.0);
        border.setWarningDistance(5);
        border.setDamageAmount(0);
    }

    public void shutdown() {
        if (periodicCleanupTask != null) {
            periodicCleanupTask. cancel();
            periodicCleanupTask = null;
        }
        if (periodicUpdateTask != null) {
            periodicUpdateTask.cancel();
            periodicUpdateTask = null;
        }
        removeAllDisplays();
    }

    private record BlockKey(String world, int x, int y, int z) {
        static BlockKey of(Location loc) {
            return new BlockKey(Objects.requireNonNull(loc.getWorld()).getName(),
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        }

        Location toLocation() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z);
        }
    }
}