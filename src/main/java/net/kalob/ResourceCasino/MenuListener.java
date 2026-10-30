package net.kalob.ResourceCasino;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/** Routes clicks in casino screens to the screen and makes sure no item ever moves. */
final class MenuListener implements Listener {

    private final ResourceCasinoPlugin plugin;

    MenuListener(ResourceCasinoPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Menu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        Inventory clicked = event.getClickedInventory();
        if (clicked == null || menu.busy()) return;
        try {
            if (clicked.equals(top)) {
                menu.click(event.getSlot(), event.getClick());
            } else {
                menu.clickOwnInventory(event.getSlot(), event.getCurrentItem(), event.getClick());
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Casino screen error: " + e);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Menu menu) menu.closed();
    }
}
