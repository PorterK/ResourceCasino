package net.kalob.ResourceCasino;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Three reels in the middle of the screen, the payline across row three.
 * <pre>
 *  . . . . P . . . .     P = paytable
 *  . . A . A . A . .     reels spin downward
 *  > . A . A . A . <     payline
 *  . . A . A . A . .
 *  . . . . R . . . .     R = last result
 *  [ betting bar ]
 * </pre>
 */
final class SlotsMenu extends GameMenu {

    private static final int[] REEL_COLUMNS = {2, 4, 6};
    private static final int PAYTABLE = 4, RESULT = 40;

    private final Slots.Symbol[][] reels = new Slots.Symbol[3][3]; // [reel][row 0..2]
    private String lastResult = "<gray>Pull the lever!";
    private Material frame = Material.YELLOW_STAINED_GLASS_PANE;

    SlotsMenu(ResourceCasinoPlugin plugin, Player player) {
        super(plugin, player, "<dark_gray>Casino</dark_gray> <yellow><bold>Slots");
        for (int reel = 0; reel < 3; reel++) {
            for (int row = 0; row < 3; row++) reels[reel][row] = Slots.spinReel(plugin.random());
        }
    }

    @Override
    String actionName() { return "PULL"; }

    @Override
    Material actionIcon() { return Material.LEVER; }

    @Override
    void render() {
        fill(Material.BLACK_STAINED_GLASS_PANE);
        renderFrame();
        set(PAYTABLE, Items.icon(Material.BOOK, 1, false, "<gold><bold>Paytable", paytable()));
        set(18, Items.icon(Material.LIME_STAINED_GLASS_PANE, "<green>▶ Payline"));
        set(26, Items.icon(Material.LIME_STAINED_GLASS_PANE, "<green>Payline ◀"));
        renderReels();
        set(RESULT, Items.icon(Material.NAME_TAG, lastResult));
        renderBetBar();
    }

    private void renderFrame() {
        ItemStack pane = Items.filler(frame);
        for (int i = 0; i < 9; i++) {
            if (i != PAYTABLE) set(i, pane);
            if (36 + i != RESULT) set(36 + i, pane);
        }
        for (int row = 1; row <= 3; row++) {
            if (row != 2) {
                set(row * 9, pane);
                set(row * 9 + 8, pane);
            }
        }
    }

    private void renderReels() {
        for (int reel = 0; reel < 3; reel++) {
            for (int row = 0; row < 3; row++) {
                Slots.Symbol symbol = reels[reel][row];
                boolean line = row == 1;
                set((row + 1) * 9 + REEL_COLUMNS[reel], Items.icon(Items.material(symbol.material), 1, line,
                        (line ? "<white>" : "<dark_gray>") + symbol.displayName));
            }
        }
    }

    static List<String> paytable() {
        List<String> lines = new ArrayList<>();
        for (Slots.Symbol symbol : Slots.Symbol.values()) {
            lines.add("<gray>3x " + symbol.displayName + " <dark_gray>»</dark_gray> <gold>" + symbol.triple + "x");
        }
        lines.add("<gray>Any 3 ingots <dark_gray>»</dark_gray> <gold>" + Payouts.format(Slots.ANY_THREE_INGOTS));
        lines.add("<gray>2 Berries <dark_gray>»</dark_gray> <gold>" + Payouts.format(Slots.TWO_CHERRIES));
        lines.add("<gray>1 Berry <dark_gray>»</dark_gray> <gold>stake back");
        lines.add("");
        lines.add("<dark_gray>Pays back " + Payouts.percent(Slots.rtp()) + " on average.");
        return lines;
    }

    @Override
    void play() {
        Slots.Symbol[] line = Slots.spin(plugin.random());
        double multiplier = Slots.multiplier(line);
        long stake = stake();
        Resource resource = resource();
        ResourceCasinoPlugin.Pending bet = plugin.placeBet(player, "Slots", resource, stake, Payouts.apply(stake, multiplier));
        if (bet == null) {
            denySound();
            render();
            return;
        }
        sound("block.lever.click", 0.8f, 0.8f);
        frame = Material.YELLOW_STAINED_GLASS_PANE;
        lastResult = "<gray>Spinning...";
        render();

        int[] stopAt = {14, 22, 30};
        int end = 34;
        int celebrate = multiplier > 1 ? 24 : 0;
        animate(tick -> {
            if (tick < end && tick % 2 == 0) {
                boolean any = false;
                for (int reel = 0; reel < 3; reel++) {
                    if (tick < stopAt[reel]) {
                        reels[reel][2] = reels[reel][1];
                        reels[reel][1] = reels[reel][0];
                        reels[reel][0] = Slots.spinReel(plugin.random());
                        any = true;
                    } else if (tick == stopAt[reel]) {
                        reels[reel][0] = Slots.spinReel(plugin.random());
                        reels[reel][1] = line[reel];
                        reels[reel][2] = Slots.spinReel(plugin.random());
                        sound("block.iron_trapdoor.close", 0.7f, 1.2f + reel * 0.2f);
                    }
                }
                if (any) sound("block.note_block.hat", 0.35f, 1.6f);
                if (viewing()) renderReels();
            }
            if (tick == end) {
                plugin.settle(bet);
                boolean won = bet.payout > 0;
                String what = Slots.describe(line);
                lastResult = won
                        ? "<gold><bold>" + what + "</bold></gold> <gray>+" + Items.amount(resource, bet.payout)
                        : "<gray>" + what;
                result(multiplier > 1, won
                        ? "<yellow>Slots:</yellow> <gold>" + what + "</gold> <gray>You get</gray> " + Items.amount(resource, bet.payout)
                        + " <gray>(" + Payouts.format(multiplier) + ")"
                        : "<yellow>Slots:</yellow> <gray>" + what + ". You lost</gray> " + Items.amount(resource, stake) + "<gray>.");
                if (multiplier >= 25) sound("ui.toast.challenge_complete", 0.9f, 1.0f);
                if (viewing()) render();
            }
            if (tick > end && tick <= end + celebrate) {
                if ((tick - end) % 4 == 0) {
                    frame = frame == Material.YELLOW_STAINED_GLASS_PANE ? Material.ORANGE_STAINED_GLASS_PANE : Material.YELLOW_STAINED_GLASS_PANE;
                    if (viewing()) renderFrame();
                }
            }
            return tick < end + celebrate;
        });
    }
}
