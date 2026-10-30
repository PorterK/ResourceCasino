package net.kalob.ResourceCasino;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * The casino floor's house rules: clicking a table/machine/pedestal opens its
 * game, nobody gets hurt or hungry, and only admins in creative can change the build.
 */
final class CasinoListener implements Listener {

    private final ResourceCasinoPlugin plugin;

    CasinoListener(ResourceCasinoPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean inCasino(org.bukkit.World world) {
        return plugin.casino() != null && plugin.casino().isCasinoWorld(world);
    }

    private static boolean builder(Player player) {
        return player.hasPermission("resourcecasino.admin") && player.getGameMode() == GameMode.CREATIVE;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || !inCasino(event.getClickedBlock().getWorld())) return;
        CasinoWorld.Station station = plugin.casino().stationAt(event.getClickedBlock());
        if (station == null) {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK && !builder(event.getPlayer())) event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;
        open(event.getPlayer(), station);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!plugin.casino().isCasinoEntity(event.getRightClicked())) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (CasinoWorld.ROLE_CASHIER.equals(plugin.casino().role(event.getRightClicked()))) {
            open(event.getPlayer(), CasinoWorld.Station.CASHIER);
        }
    }

    void open(Player player, CasinoWorld.Station station) {
        if (!player.hasPermission("resourcecasino.play")) {
            plugin.tell(player, "<red>You don't have permission to play.");
            return;
        }
        plugin.casino().ensureDecorations();
        Menu menu = switch (station) {
            case LOBBY -> new LobbyMenu(plugin, player);
            case CASHIER -> new CashierMenu(plugin, player);
            case ROULETTE -> new RouletteMenu(plugin, player);
            case SLOTS -> new SlotsMenu(plugin, player);
            case DERBY -> new DerbyMenu(plugin, player);
            case WHEEL -> new WheelMenu(plugin, player);
            case EXIT -> null;
        };
        if (menu == null) {
            plugin.casino().leave(player);
        } else {
            menu.open();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (inCasino(event.getBlock().getWorld()) && !builder(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (inCasino(event.getBlock().getWorld()) && !builder(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (plugin.casino().isCasinoEntity(event.getEntity())
                || (event.getEntity() instanceof Player && inCasino(event.getEntity().getWorld()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (inCasino(event.getEntity().getWorld()) && event.getFoodLevel() < event.getEntity().getFoodLevel()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (inCasino(event.getLocation().getWorld()) && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(EntityDropItemEvent event) {
        if (plugin.casino().isCasinoEntity(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (inCasino(event.getBlock().getWorld())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (inCasino(event.getLocation().getWorld())) event.blockList().clear();
    }
}
