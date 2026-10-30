package net.kalob.ResourceCasino;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns real items into casino balance and back. Click a resource in your own
 * inventory to deposit that stack (blocks count as 9), click a resource up top
 * to withdraw it as ingots/gems.
 */
final class CashierMenu extends Menu {

    static final int FIRST_RESOURCE = 20, DEPOSIT_ALL = 40, HELP = 38, LOBBY = 45, CLOSE = 53;

    CashierMenu(ResourceCasinoPlugin plugin, Player player) {
        super(plugin, player, 6, "<dark_gray>Casino</dark_gray> <gold><bold>Cashier");
    }

    @Override
    void render() {
        fill(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) set(i, Items.filler(Material.YELLOW_STAINED_GLASS_PANE));
        set(4, Items.icon(Material.GOLD_INGOT, 1, true, "<gold><bold>Cashier",
                List.of("<gray>Each resource is its own currency.", "<gray>Bet iron, win iron.")));

        for (Resource r : Resource.values()) {
            long balance = plugin.balance(player.getUniqueId(), r);
            int carried = carried(r);
            set(FIRST_RESOURCE + r.ordinal(), Items.icon(Items.unit(r), (int) Math.max(1, Math.min(64, balance)), balance > 0,
                    "<" + Items.hex(r.color()) + "><bold>" + r.displayName(),
                    List.of("<gray>Casino balance: " + Items.amount(r, balance),
                            "<gray>In your inventory: <white>" + carried,
                            "",
                            "<yellow>Click</yellow> <gray>withdraw 1",
                            "<yellow>Right-click</yellow> <gray>withdraw 16",
                            "<yellow>Shift-click</yellow> <gray>withdraw 64",
                            "<yellow>Shift-right-click</yellow> <gray>withdraw everything")));
        }

        set(HELP, Items.icon(Material.BOOK, "<white>How to deposit",
                "<gray>Click any copper, iron, emerald,",
                "<gray>diamond or netherite in <white>your own</white>",
                "<gray>inventory below to deposit the stack.",
                "<gray>Blocks count as 9."));
        set(DEPOSIT_ALL, Items.icon(Material.HOPPER, 1, true, "<green><bold>Deposit everything",
                List.of("<gray>Every casino resource you're carrying,", "<gray>ingots, gems and blocks.")));
        set(42, Items.icon(Material.PAPER, "<gray>Odds", LobbyMenu.oddsLore(plugin).toArray(String[]::new)));
        set(LOBBY, Items.icon(Material.DARK_OAK_DOOR, "<gray>Back to the lobby"));
        set(CLOSE, Items.icon(Material.BARRIER, "<red>Close"));
    }

    /** Units of a resource the player is carrying (blocks count 9). */
    private int carried(Resource r) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null) continue;
            total += Resource.unitsOf(r, stack.getType().name()) * stack.getAmount();
        }
        return total;
    }

    @Override
    void click(int slot, ClickType click) {
        if (slot >= FIRST_RESOURCE && slot < FIRST_RESOURCE + Resource.values().length) {
            Resource r = Resource.values()[slot - FIRST_RESOURCE];
            long balance = plugin.balance(player.getUniqueId(), r);
            long want;
            if (click == ClickType.SHIFT_RIGHT) want = balance;
            else if (click.isShiftClick()) want = 64;
            else if (click.isRightClick()) want = 16;
            else want = 1;
            withdraw(r, Math.min(want, balance));
        } else if (slot == DEPOSIT_ALL) {
            depositAll();
        } else if (slot == LOBBY) {
            clickSound();
            switchTo(new LobbyMenu(plugin, player));
        } else if (slot == CLOSE) {
            player.closeInventory();
        }
    }

    @Override
    void clickOwnInventory(int slot, ItemStack item, ClickType click) {
        if (item == null || item.getType().isAir()) return;
        Resource r = Resource.ofMaterial(item.getType().name());
        if (r == null) {
            denySound();
            return;
        }
        int units = Resource.unitsOf(r, item.getType().name()) * item.getAmount();
        player.getInventory().setItem(slot, null);
        plugin.data().wallet().deposit(player.getUniqueId(), r, units);
        plugin.tell(player, "<green>Deposited " + Items.amount(r, units) + "<green>.");
        sound("block.chain.place", 0.8f, 1.4f);
        render();
    }

    private void depositAll() {
        PlayerInventory inv = player.getInventory();
        long[] totals = new long[Resource.values().length];
        ItemStack[] contents = inv.getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null) continue;
            Resource r = Resource.ofMaterial(stack.getType().name());
            if (r == null) continue;
            totals[r.ordinal()] += (long) Resource.unitsOf(r, stack.getType().name()) * stack.getAmount();
            inv.setItem(i, null);
        }
        List<String> parts = new ArrayList<>();
        for (Resource r : Resource.values()) {
            if (totals[r.ordinal()] == 0) continue;
            plugin.data().wallet().deposit(player.getUniqueId(), r, totals[r.ordinal()]);
            parts.add(Items.amount(r, totals[r.ordinal()]));
        }
        if (parts.isEmpty()) {
            denySound();
            plugin.tell(player, "<gray>You're not carrying any casino resources.");
        } else {
            sound("block.chain.place", 0.8f, 1.2f);
            plugin.tell(player, "<green>Deposited</green> " + String.join("<gray>, </gray>", parts) + "<green>.");
        }
        render();
    }

    private void withdraw(Resource r, long amount) {
        if (amount <= 0 || !plugin.data().wallet().withdraw(player.getUniqueId(), r, amount)) {
            denySound();
            plugin.tell(player, "<gray>You don't have any " + r.displayName() + " in the casino.");
            return;
        }
        Material unit = Items.unit(r);
        int stackSize = Math.max(1, unit.getMaxStackSize());
        long left = amount;
        long refunded = 0;
        while (left > 0) {
            int n = (int) Math.min(stackSize, left);
            left -= n;
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(new ItemStack(unit, n));
            if (!overflow.isEmpty()) {
                for (ItemStack rest : overflow.values()) refunded += rest.getAmount();
                refunded += left;
                break;
            }
        }
        if (refunded > 0) plugin.data().wallet().deposit(player.getUniqueId(), r, refunded);
        long given = amount - refunded;
        if (given > 0) {
            sound("entity.item.pickup", 0.8f, 1.0f);
            plugin.tell(player, "<green>Withdrew " + Items.amount(r, given) + "<green>."
                    + (refunded > 0 ? " <gray>Your inventory is full; the rest stays in the casino." : ""));
        } else {
            denySound();
            plugin.tell(player, "<red>Your inventory is full.");
        }
        render();
    }
}
