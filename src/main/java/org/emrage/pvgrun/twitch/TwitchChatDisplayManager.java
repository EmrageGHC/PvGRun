package org.emrage.pvgrun.twitch;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.emrage.pvgrun.Main;

import java.util.*;

import static org.emrage.pvgrun.util.MessageUtils.legacy;

public class TwitchChatDisplayManager {

    private final Main plugin;
    private TwitchIRCClient ircClient;
    private Location displayLocation;
    private String trackedPlayerName; // Track player for relative positioning

    private final List<ChatLine> chatLines = new ArrayList<>();
    private final int maxLines = 10;
    private final double lineSpacing = 0.30; // Increased spacing for better readability

    private BukkitRunnable updateTask;
    private BukkitRunnable animationTask;

    public TwitchChatDisplayManager(Main plugin) {
        this.plugin = plugin;
    }

    public void start(String channelName, Location location) {
        start(channelName, location, null);
    }

    public void start(String channelName, Location location, String playerName) {
        if (ircClient != null) {
            stop();
        }

        this.displayLocation = location.clone();
        this.trackedPlayerName = playerName;

        // Connect to Twitch IRC
        ircClient = new TwitchIRCClient(plugin, channelName, this::onChatMessage);
        ircClient.connect();

        // Start update task for removing old messages
        startUpdateTask();

        // Start animation task for smooth movement
        startAnimationTask();

        plugin.getLogger().info("Started Twitch chat display for channel: " + channelName);
    }

    private void onChatMessage(TwitchIRCClient.ChatMessage msg) {
        // Format message with color coding based on badges
        String displayText = formatChatMessage(msg);

        ChatLine line = new ChatLine(displayText, msg.getTimestamp());

        // Add new message at the beginning (top)
        chatLines.add(0, line);

        // Limit to max lines and remove oldest
        while (chatLines.size() > maxLines) {
            ChatLine removed = chatLines.remove(chatLines.size() - 1);
            if (removed.armorStand != null) {
                removed.armorStand.remove();
            }
        }

        // Update all armor stands positions with animation
        updateArmorStandsAnimated();
    }

    /**
     * Format chat message with colored badges
     */
    private String formatChatMessage(TwitchIRCClient.ChatMessage msg) {
        String badges = msg.getBadges();
        String prefix = "";
        String nameColor = "§f"; // Default white

        // Check for moderator
        if (badges.contains("moderator/")) {
            prefix = "§a⚔ §r"; // Green sword
            nameColor = "§a§l"; // Green bold
        }
        // Check for VIP
        else if (badges.contains("vip/")) {
            prefix = "§d◆ §r"; // Pink diamond
            nameColor = "§d§l"; // Pink bold
        }
        // Check for subscriber
        else if (badges.contains("subscriber/")) {
            prefix = "§6★ §r"; // Gold star
            nameColor = "§6"; // Gold
        }
        // Check for broadcaster
        else if (badges.contains("broadcaster/")) {
            prefix = "§c👑 §r"; // Red crown
            nameColor = "§c§l"; // Red bold
        }

        return prefix + nameColor + msg.getUsername() + "§r§7: §f" + msg.getMessage();
    }

    /**
     * Update armor stands with smooth animation
     */
    private void updateArmorStandsAnimated() {
        if (displayLocation == null) return;

        Location baseLocation = getBaseLocation();

        for (int i = 0; i < chatLines.size(); i++) {
            ChatLine line = chatLines.get(i);

            // Calculate target position (newest at bottom, oldest at top)
            double targetYOffset = (chatLines.size() - 1 - i) * lineSpacing;
            line.targetY = baseLocation.getY() + targetYOffset;

            if (line.armorStand == null || !line.armorStand.isValid()) {
                // Create new armor stand at bottom position
                Location spawnLoc = baseLocation.clone();
                spawnLoc.setY(baseLocation.getY() - lineSpacing); // Spawn below visible area

                ArmorStand as = (ArmorStand) baseLocation.getWorld().spawnEntity(spawnLoc, EntityType.ARMOR_STAND);
                as.setVisible(false);
                as.setGravity(false);
                as.setMarker(true);
                as.setCustomNameVisible(true);
                as.customName(legacy(line.text));
                as.setInvulnerable(true);
                line.armorStand = as;
                line.currentY = spawnLoc.getY();
            }
        }
    }

