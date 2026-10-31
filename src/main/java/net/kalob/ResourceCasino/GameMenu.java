package net.kalob.ResourceCasino;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.List;

/**
 * A six-row game screen with the shared betting bar along the bottom:
 * <pre>
 * [resource][chip][chip][chip][chip][chip][chip][lobby][ PLAY ]
 *     45     46 ......................... 51    52      53
 * </pre>
 * Click a chip to make that your bet. Each chip is a stack of the resource you're
 * betting, so the stack size is the bet. The chips scale to each resource's table
 * limit, and each resource remembers its own bet.
 */
abstract class GameMenu extends Menu {

    static final int RESOURCE = 45, FIRST_CHIP = 46, LOBBY = 52, ACTION = 53;

    protected final ResourceCasinoPlugin.Prefs prefs;

    GameMenu(ResourceCasinoPlugin plugin, Player player, String title) {
        super(plugin, player, 6, title);
        this.prefs = plugin.prefs(player.getUniqueId());
        prefs.setStake(clampStake(prefs.stake()));
    }

    Resource resource() { return prefs.resource; }

    long stake() { return prefs.stake(); }

    long balance() { return plugin.balance(player.getUniqueId(), prefs.resource); }

    long clampStake(long stake) {
        return Math.max(1, Math.min(plugin.maxBet(prefs.resource), stake));
    }

    long[] chips() {
        return BetChips.forLimit(plugin.maxBet(prefs.resource));
    }

    /** Name of the big button in slot 53 ("SPIN", "PULL", "START RACE"). */
    abstract String actionName();

    abstract Material actionIcon();

    /** Pressed the big button. */
    abstract void play();

    /** Extra lines under the action button. */
    List<String> actionLore() {
        return List.of();
    }

    void renderBetBar() {
        Resource r = prefs.resource;
        set(RESOURCE, Items.icon(Items.block(r), 1, true, "<gray>Betting with</gray> <" + Items.hex(r.color()) + "><bold>" + r.displayName(),
                List.of("<gray>Balance: " + Items.amount(r, balance()),
                        "",
                        "<yellow>Click</yellow> <gray>next resource",
                        "<yellow>Right-click</yellow> <gray>previous resource")));
        renderChips(FIRST_CHIP, "<gray>Click to bet this much");
        set(LOBBY, Items.icon(Material.DARK_OAK_DOOR, "<gray>Back to the lobby"));
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Your bet: " + Items.amount(r, stake()));
        lore.addAll(actionLore());
        if (balance() < stake()) {
            lore.add("");
            lore.add("<red>Not enough " + r.displayName() + " - visit the cashier");
        }
        set(ACTION, Items.icon(actionIcon(), 1, true, "<gold><bold>" + actionName() + " <gray>(" + stake() + " " + r.displayName() + ")", lore));
    }

    /**
     * Draws the six chips starting at {@code first}. The chosen one glows; ones you can't
     * afford are shown but marked. The stack size is the chip's value (names say it past 64).
     */
    void renderChips(int first, String hint) {
        Resource r = prefs.resource;
        long[] chips = chips();
        long balance = balance();
        for (int i = 0; i < BetChips.MAX_CHIPS; i++) {
            if (i >= chips.length) {
                set(first + i, Items.filler(Material.GRAY_STAINED_GLASS_PANE));
                continue;
            }
            long value = chips[i];
            boolean chosen = value == stake();
            boolean affordable = value <= balance;
            List<String> lore = new ArrayList<>();
            lore.add(chosen ? "<green>Your current bet" : hint);
            if (!affordable) lore.add("<red>You have " + balance + " " + r.displayName());
            set(first + i, Items.icon(affordable || chosen ? Items.unit(r) : Material.GRAY_DYE, (int) Math.min(64, value), chosen,
                    (chosen ? "<green><bold>▶ " : "<white>") + "Bet " + Items.amount(r, value), lore));
        }
    }

    /** Handles a click on the betting bar. Returns true if the click was for the bar. */
    boolean clickBetBar(int slot, ClickType click) {
        if (slot >= FIRST_CHIP && slot < FIRST_CHIP + BetChips.MAX_CHIPS) {
            long[] chips = chips();
            int index = slot - FIRST_CHIP;
            if (index >= chips.length) return true;
            prefs.setStake(chips[index]);
            sound("block.chain.place", 0.5f, 1.6f + index * 0.08f);
            render();
            return true;
        }
        switch (slot) {
            case RESOURCE -> {
                prefs.resource = click.isRightClick() ? prefs.resource.previous() : prefs.resource.next();
                prefs.setStake(clampStake(prefs.stake()));
                clickSound();
                render();
            }
            case LOBBY -> {
                clickSound();
                switchTo(new LobbyMenu(plugin, player));
            }
            case ACTION -> play();
            default -> {
                return false;
            }
        }
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
