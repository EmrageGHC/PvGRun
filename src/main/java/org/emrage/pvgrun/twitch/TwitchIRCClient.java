package org.emrage.pvgrun.twitch;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.emrage.pvgrun.Main;

import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;

public class TwitchIRCClient {

    private final Main plugin;
    private final String channel;
    private final Consumer<ChatMessage> messageHandler;

    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private BukkitRunnable readTask;
    private boolean running = false;

    public TwitchIRCClient(Main plugin, String channel, Consumer<ChatMessage> messageHandler) {
        this.plugin = plugin;
        this.channel = channel.toLowerCase().replace("#", "");
        this.messageHandler = messageHandler;
    }

    public void connect() {
        if (running) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    socket = new Socket("irc.chat.twitch.tv", 6667);
                    reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

                    // Request tags capability for badges
                    send("CAP REQ :twitch.tv/tags");

                    // Anonymous login (no auth needed for reading public chat)
                    send("NICK justinfan12345");
                    send("USER justinfan12345 8 * :justinfan12345");

                    Thread.sleep(1000);

                    // Join channel
                    send("JOIN #" + channel);

                    running = true;
                    plugin.getLogger().info("Connected to Twitch chat: " + channel);

                    startReadLoop();

                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to connect to Twitch IRC: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private void startReadLoop() {
        readTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    String line;
                    while (running && (line = reader.readLine()) != null) {
                        processLine(line);
                    }
                } catch (IOException e) {
                    if (running) {
                        plugin.getLogger().warning("Twitch IRC connection lost: " + e.getMessage());
                        reconnect();
                    }
                }
            }
        };
        readTask.runTaskAsynchronously(plugin);
    }

    private void processLine(String line) {
        // Respond to PING
        if (line.startsWith("PING")) {
            send("PONG :tmi.twitch.tv");
            return;
        }

        // Parse PRIVMSG (chat messages)
        // Format: @badge-info=;badges=moderator/1;... :username!username@username.tmi.twitch.tv PRIVMSG #channel :message
        if (line.contains("PRIVMSG")) {
            try {
                String username;
                String message;
                String badges = "";

                // Extract badges if present
                if (line.startsWith("@")) {
                    int badgeStart = line.indexOf("badges=");
                    if (badgeStart != -1) {
                        int badgeEnd = line.indexOf(";", badgeStart);
                        if (badgeEnd != -1) {
                            badges = line.substring(badgeStart + 7, badgeEnd);
                        }
                    }

                    // Extract username
                    int userStart = line.indexOf(":", 1);
                    if (userStart != -1) {
                        username = line.substring(userStart + 1, line.indexOf('!', userStart));
                    } else {
                        return; // Malformed message
                    }
                } else {
                    // Old format without badges
                    username = line.substring(1, line.indexOf('!'));
                }

                // Extract message
                message = line.substring(line.indexOf(':', line.indexOf("PRIVMSG")) + 1);

                ChatMessage chatMsg = new ChatMessage(username, message, badges);

                // Call handler on main thread
                Bukkit.getScheduler().runTask(plugin, () -> messageHandler.accept(chatMsg));

            } catch (Exception e) {
                // Ignore malformed messages
            }
        }
    }

    private void send(String message) {
        try {
            writer.write(message + "\r\n");
            writer.flush();
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to send IRC message: " + e.getMessage());
        }
    }

    private void reconnect() {
        disconnect();
        Bukkit.getScheduler().runTaskLater(plugin, this::connect, 100L); // Wait 5 seconds
    }

    public void disconnect() {
        running = false;

        if (readTask != null) {
            readTask.cancel();
            readTask = null;
        }

        try {
            if (writer != null) {
                send("PART #" + channel);
                writer.close();
            }
            if (reader != null) reader.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            // Ignore
        }

        plugin.getLogger().info("Disconnected from Twitch chat");
    }

    public static class ChatMessage {
        private final String username;
        private final String message;
        private final String badges;
        private final long timestamp;

        public ChatMessage(String username, String message, String badges) {
            this.username = username;
            this.message = message;
            this.badges = badges;
            this.timestamp = System.currentTimeMillis();
        }

        public String getUsername() { return username; }
        public String getMessage() { return message; }
        public String getBadges() { return badges; }
        public long getTimestamp() { return timestamp; }
    }
}


