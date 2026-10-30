package net.kalob.ResourceCasino;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Plinko on screen. The top four rows are the peg board (each row is two rows of
 * the real board), the fifth row is the nine bins with their payouts. The ball
 * drops in step with the big board on the casino floor.
 */
final class PlinkoMenu extends GameMenu {

    private static final int BIN_ROW = 36;

    private Plinko.Drop drop;
    private int shownRow = -1;
    private int landedBin = -1;

    PlinkoMenu(ResourceCasinoPlugin plugin, Player player) {
        super(plugin, player, "<dark_gray>Casino</dark_gray> <aqua><bold>Plinko");
    }

    @Override
    String actionName() { return "DROP"; }

    @Override
    Material actionIcon() { return Material.HEART_OF_THE_SEA; }

    @Override
    List<String> actionLore() {
        return List.of("<gray>Bet " + Items.amount(resource(), stake()),
                "<gray>Best bin pays " + Items.amount(resource(), Payouts.apply(stake(), Plinko.MULTIPLIERS[0])));
    }

    static String binDye(double multiplier) {
        if (multiplier >= 10) return "YELLOW";
        if (multiplier >= 3) return "ORANGE";
        if (multiplier >= 1) return "LIME";
        if (multiplier >= 0.6) return "LIGHT_BLUE";
        return "GRAY";
    }

    static int binRgb(double multiplier) {
        return switch (binDye(multiplier)) {
            case "YELLOW" -> 0xFFD83D;
            case "ORANGE" -> 0xF9801D;
            case "LIME" -> 0x80C71F;
            case "LIGHT_BLUE" -> 0x3AB3DA;
            default -> 0x9D9D97;
        };
    }

    @Override
    void render() {
        renderBoard();
        renderBetBar();
    }

    private void renderBoard() {
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                int offset = col - 4;
                boolean peg = Math.abs(offset) <= row && Math.floorMod(offset + row, 2) == 0;
                boolean ball = drop != null && row == shownRow && col == 4 + drop.offsetAfter(row * 2) / 2;
                if (ball) {
                    set(row * 9 + col, Items.icon(Material.HEART_OF_THE_SEA, 1, true, "<aqua><bold>Ball"));
                } else if (peg) {
                    set(row * 9 + col, Items.icon(Material.END_ROD, "<gray>Peg"));
                } else {
                    set(row * 9 + col, Items.filler(Material.BLACK_STAINED_GLASS_PANE));
                }
            }
        }
        for (int bin = 0; bin < Plinko.BINS; bin++) {
            double m = Plinko.MULTIPLIERS[bin];
            boolean landed = bin == landedBin;
            String color = Items.hex(binRgb(m));
            set(BIN_ROW + bin, Items.icon(Items.material(binDye(m) + (landed ? "_CONCRETE" : "_STAINED_GLASS_PANE")), 1, landed,
                    "<" + color + "><bold>" + Payouts.format(m),
                    List.of(String.format(java.util.Locale.ROOT, "<gray>%.1f%% of balls land here", Plinko.chance(bin) * 100),
                            "", "<dark_gray>Plinko pays back " + Payouts.percent(Plinko.expectedReturn()) + " on average.")));
        }
    }

    @Override
    void play() {
        Plinko.Drop rolled = Plinko.drop(plugin.random());
        double m = rolled.multiplier();
        long stake = stake();
        Resource resource = resource();
        ResourceCasinoPlugin.Pending bet = plugin.placeBet(player, "Plinko", resource, stake, Payouts.apply(stake, m));
        if (bet == null) {
            denySound();
            render();
            return;
        }
        drop = rolled;
        landedBin = -1;
        shownRow = -1;
        boolean onBoard = plugin.casino().plinko().drop(rolled);
        if (onBoard && !plugin.casino().isCasinoWorld(player.getWorld())) {
            plugin.tell(player, "<aqua>Your ball is dropping on the big board on the casino floor too.");
        }
        sound("block.dispenser.launch", 0.6f, 1.4f);
        int land = PlinkoBoard.landingTicks();
        animate(tick -> {
            // Row r of the screen shows the ball when the real one reaches peg row 2r.
            for (int row = 0; row < 4; row++) {
                int at = PlinkoBoard.START_TICKS + row * 2 * PlinkoBoard.HOP_TICKS;
                if (tick == at) {
                    shownRow = row;
                    sound("block.wooden_button.click_on", 0.5f, 1.2f + row * 0.15f);
                    if (viewing()) renderBoard();
                }
            }
            if (tick == land) {
                shownRow = -1;
                landedBin = rolled.bin();
                plugin.settle(bet);
                result(m > 1, "<aqua>Plinko:</aqua> <gray>landed in the</gray> <" + Items.hex(binRgb(m)) + "><bold>"
                        + Payouts.format(m) + "</bold></" + Items.hex(binRgb(m)) + "> <gray>bin. "
                        + (bet.payout > 0 ? "You get " + Items.amount(resource, bet.payout) : "You lost " + Items.amount(resource, stake)));
                if (viewing()) render();
                return false;
            }
            return true;
        });
    }
}