    /**
     * Get base location for hologram (tracks player if set)
     */
    private Location getBaseLocation() {
        if (trackedPlayerName != null) {
            Player player = Bukkit.getPlayer(trackedPlayerName);
            if (player != null && player.isOnline()) {
                return player.getLocation().add(0, 3, 0);
            }
        }
        return displayLocation.clone();
    }

    private void updateArmorStands() {
        if (displayLocation == null) return;

        Location baseLocation = getBaseLocation();

        for (int i = 0; i < chatLines.size(); i++) {
            ChatLine line = chatLines.get(i);
            // Newest messages at bottom, oldest at top (reverse order)
            double yOffset = (chatLines.size() - 1 - i) * lineSpacing;
            Location loc = baseLocation.clone().add(0, yOffset, 0);

            if (line.armorStand == null || !line.armorStand.isValid()) {
                // Create new armor stand
                ArmorStand as = (ArmorStand) baseLocation.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
                as.setVisible(false);
                as.setGravity(false);
                as.setMarker(true);
                as.setCustomNameVisible(true);
                as.customName(legacy(line.text));
                as.setInvulnerable(true);
                line.armorStand = as;
            } else {
                // Update position
                line.armorStand.teleport(loc);
            }
        }
    }

    /**
     * Animation task for smooth upward movement
     */
    private void startAnimationTask() {
        if (animationTask != null) animationTask.cancel();

        animationTask = new BukkitRunnable() {
            @Override
            public void run() {
                Location baseLocation = getBaseLocation();

                for (ChatLine line : chatLines) {
                    if (line.armorStand != null && line.armorStand.isValid()) {
                        // Smooth interpolation
                        double diff = line.targetY - line.currentY;
                        if (Math.abs(diff) > 0.01) {
                            line.currentY += diff * 0.3; // Smooth acceleration

                            Location newLoc = line.armorStand.getLocation();
                            newLoc.setY(line.currentY);
                            newLoc.setX(baseLocation.getX());
                            newLoc.setZ(baseLocation.getZ());
                            line.armorStand.teleport(newLoc);
                        }
                    }
                }
            }
        };
        animationTask.runTaskTimer(plugin, 1L, 1L); // Run every tick for smooth animation
    }

    private void startUpdateTask() {
        if (updateTask != null) updateTask.cancel();

        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                boolean changed = false;

                // Remove messages older than 60 seconds
                Iterator<ChatLine> it = chatLines.iterator();
                while (it.hasNext()) {
                    ChatLine line = it.next();
                    if (now - line.timestamp > 60000) {
                        if (line.armorStand != null) {
                            line.armorStand.remove();
                        }
                        it.remove();
                        changed = true;
                    }
                }

                if (changed) {
                    updateArmorStands();
                }
            }
        };
        updateTask.runTaskTimer(plugin, 20L, 20L);
    }

    public void setLocation(Location location) {
        this.displayLocation = location.clone();
        this.trackedPlayerName = null; // Stop tracking player when manually setting location
        updateArmorStands();
    }

    public void setTrackedPlayer(String playerName) {
        this.trackedPlayerName = playerName;
    }

    public void stop() {
        if (ircClient != null) {
            ircClient.disconnect();
            ircClient = null;
        }

        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }

        if (animationTask != null) {
            animationTask.cancel();
            animationTask = null;
        }

        // Remove all armor stands
        for (ChatLine line : chatLines) {
            if (line.armorStand != null) {
                line.armorStand.remove();
            }
        }
        chatLines.clear();

        plugin.getLogger().info("Stopped Twitch chat display");
    }

    public boolean isRunning() {
        return ircClient != null;
    }

    public void changeChannel(String channelName) {
        Location loc = getBaseLocation();
        stop();
        start(channelName, loc, trackedPlayerName);
    }

    private static class ChatLine {
        String text;
        long timestamp;
        ArmorStand armorStand;
        double currentY; // Current Y position for animation
        double targetY;  // Target Y position for animation

        ChatLine(String text, long timestamp) {
            this.text = text;
            this.timestamp = timestamp;
            this.currentY = 0;
            this.targetY = 0;
        }
    }
}

