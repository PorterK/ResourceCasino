package net.kalob.ResourceCasino;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;

/**
 * Duck Derby. Each of the top five rows is a lane: the wool on the left is the
 * duck (click to back it, it shows the odds), the duck paddles right across the
 * water to the finish flag in the last column.
 * If the race track on the casino floor is free, the same race runs there too.
 */
final class DerbyMenu extends GameMenu {

    private static final int LANE_START = 1, LANE_END = 7, FINISH = 8;

    private DuckDerby.Field field;
    private int pick = -1;
    private DuckDerby.RacePlan plan;
    private int frame;

    DerbyMenu(ResourceCasinoPlugin plugin, Player player) {
        super(plugin, player, "<dark_gray>Casino</dark_gray> <aqua><bold>Duck Derby");
        field = DuckDerby.newField(plugin.random(), plugin.derbyEdge());
    }

    @Override
    String actionName() { return "START RACE"; }

    @Override
    Material actionIcon() { return Material.BELL; }

    @Override
    List<String> actionLore() {
        if (pick < 0) return List.of("<gray>Click a duck on the left first");
        return List.of("<gray>Backing " + duckName(pick),
                "<gray>Bet " + Items.amount(resource(), stake()),
                "<gray>Wins " + Items.amount(resource(), Payouts.apply(stake(), field.multiplier()[pick])));
    }

    static String duckName(int duck) {
        return "<" + Items.hex(DuckDerby.COLORS[duck]) + "><bold>" + DuckDerby.NAMES[duck] + "</bold></" + Items.hex(DuckDerby.COLORS[duck]) + ">";
    }

    @Override
    void render() {
        for (int duck = 0; duck < DuckDerby.DUCKS; duck++) renderLane(duck);
        renderBetBar();
    }

    private void renderLane(int duck) {
        int row = duck * 9;
        double odds = field.multiplier()[duck];
        boolean picked = duck == pick;
        set(row, Items.icon(Items.material(DuckDerby.DYES[duck] + "_WOOL"), duck + 1, picked,
                duckName(duck) + (picked ? " <yellow>← your duck" : ""),
                List.of("<gray>Pays <gold>" + Payouts.format(odds),
                        "<gray>Form: " + form(field.winChance()[duck]),
                        "",
                        picked ? "<green>You're backing this duck" : "<yellow>Click</yellow> <gray>to back this duck")));
        int column = plan == null ? LANE_START : columnFor(plan.at(duck, frame));
        for (int col = LANE_START; col <= FINISH; col++) {
            if (col == column) {
                set(row + col, Items.icon(Material.FEATHER, 1, picked, duckName(duck)));
            } else if (col == FINISH) {
                set(row + col, Items.icon((duck % 2 == 0) ? Material.WHITE_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE, "<white><bold>FINISH"));
            } else {
                set(row + col, Items.filler(Material.LIGHT_BLUE_STAINED_GLASS_PANE));
            }
        }
    }

    static int columnFor(double progress) {
        if (progress >= 1.0) return FINISH;
        return LANE_START + (int) Math.min(LANE_END - LANE_START, Math.floor(progress * (LANE_END - LANE_START + 1)));
    }

    static String form(double chance) {
        if (chance >= 0.3) return "<green>Favourite";
        if (chance >= 0.2) return "<green>Strong";
        if (chance >= 0.15) return "<yellow>Fair";
        if (chance >= 0.1) return "<gold>Shaky";
        return "<red>Long shot";
    }

    @Override
    void clickTable(int slot, ClickType click) {
        if (slot % 9 == 0 && slot / 9 < DuckDerby.DUCKS) {
            pick = slot / 9;
            plan = null;
            sound("entity.chicken.ambient", 0.8f, 1.4f);
            render();
        }
    }

    @Override
    void play() {
        if (pick < 0) {
            denySound();
            plugin.tell(player, "<gray>Click a duck on the left to back it first.");
            return;
        }
        int winner = DuckDerby.pickWinner(field, plugin.random());
        int backed = pick;
        double odds = field.multiplier()[backed];
        long stake = stake();
        Resource resource = resource();
        long payout = winner == backed ? Payouts.apply(stake, odds) : 0;
        ResourceCasinoPlugin.Pending bet = plugin.placeBet(player, "Duck Derby", resource, stake, payout);
        if (bet == null) {
            denySound();
            render();
            return;
        }
        int raceFrames = plugin.raceTicks();
        int tail = 30;
        plan = DuckDerby.plan(winner, raceFrames, tail, plugin.random());
        frame = 0;
        boolean onTrack = plugin.casino().track().run(plan);
        sound("block.note_block.bell", 1f, 1.0f);
        plugin.tell(player, "<aqua>Duck Derby:</aqua> <gray>They're off! You backed " + duckName(backed)
                + (onTrack ? " <gray>(it's running live on the casino track)" : ""));
        render();

        DuckDerby.RacePlan race = plan;
        animate(tick -> {
            frame = tick;
            if (tick % 2 == 0 && viewing()) {
                for (int duck = 0; duck < DuckDerby.DUCKS; duck++) renderLane(duck);
            }
            if (tick % 20 == 10 && tick < raceFrames) {
                player.sendActionBar(Items.mm(duckName(race.leader(tick)) + " <gray>leads!"));
                sound("entity.chicken.step", 0.6f, 1.5f);
            }
            if (tick == raceFrames) {
                plugin.settle(bet);
                player.sendActionBar(Items.mm(duckName(winner) + " <gold>wins!"));
                if (winner == backed) {
                    result(true, "<aqua>Duck Derby:</aqua> " + duckName(winner) + " <gold>wins!</gold> <gray>You get</gray> "
                            + Items.amount(resource, bet.payout) + " <gray>(" + Payouts.format(odds) + ")");
                } else {
                    result(false, "<aqua>Duck Derby:</aqua> " + duckName(winner) + " <gray>wins. " + duckName(backed)
                            + " <gray>came up short - you lost " + Items.amount(resource, stake) + "<gray>.");
                }
            }
            if (tick >= race.frames() + 10) {
                field = DuckDerby.newField(plugin.random(), plugin.derbyEdge());
                pick = -1;
                plan = null;
                if (viewing()) render();
                return false;
            }
            return true;
        });
    }
}
