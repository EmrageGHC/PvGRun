package org.emrage. pvgrun. listeners;

import org.bukkit.attribute.Attribute;
import org. bukkit.entity.Entity;
import org.bukkit. entity.Player;
import org. bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org. bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org. emrage.pvgrun.Main;

import static org.emrage. pvgrun.util.MessageUtils.c;

public class PlayerDamageListener implements Listener {

    private final Main plugin;
    public PlayerDamageListener(Main plugin) { this.plugin = plugin; }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player p = (Player) event.getEntity();
        var gm = plugin.getGameManager();

        // No damage in lobby or preparing
        if (gm.getState() == org.emrage.pvgrun.enums.GameState.LOBBY || gm.getState() == org.emrage.pvgrun.enums.GameState.PREPARING) {
            event.setCancelled(true);
            return;
        }

        if (plugin.getPlayerDataManager().isExcluded(p) || plugin.getPlayerDataManager().isDead(p)) {
            event.setCancelled(true);
            return;
        }

        // Cancel PvP
        if (event instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent ed = (EntityDamageByEntityEvent) event;
            if (ed.getDamager() instanceof Player) { ed.setCancelled(true); return; }
        }

        double finalHealth = p.getHealth() - event.getFinalDamage();
        if (finalHealth <= 0) {
            event. setCancelled(true);

            // compute distance
            double dist = 0;
            if (gm != null) {
                var spawn = gm.getSpawnLocation();
                if (spawn != null) dist = Math.max(0.0, spawn.getZ() - p.getLocation().getZ());
            }

            // store final best distance
            plugin.getPlayerDataManager().updateDistance(p, dist);

            // compute placement (before marking dead)
            int placement = plugin.getPlayerDataManager().getPlacement(p);

            // mark dead
            plugin.getPlayerDataManager().markDead(p);

            // Determine killer/cause for a nicer message
            String killerStr = "Unbekannt";
            if (event instanceof EntityDamageByEntityEvent) {
                Entity dam = ((EntityDamageByEntityEvent) event).getDamager();
                if (dam != null) {
                    if (dam instanceof Player) killerStr = ((Player) dam).getName();
                    else killerStr = dam.getType().name().toLowerCase().replace("_", " ");
                }
            } else {
                killerStr = event. getCause().name().toLowerCase().replace("_", " ");
            }

            // broadcast nice death message
            String deathBroadcast = String.format("<#FF5555><bold>☠</bold> <#FFFFFF>%s <#888888>ist ausgeschieden <#666666>(%s)", p.getName(), killerStr);
            if (gm != null) org.bukkit.Bukkit.getServer().broadcast(c(deathBroadcast));

            // Check if player is HardcorePvG or EmrageGHC
            String playerName = p.getName();
            if (playerName.equals("HardcorePvG") || playerName.equals("EmrageGHC")) {
                // Set to spectator instead of banning
                p.setGameMode(org.bukkit.GameMode.SPECTATOR);
                p.sendMessage(c("<#888888>Du bist ausgeschieden und beobachtest nun als Spectator."));
            } else {
                // Ban player and kick with message
                plugin.getBanManager().ban(playerName);

                String distStr = String.format("%.0f", dist);
                String placeStr = (placement <= 0) ? "—" : ("#"+placement);
                String reason = String.join("\n",
                        "<#FFD700><bold>━━━━━━━━━━━━━━━━━</bold>",
                        "<#FFD700><bold>DEATHRUN</bold>",
                        "",
                        "<#55FF55>Danke fürs Teilnehmen! ",
                        "",
                        "<#AAAAAA>Deine Distanz: <#55FF55><bold>" + distStr + "m</bold>",
                        "<#AAAAAA>Dein Rang: <#FFD700><bold>" + placeStr + "</bold>",
                        "",
                        "<#888888>Du wirst entbannt, sobald das Spiel vorbei ist.",
                        "",
                        "<#FFD700><bold>━━━━━━━━━━━━━━━━━</bold>"
                );

                p.kick(c(reason));
            }

            // Update other players' boards (dead player's score remains visible)
            plugin.getScoreboardManager().updateAllForActive();

            // check win condition (if only one runner remains)
            if (gm != null) gm.checkForWinner();
            return;
        }

        // Track hearts lost for stats (optional)
        int heartsLost = (int) Math.ceil(event.getFinalDamage() / 2.0);
        if (heartsLost > 0) {
            plugin.getPlayerDataManager().addHeartsLost(p.getName(), heartsLost);
        }

        // NO MaxHealth reduction — hearts stay visible but empty (grey/unregeneratable)
        // Regeneration is already blocked by HealthRegenBlockerListener
    }
}
