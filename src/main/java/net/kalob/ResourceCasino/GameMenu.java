package net.kalob.ResourceCasino;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.List;

/**
 * A six-row game screen with the shared betting bar along the bottom:
 * <pre>
 * [resource][-10][-1][ bet ][+1][+10][max][lobby][ PLAY ]
 *     45     46   47   48    49   50   51   52     53
 * </pre>
 */
abstract class GameMenu extends Menu {

    static final int RESOURCE = 45, MINUS_10 = 46, MINUS_1 = 47, STAKE = 48, PLUS_1 = 49, PLUS_10 = 50,
            MAX = 51, LOBBY = 52, ACTION = 53;

    protected final ResourceCasinoPlugin.Prefs prefs;

    GameMenu(ResourceCasinoPlugin plugin, Player player, String title) {
        super(plugin, player, 6, title);
        this.prefs = plugin.prefs(player.getUniqueId());
        // Every game starts at a bet of 1. The resource you last bet with is remembered.
        prefs.stake = 1;
    }

    Resource resource() { return prefs.resource; }

    long stake() { return prefs.stake; }

    long balance() { return plugin.balance(player.getUniqueId(), prefs.resource); }

    long clampStake(long stake) {
        return Math.max(1, Math.min(plugin.maxBet(prefs.resource), stake));
    }

    /** Name of the big button in slot 53 ("SPIN", "PULL", "START RACE"). */
    abstract String actionName();

    abstract Material actionIcon();

    /** Pressed the big button. */
    abstract void play();

    /** Extra lines under the action button. */
    List<String> actionLore() {
        return List.of("<gray>Bet " + Items.amount(prefs.resource, prefs.stake) + "</gray>");
    }

    void renderBetBar() {
        Resource r = prefs.resource;
        set(RESOURCE, Items.icon(Items.unit(r), 1, true, "<gray>Betting with</gray> <" + Items.hex(r.color()) + "><bold>" + r.displayName(),
                List.of("<gray>Balance: " + Items.amount(r, balance()),
                        "",
                        "<yellow>Click</yellow> <gray>next resource",
                        "<yellow>Right-click</yellow> <gray>previous resource")));
        set(MINUS_10, Items.icon(Material.RED_STAINED_GLASS_PANE, "<red><bold>-10", "<gray>Shift-click: -64"));
        set(MINUS_1, Items.icon(Material.RED_STAINED_GLASS_PANE, "<red><bold>-1"));
        set(STAKE, Items.icon(Items.unit(r), (int) Math.min(64, prefs.stake), false, "<white>Bet: " + Items.amount(r, prefs.stake),
                List.of("<gray>Table limit: " + Items.amount(r, plugin.maxBet(r)))));
        set(PLUS_1, Items.icon(Material.LIME_STAINED_GLASS_PANE, "<green><bold>+1"));
        set(PLUS_10, Items.icon(Material.LIME_STAINED_GLASS_PANE, "<green><bold>+10", "<gray>Shift-click: +64"));
        set(MAX, Items.icon(Material.GOLD_NUGGET, "<gold>Max bet", "<gray>As much as you can, up to the limit"));
        set(LOBBY, Items.icon(Material.DARK_OAK_DOOR, "<gray>Back to the lobby"));
        List<String> lore = new ArrayList<>(actionLore());
        if (balance() < prefs.stake) {
            lore.add("");
            lore.add("<red>Not enough " + r.displayName() + " - visit the cashier");
        }
        set(ACTION, Items.icon(actionIcon(), 1, true, "<gold><bold>" + actionName(), lore));
    }

    /** Handles a click on the betting bar. Returns true if the click was for the bar. */
    boolean clickBetBar(int slot, ClickType click) {
        switch (slot) {
            case RESOURCE -> {
                prefs.resource = click.isRightClick() ? prefs.resource.previous() : prefs.resource.next();
                // Can't cover the current bet in the new resource? Start it back at 1.
                prefs.stake = balance() < prefs.stake ? 1 : clampStake(prefs.stake);
            }
            case MINUS_10 -> prefs.stake = clampStake(prefs.stake - (click.isShiftClick() ? 64 : 10));
            case MINUS_1 -> prefs.stake = clampStake(prefs.stake - 1);
            case PLUS_1 -> prefs.stake = clampStake(prefs.stake + 1);
            case PLUS_10 -> prefs.stake = clampStake(prefs.stake + (click.isShiftClick() ? 64 : 10));
            case MAX -> prefs.stake = clampStake(Math.max(1, balance()));
            case LOBBY -> {
                clickSound();
                switchTo(new LobbyMenu(plugin, player));
                return true;
            }
            case ACTION -> {
                play();
                return true;
            }
            default -> {
                return false;
            }
        }
        clickSound();
        render();
        return true;
    }

    @Override
    void click(int slot, ClickType click) {
        if (slot >= RESOURCE) {
            clickBetBar(slot, click);
        } else {
            clickTable(slot, click);
        }
    }

    /** A click above the betting bar. */
    void clickTable(int slot, ClickType click) {}

    void result(boolean won, String message) {
        plugin.tell(player, message);
        if (won) sound("entity.player.levelup", 0.7f, 1.2f);
        else sound("block.note_block.bass", 0.6f, 0.6f);
    }
}
