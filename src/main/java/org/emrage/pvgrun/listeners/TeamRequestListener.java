package org.emrage.pvgrun.listeners;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.emrage.pvgrun.Main;
import static org.emrage.pvgrun.util.MessageUtils.c;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;
import org.emrage.pvgrun.managers.TeamBackpackHolder;

public class TeamRequestListener implements Listener {
    private final Main plugin;
    public TeamRequestListener(Main plugin) { this.plugin = plugin; }

    // Spieler schlägt anderen with Team-Item (jetzt Karottenangel)
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager) || !(event.getEntity() instanceof Player target)) return;
        ItemStack item = damager.getInventory().getItemInMainHand();
        if (!isTeamSelector(item)) return;
        event.setCancelled(true);
        // Nur Team-Anfrage, wenn beide KEIN Team haben und keine PendingRequest existiert
        if (plugin.getTeamManager().hasTeam(damager) || plugin.getTeamManager().hasTeam(target)) return;
        if (plugin.getTeamManager().hasPendingRequest(damager, target)) return; // Doppelte Anfrage verhindern
        plugin.getTeamManager().sendTeamRequest(damager, target);
        damager.sendMessage(c("<gray>Team-Anfrage an <white>" + target.getName() + "</white> gesendet."));
        target.sendMessage(c("<gray>Du hast eine Team-Anfrage von <white>" + damager.getName() + "</white> erhalten. Schlage ihn zurück oder nutze /team accept/deny!"));
    }

    // Spieler schlägt zurück = Annahme
    @EventHandler
    public void onReturnHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager) || !(event.getEntity() instanceof Player target)) return;
        ItemStack item = damager.getInventory().getItemInMainHand();
        if (!isTeamSelector(item)) return;
        // Nur Annahme, wenn PendingRequest existiert und beide kein Team haben
        if (!plugin.getTeamManager().hasPendingRequest(target, damager)) return;
        if (plugin.getTeamManager().hasTeam(damager) || plugin.getTeamManager().hasTeam(target)) return;
        event.setCancelled(true);
        plugin.getTeamManager().acceptTeamRequest(target, damager);
        damager.sendMessage(c("<green>Team mit " + target.getName() + " gebildet!"));
        target.sendMessage(c("<green>Team mit " + damager.getName() + " gebildet!"));
        String mode = plugin.getConfigManager().getGameMode();
        if ("dimension".equals(mode)) {
            giveDimensionSelectItem(damager);
            giveDimensionSelectItem(target);
        }
        // Team-Backpack nach Team-Bildung öffnen
        plugin.getTeamBackpackManager().openBackpack(damager, plugin.getTeamManager().getTeam(damager).getName());
        plugin.getTeamBackpackManager().openBackpack(target, plugin.getTeamManager().getTeam(target).getName());
        // Give dimension select items on next tick so they are visible in the players' inventory while the backpack UI is open
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            String mode1 = plugin.getConfigManager().getGameMode();
            if ("dimension".equals(mode1)) {
                giveDimensionSelectItem(damager);
                giveDimensionSelectItem(target);
            }
        });
    }

    // Team-Item darf nicht gedroppt, aufgehoben, verschoben, entfernt werden (Karottenangel)
    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (isTeamSelector(item) || isDimensionSelector(item)) {
            event.setCancelled(true);
        }
    }
    @EventHandler
    public void onPickup(org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        ItemStack item = event.getItem().getItemStack();
        if (isTeamSelector(item) || isDimensionSelector(item)) {
            event.setCancelled(true);
        }
    }
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        ItemStack item = event.getCurrentItem();
        if (isTeamSelector(item) || isDimensionSelector(item)) {
            event.setCancelled(true);
        }
    }
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        for (ItemStack item : event.getNewItems().values()) {
            if (isTeamSelector(item) || isDimensionSelector(item)) {
                event.setCancelled(true);
                break;
            }
        }
    }
    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (isTeamSelector(event.getMainHandItem()) || isDimensionSelector(event.getMainHandItem())
            || isTeamSelector(event.getOffHandItem()) || isDimensionSelector(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (isTeamSelector(item)) {
            // Wenn kein Entity getroffen wurde (Luftklick): Hinweis
            if (event.getAction().toString().contains("LEFT") || event.getAction().toString().contains("RIGHT")) {
                // PlayerInteractEvent hat keine getClickedEntity(), daher prüfen wir auf PlayerInteractEntityEvent im Listener separat
                event.getPlayer().sendMessage(c("<gray>Schlage einen Spieler mit dem Team-Item, um ihn ins Team einzuladen!"));
                event.setCancelled(true);
            }
        }
    }
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getView() == null) return;
        if (event.getView().getTopInventory() == null) return;
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof TeamBackpackHolder) {
            TeamBackpackHolder h = (TeamBackpackHolder) holder;
            try { plugin.getTeamBackpackManager().saveBackpack(h.getTeamName()); } catch (Exception ignored) {}
        }
    }
    private boolean isTeamSelector(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (item.getType() != Material.CARROT_ON_A_STICK) return false;
        Component expected = c("<#55FF55>Team Anfrage");
        Component actual = meta.hasDisplayName() ? meta.displayName() : null;
        return actual != null && actual.equals(expected);
    }
    private boolean isDimensionSelector(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (item.getType() != Material.BLAZE_ROD) return false;
        Component expected = c("<#FFAA00>Dimension Auswahl");
        Component actual = meta.hasDisplayName() ? meta.displayName() : null;
        return actual != null && actual.equals(expected);
    }

    // Team-Item nur geben, wenn nicht im Team
    public static void giveTeamInviteItem(Player p) {
        ItemStack rod = new ItemStack(Material.CARROT_ON_A_STICK);
        ItemMeta meta = rod.getItemMeta();
        meta.displayName(c("<#55FF55>Team Anfrage"));
        rod.setItemMeta(meta);
        p.getInventory().setItem(4, rod); // mittlerer Slot
    }

    public static void giveDimensionSelectItem(Player p) {
        ItemStack stick = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = stick.getItemMeta();
        meta.displayName(c("<#FFAA00>Dimension Auswahl"));
        stick.setItemMeta(meta);
        p.getInventory().setItem(4, stick);
    }
}
