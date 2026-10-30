package net.kalob.ResourceCasino;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;

/**
 * Horse racing. Each of the top five rows is a lane: the wool on the left is the
 * horse (click to back it, it shows the odds), and the horse gallops right to the
 * finish flag in the last column. If the racetrack on the casino floor is free,
 * the same race runs there with real horses.
 */
final class HorseRaceMenu extends GameMenu {

    private static final int LANE_START = 1, LANE_END = 7, FINISH = 8;

    private HorseRace.Field field;
    private int pick = -1;
    private HorseRace.RacePlan plan;
    private int frame;

    HorseRaceMenu(ResourceCasinoPlugin plugin, Player player) {
        super(plugin, player, "<dark_gray>Casino</dark_gray> <gold><bold>Horse Racing");
        field = HorseRace.newField(plugin.random(), plugin.horseEdge());
    }

    @Override
    String actionName() { return "START RACE"; }

    @Override
    Material actionIcon() { return Material.BELL; }

    @Override
    List<String> actionLore() {
        if (pick < 0) return List.of("<gray>Click a horse on the left first");
        return List.of("<gray>Backing " + horseName(pick),
                "<gray>Bet " + Items.amount(resource(), stake()),
                "<gray>Wins " + Items.amount(resource(), Payouts.apply(stake(), field.multiplier()[pick])));
    }

    static String horseName(int horse) {
        return "<" + Items.hex(HorseRace.COLORS[horse]) + "><bold>" + HorseRace.NAMES[horse] + "</bold></" + Items.hex(HorseRace.COLORS[horse]) + ">";
    }

    @Override
    void render() {
        for (int horse = 0; horse < HorseRace.HORSES; horse++) renderLane(horse);
        renderBetBar();
    }

    private void renderLane(int horse) {
        int row = horse * 9;
        double odds = field.multiplier()[horse];
        boolean picked = horse == pick;
        set(row, Items.icon(Items.material(HorseRace.DYES[horse] + "_WOOL"), horse + 1, picked,
                horseName(horse) + (picked ? " <yellow>← your horse" : ""),
                List.of("<gray>Pays <gold>" + Payouts.format(odds),
                        "<gray>Form: " + form(field.winChance()[horse]),
                        "",
                        picked ? "<green>You're backing this horse" : "<yellow>Click</yellow> <gray>to back this horse")));
        int column = plan == null ? LANE_START : columnFor(plan.at(horse, frame));
        for (int col = LANE_START; col <= FINISH; col++) {
            if (col == column) {
                set(row + col, Items.icon(Material.SADDLE, 1, picked, horseName(horse)));
            } else if (col == FINISH) {
                set(row + col, Items.icon((horse % 2 == 0) ? Material.WHITE_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE, "<white><bold>FINISH"));
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
        if (slot % 9 == 0 && slot / 9 < HorseRace.HORSES) {
            pick = slot / 9;
            plan = null;
            sound("entity.horse.ambient", 0.6f, 1.2f);
            render();
        }
    }

    @Override
    void play() {
        if (pick < 0) {
            denySound();
            plugin.tell(player, "<gray>Click a horse on the left to back it first.");
            return;
        }
        int winner = HorseRace.pickWinner(field, plugin.random());
        int backed = pick;
        double odds = field.multiplier()[backed];
        long stake = stake();
        Resource resource = resource();
        long payout = winner == backed ? Payouts.apply(stake, odds) : 0;
        ResourceCasinoPlugin.Pending bet = plugin.placeBet(player, "Horse Racing", resource, stake, payout);
        if (bet == null) {
            denySound();
            render();
            return;
        }
        int raceFrames = plugin.raceTicks();
        int tail = 30;
        plan = HorseRace.plan(winner, raceFrames, tail, plugin.random());
        frame = 0;
        boolean onTrack = plugin.casino().track().run(plan);
        sound("block.note_block.bell", 1f, 1.0f);
        plugin.tell(player, "<gold>Horse Racing:</gold> <gray>And they're off! You backed " + horseName(backed)
                + (onTrack ? " <gray>(running live on the casino racetrack)" : ""));
        render();

        HorseRace.RacePlan race = plan;
        animate(tick -> {
            frame = tick;
            if (tick % 2 == 0 && viewing()) {
                for (int horse = 0; horse < HorseRace.HORSES; horse++) renderLane(horse);
            }
            if (tick % 20 == 10 && tick < raceFrames) {
                player.sendActionBar(Items.mm(horseName(race.leader(tick)) + " <gray>leads!"));
                sound("entity.horse.gallop", 0.5f, 1.0f);
            }
            if (tick == raceFrames) {
                plugin.settle(bet);
                player.sendActionBar(Items.mm(horseName(winner) + " <gold>wins!"));
                if (winner == backed) {
                    result(true, "<gold>Horse Racing:</gold> " + horseName(winner) + " <gold>wins!</gold> <gray>You get</gray> "
                            + Items.amount(resource, bet.payout) + " <gray>(" + Payouts.format(odds) + ")");
                } else {
                    result(false, "<gold>Horse Racing:</gold> " + horseName(winner) + " <gray>wins. " + horseName(backed)
                            + " <gray>came up short - you lost " + Items.amount(resource, stake) + "<gray>.");
                }
            }
            if (tick >= race.frames() + 10) {
                field = HorseRace.newField(plugin.random(), plugin.horseEdge());
                pick = -1;
                plan = null;
                if (viewing()) render();
                return false;
            }
            return true;
        });
    }
}
