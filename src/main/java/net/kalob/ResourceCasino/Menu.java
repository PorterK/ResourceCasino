package net.kalob.ResourceCasino;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

/**
 * A chest-style casino screen. Every click in it is cancelled by
 * {@link MenuListener} and handed to {@link #click} instead, so nothing can
 * ever be taken out of a casino screen.
 */
abstract class Menu implements InventoryHolder {

    protected final ResourceCasinoPlugin plugin;
    protected final Player player;
    protected final Inventory inventory;
    private BukkitTask animation;

    Menu(ResourceCasinoPlugin plugin, Player player, int rows, String title) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, rows * 9, Items.mm(title));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void open() {
        render();
        player.openInventory(inventory);
    }

    /** Redraws the whole screen. */
    abstract void render();

    /** A click on one of this screen's slots. */
    abstract void click(int slot, ClickType click);

    /** A click on an item in the player's own inventory while this screen is open. */
    void clickOwnInventory(int slot, ItemStack item, ClickType click) {}

    void closed() {}

    /** Whether an animation (spin, race) is running; clicks are ignored meanwhile. */
    boolean busy() {
        return animation != null && !animation.isCancelled();
    }

    /** Runs {@code frame} every tick until it returns false. */
    void animate(java.util.function.IntPredicate frame) {
        stopAnimation();
        int[] tick = {0};
        animation = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            boolean more;
            try {
                more = frame.test(tick[0]++);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Casino animation failed: " + e);
                more = false;
            }
            if (!more) stopAnimation();
        }, 1L, 1L);
    }

    void stopAnimation() {
        if (animation != null) {
            animation.cancel();
            animation = null;
        }
    }

    boolean viewing() {
        return player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() == this;
    }

    void set(int slot, ItemStack item) {
        inventory.setItem(slot, item);
    }

    void fill(Material pane) {
        ItemStack filler = Items.filler(pane);
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);
    }

    /** Opens another screen next tick (opening from inside a click handler is unsafe). */
    void switchTo(Menu next) {
        Bukkit.getScheduler().runTask(plugin, next::open);
    }

    void sound(String key, float volume, float pitch) {
        player.playSound(Sound.sound(Key.key(key), Sound.Source.MASTER, volume, pitch));
    }

    void clickSound() {
        sound("ui.button.click", 0.4f, 1.4f);
    }

    void denySound() {
        sound("entity.villager.no", 0.6f, 1.0f);
    }
}
