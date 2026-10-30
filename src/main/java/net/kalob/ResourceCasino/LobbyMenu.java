package net.kalob.ResourceCasino;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.List;

/** The front door: your balance, every game, the cashier, and the trip to the casino floor. */
final class LobbyMenu extends Menu {

    static final int BALANCE = 4, CASHIER = 10, ROULETTE = 11, SLOTS = 12, DERBY = 14, WHEEL = 15, TRAVEL = 16, ODDS = 22;

    LobbyMenu(ResourceCasinoPlugin plugin, Player player) {
        super(plugin, player, 3, "<dark_gray>Resource</dark_gray><gold><bold>Casino</bold></gold>");
    }

    @Override
    void render() {
        fill(Material.BLACK_STAINED_GLASS_PANE);
        for (int slot : new int[]{0, 8, 18, 26}) set(slot, Items.filler(Material.YELLOW_STAINED_GLASS_PANE));

        List<String> balance = new ArrayList<>();
        for (Resource r : Resource.values()) balance.add(Items.amount(r, plugin.balance(player.getUniqueId(), r)));
        set(BALANCE, Items.icon(Material.BUNDLE, 1, false, "<gold>Your casino balance", balance));

        set(CASHIER, Items.icon(Material.ENDER_CHEST, "<gold><bold>Cashier",
                "<gray>Deposit resources to play,", "<gray>withdraw what you win."));
        set(ROULETTE, Items.icon(Material.CLOCK, "<red><bold>Roulette",
                "<gray>Single-zero wheel. Numbers pay 35 to 1,", "<gray>red/black/odd/even pay even money."));
        set(SLOTS, Items.icon(Material.BELL, "<yellow><bold>Slots",
                "<gray>Three reels. Line up three netherite", "<gray>for 150x your bet."));
        set(13, Items.icon(Material.NETHER_STAR, 1, true, "<gold>Bet iron, copper, emeralds,", List.of("<gold>diamonds or netherite.",
                "", "<gray>Win the same resource you bet.")));
        set(DERBY, Items.icon(Material.FEATHER, "<aqua><bold>Duck Derby",
                "<gray>Five ducks, one winner.", "<gray>Back a long shot for up to 11x."));
        set(WHEEL, Items.icon(Material.SUNFLOWER, "<light_purple><bold>Spin the Wheel",
                "<gray>24 segments, up to " + Payouts.format(plugin.wheel().maxMultiplier()) + " your bet."));

        boolean onFloor = plugin.casino().isCasinoWorld(player.getWorld());
        if (onFloor) {
            set(TRAVEL, Items.icon(Material.OAK_DOOR, "<green><bold>Leave the casino", "<gray>Back to where you came from."));
        } else if (plugin.data().built()) {
            set(TRAVEL, Items.icon(Material.ENDER_PEARL, "<green><bold>Go to the casino floor",
                    "<gray>Tables, slot machines, a duck", "<gray>race track and a giant wheel."));
        } else {
            set(TRAVEL, Items.icon(Material.BARRIER, "<gray>Casino floor not built yet",
                    "<gray>An admin can build it with", "<yellow>/casino build"));
        }

        set(ODDS, Items.icon(Material.BOOK, 1, false, "<gray>House odds", oddsLore(plugin)));
    }

    static List<String> oddsLore(ResourceCasinoPlugin plugin) {
        return List.of(
                "<gray>Average paid back per bet:",
                "<red>Roulette</red> <white>" + Payouts.percent(Roulette.Bet.of(Roulette.Kind.RED).expectedReturn()),
                "<yellow>Slots</yellow> <white>" + Payouts.percent(Slots.rtp()),
                "<aqua>Duck Derby</aqua> <white>~" + Payouts.percent(1 - plugin.derbyEdge()),
                "<light_purple>Wheel</light_purple> <white>" + Payouts.percent(plugin.wheel().expectedReturn()),
                "",
                "<dark_gray>The house always wins in the end.");
    }

    @Override
    void click(int slot, ClickType click) {
        Menu next = switch (slot) {
            case CASHIER -> new CashierMenu(plugin, player);
            case ROULETTE -> game(new RouletteMenu(plugin, player));
            case SLOTS -> game(new SlotsMenu(plugin, player));
            case DERBY -> game(new DerbyMenu(plugin, player));
            case WHEEL -> game(new WheelMenu(plugin, player));
            default -> null;
        };
        if (slot == TRAVEL) {
            player.closeInventory();
            if (plugin.casino().isCasinoWorld(player.getWorld())) plugin.casino().leave(player);
            else plugin.casino().enter(player);
            return;
        }
        if (next != null) {
            clickSound();
            switchTo(next);
        }
    }

    private Menu game(Menu menu) {
        if (plugin.playAnywhere() || plugin.casino().isCasinoWorld(player.getWorld())) return menu;
        denySound();
        plugin.tell(player, "<gray>Games are played on the casino floor. Click the ender pearl to go there.");
        return null;
    }
}
