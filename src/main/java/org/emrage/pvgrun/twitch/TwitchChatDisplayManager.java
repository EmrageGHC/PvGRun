package org.emrage.pvgrun.twitch;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.emrage.pvgrun.Main;

import java.util.ArrayDeque;
import java.util.Deque;

public class TwitchChatDisplayManager {

    private final Main plugin;
    private TwitchIRCClient ircClient;

    private Location baseLocation;
    private String trackedPlayer;

    private TextDisplay chatDisplay;
    private final Deque<String> lines = new ArrayDeque<>();

    private static final int MAX_LINES = 10;
    private static final int MAX_CHARS = 70; // ~2 Zeilen max
    private static final int MAX_TOTAL_CHARS = MAX_LINES * MAX_CHARS;

    private final MiniMessage mm = MiniMessage.miniMessage();

    public TwitchChatDisplayManager(Main plugin) {
        this.plugin = plugin;
    }

    // Gibt an, ob der IRC-Client aktuell läuft
    public boolean isRunning() {
        return ircClient != null;
    }

    /* -------------------------------------------------- */
    /*  Start / Stop                                      */
    /* -------------------------------------------------- */

    public void start(String channel, Location location, String trackPlayer) {
        stop();

        this.baseLocation = location.clone();
        this.trackedPlayer = trackPlayer;

        spawnDisplay();

        ircClient = new TwitchIRCClient(plugin, channel, this::onMessage);
        ircClient.connect();
    }

    public void stop() {
        if (ircClient != null) {
            ircClient.disconnect();
            ircClient = null;
        }

        if (chatDisplay != null) {
            chatDisplay.remove();
            chatDisplay = null;
        }

        lines.clear();
        baseLocation = null;
        trackedPlayer = null;
    }

    /* -------------------------------------------------- */
    /*  Chat Input                                        */
    /* -------------------------------------------------- */

    private void onMessage(TwitchIRCClient.ChatMessage msg) {
        String clean = sanitize(msg.getMessage());

        String line =
                "<#4DA3FF>" + msg.getUsername() + "</#4DA3FF>" +
                        "<gray>: </gray>" +
                        "<white>" + mm.escapeTags(clean) + "</white>";

        lines.addFirst(line);

        while (lines.size() > MAX_LINES) {
            lines.removeLast();
        }

        updateText();
    }


    /* -------------------------------------------------- */
    /*  Display                                           */
    /* -------------------------------------------------- */

    private void spawnDisplay() {
        Location loc = getBaseLocation();

        chatDisplay = loc.getWorld().spawn(loc, TextDisplay.class);
        // Use VERTICAL so the text faces the nearest player and rotates with them
        chatDisplay.setBillboard(Display.Billboard.VERTICAL);
        chatDisplay.setSeeThrough(true);
        chatDisplay.setShadowed(false);
        chatDisplay.setLineWidth(200);
        chatDisplay.setBackgroundColor(null);
    }

    private void updateText() {
        if (chatDisplay == null) return;

        chatDisplay.text(mm.deserialize(String.join("\n", lines)));
        chatDisplay.teleport(getBaseLocation());
    }

    private Location getBaseLocation() {
        if (trackedPlayer != null) {
            Player p = Bukkit.getPlayer(trackedPlayer);
            if (p != null && p.isOnline()) {
                return p.getLocation().add(0, 2.8, 0);
            }
        }
        return baseLocation.clone();
    }

    /* -------------------------------------------------- */
    /*  SANITIZER (DAS ist der Key)                        */
    /* -------------------------------------------------- */

    private String sanitize(String input) {
        // ❌ Alles nach Newline killen (IRC-Tags / Spam)
        String cleaned = input.split("\n")[0];

        // ❌ Unicode + Emojis raus
        cleaned = cleaned.replaceAll("[^\\x20-\\x7E]", "");

        // ❌ Mehrfach-Leerzeichen
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        // ❌ Zu lange Nachrichten kappen
        if (cleaned.length() > MAX_CHARS) {
            cleaned = cleaned.substring(0, MAX_CHARS - 1) + "…";
        }

        return cleaned;
    }

    private String sanitizeMessage(String input) {
        // Unicode / Emojis entfernen
        if (input == null) return "";

        // Remove ASCII control characters (except newline)
        String cleaned = input.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "");

        // Remove common IRC/Twitch tag artifacts that may leak into messages
        // Examples: tmi-sent-ts=167..., msg-id=highlighted, id=..., emotes=..., flags=...
        cleaned = cleaned.replaceAll("\\b(?:tmi-sent-ts|msg-id|id|emotes|flags|bits)=\\S+\\b", "");

        // Remove any leftover key=value tokens where value is numeric (e.g., some malformed tags)
        cleaned = cleaned.replaceAll("\\b\\w+=\\d+\\b", "");

        // Remove any stray IRC control bytes like 0x01 (ACTION) or similar
        cleaned = cleaned.replaceAll("\\u0001", "");

        // Remove non-ASCII (emojis etc.) now that control bits removed
        cleaned = cleaned.replaceAll("[^\\p{ASCII}]", "");

        // Normalize whitespace
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        // Limit total characters to twice a line length (approx 2 lines)
        if (cleaned.length() > MAX_TOTAL_CHARS) {
            cleaned = cleaned.substring(0, MAX_TOTAL_CHARS - 1) + "…";
        }

        return cleaned;
    }
}
