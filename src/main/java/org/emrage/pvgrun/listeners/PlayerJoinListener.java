package org.emrage.pvgrun.listeners;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute. Attribute;
import org.bukkit.event.EventHandler;
import org. bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.entity.Player;
import org.emrage.pvgrun.Main;
import org.jspecify.annotations.NonNull;

import java.net.URI;
import java.util.*;

import static org.emrage.pvgrun.util.MessageUtils. c;

public class PlayerJoinListener implements Listener {

    private final Set<UUID> pendingTitle = new HashSet<>();

    private final Main plugin;
    public PlayerJoinListener(Main plugin) { this.plugin = plugin; }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        var gm = plugin.getGameManager();

        // Check if player is banned
        if (plugin.getBanManager().isBanned(p.getName())) {
            try {
                event.joinMessage(Component.empty());
            } catch (Exception ignored) {}
            p.kick(c("<#FF5555><bold>Du bist vom Deathrun gebannt.</bold>\n\n<#AAAAAA>Du wirst entbannt, sobald das Spiel vorbei ist."));
            return;
        }

        // immediate check:  run-banned players (kicked earlier) are not allowed to join during this game
        if (plugin.getPlayerDataManager().isRunBanned(p.getName())) {
            try {
                event.joinMessage(Component.empty());
            } catch (Exception ignored) {}
            p.kick(c("<#FF5555><bold>Du bist vom aktuellen Deathrun ausgeschlossen. </bold>\n\n<#AAAAAA>Du kannst nach einem Server-Restart erneut beitreten. "));
            return;
        }

        // Im LOBBY-State: Bossbar-Queue für Join-Messages, keine Chatnachricht mehr
        if (gm.getState() == org.emrage.pvgrun.enums.GameState.LOBBY) {
            p.teleport(gm.getSpawnLocation());
            p.setGameMode(GameMode.SURVIVAL);

            // Reset health to full
            double maxHealth = Objects.requireNonNull(p.getAttribute(Attribute.MAX_HEALTH)).getBaseValue();
            if (maxHealth <= 0) maxHealth = 20.0;
            Objects.requireNonNull(p.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(20.0);
            p.setHealth(maxHealth);
            p.setFoodLevel(20);
            sendResourcePack(p);

            plugin.getPlayerDataManager().updateDistance(p, 0.0);
            // Scoreboard NICHT anzeigen, solange nicht RUNNING
            // plugin.getScoreboardManager().addForPlayer(p); // ENTFERNT

            // Apply excluded from config
            if (plugin.getConfigManager().getExcludedPlayers().contains(p.getName())) {
                plugin.getPlayerDataManager().exclude(p);
            }

            // Team-Item nur im Dimension-Modus und wenn nicht im Team
            if (plugin.getConfigManager().getGameMode().equals("dimension") && !plugin.getTeamManager().hasTeam(p)) {
                org.emrage.pvgrun.listeners.TeamRequestListener.giveTeamInviteItem(p);
            }

            // Hier: Bossbar-Queue für Join-Messages aufrufen
            plugin.getBossBarManager().addForPlayer(p);
            plugin.getBossBarManager().queueJoinMessage(p);
            pendingTitle.add(p.getUniqueId());
            // Resourcepack-Title erst nach erfolgreichem Laden anzeigen
            // p.showTitle(
            //     net.kyori.adventure.title.Title.title(
            //         c("😀"),
            //         c("Hey, " + p.getName() + "!"),
            //         net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(400), java.time.Duration.ofMillis(1200), java.time.Duration.ofMillis(400))
            //     )
            // );
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, org.bukkit.SoundCategory.MASTER, 1.0f, 1.2f);
            return;
        }

        if (gm.getState() == org.emrage.pvgrun.enums.GameState. RUNNING) {
            // if they were NOT a participant -> deny join
            if (!gm.wasParticipant(p. getUniqueId())) {
                try {
                    event. joinMessage(Component.empty());
                } catch (Exception ignored) {}
                double dist = plugin.getPlayerDataManager().getStoredDistance(p. getName());
                plugin.getPlayerDataManager().addRunBan(p.getName());
                p.kick(c(String.format("<#FFD700><bold>DEATHRUN</bold>\n<#FF5555>Das Spiel läuft bereits.\n\n<#AAAAAA>Dein Score: <#55FF55>%sm", String.format("%.0f", dist))));
                return;
            }

            // participant rejoining
            try {
                event.joinMessage(Component.empty());
            } catch (Exception ignored) {}

            // If they're dead or excluded -> spectator mode
            if (plugin.getPlayerDataManager().isDead(p)) {
                p.setGameMode(GameMode.SPECTATOR);
                p.sendMessage(c("<#888888>Du bist bereits ausgeschieden und beobachtest nun. "));
                plugin.getScoreboardManager().addForPlayer(p);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.updateVisibility(p);

                    for (Player online : Bukkit.getOnlinePlayers()) {
                        plugin.updateVisibility(online);
                    }
                });
            } else if (plugin.getPlayerDataManager().isExcluded(p)) {
                p.setGameMode(GameMode.SPECTATOR);
                plugin.getScoreboardManager().addForPlayer(p);
            } else {
                // Active participant rejoining -> restore to survival
                p.setGameMode(GameMode.SURVIVAL);
                plugin.getScoreboardManager().addForPlayer(p);
                plugin.getScoreboardManager().updateAllForActive();
                p.sendMessage(c("<#55FF55>Willkommen zurück!  Viel Erfolg! "));
            }
            return;
        }

        // other states (PREPARING/ENDING) - block join
        try {
            event.joinMessage(Component.empty());
        } catch (Exception ignored) {}
        p.kick(c("<#FF5555>Das Spiel ist momentan nicht verfügbar. "));
    }

    private static final ResourcePackInfo PACK_INFO = ResourcePackInfo.resourcePackInfo()
            .uri(URI.create("https://download.mc-packs.net/pack/401b402cefdb05776cb1bb06db0afc0ed566e20d.zip"))
            .hash("401b402cefdb05776cb1bb06db0afc0ed566e20d")
            .id(UUID.fromString("d5150123-2d83-4505-b646-76d5c3faad01"))
            .build();

    public void sendResourcePack(final @NonNull Audience target) {
        final ResourcePackRequest request = ResourcePackRequest.resourcePackRequest()
                .packs(PACK_INFO)
                .prompt(MiniMessage.miniMessage().deserialize("<bold><gradient:#00ff00:#008000:#00ff00>Installiere das <gradient:#ff03cd:#960279>Resourcepack</gradient> um am <gradient:#ff03cd:#960279>Deathrun</gradient> teilnehmen zu können</gradient></bold>"))
                .required(false)
                .build();

        // Send the resource pack request to the target audience
        target.sendResourcePacks(request);
    }

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        // Zeige den Title nach jedem Abschluss (SUCCESS, DECLINED, FAILED_DOWNLOAD, ACCEPTED, etc.)
        switch (event.getStatus().name()) {
            case "SUCCESS":
            case "DECLINED":
            case "FAILED_DOWNLOAD":
            case "ACCEPTED":
                Player p = event.getPlayer();
                if (pendingTitle.remove(p.getUniqueId())) {
                    p.showTitle(
                        net.kyori.adventure.title.Title.title(
                            c("😀"),
                            c("Hey, " + p.getName() + "!"),
                            net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(400), java.time.Duration.ofMillis(1200), java.time.Duration.ofMillis(400))
                        )
                    );
                }
                break;
            default:
                // Keine Aktion für andere Status
        }
    }
}
