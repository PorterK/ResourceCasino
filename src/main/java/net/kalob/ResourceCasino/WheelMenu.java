package net.kalob.ResourceCasino;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Spin the Wheel. The 24 segments run clockwise around the edge of the top five
 * rows; the lit segment races round and slows to a stop on the result. When the
 * big wheel on the casino floor is free, it spins to the same segment.
 */
final class WheelMenu extends GameMenu {

    /** Ring slots, clockwise from the top-left corner. */
    static final int[] RING = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            17, 26, 35,
            44, 43, 42, 41, 40, 39, 38, 37, 36,
            27, 18, 9
    };
    private static final int POINTER = 22, PAYTABLE = 13, LAST = 31;

    private int lit;
    private String last = "<gray>Spin to win up to ";

    WheelMenu(ResourceCasinoPlugin plugin, Player player) {
        super(plugin, player, "<dark_gray>Casino</dark_gray> <light_purple><bold>Spin the Wheel");
        lit = plugin.wheelTop();
        last += Payouts.format(plugin.wheel().maxMultiplier()) + "!";
    }

    @Override
    String actionName() { return "SPIN"; }

    @Override
    Material actionIcon() { return Material.SUNFLOWER; }

    @Override
    void render() {
        fill(Material.BLACK_STAINED_GLASS_PANE);
        renderRing();
        Wheel wheel = plugin.wheel();
        List<String> table = new ArrayList<>();
        for (double tier : wheel.tiers()) {
            table.add("<" + Items.hex(Wheel.rgbFor(tier)) + ">" + Wheel.label(tier) + "</" + Items.hex(Wheel.rgbFor(tier)) + "> <gray>"
                    + wheel.count(tier) + " of " + wheel.size() + " segments");
        }
        table.add("");
        table.add("<dark_gray>Pays back " + Payouts.percent(wheel.expectedReturn()) + " on average.");
        set(PAYTABLE, Items.icon(Material.BOOK, 1, false, "<gold><bold>The wheel", table));
        renderPointer();
        set(LAST, Items.icon(Material.NAME_TAG, last));
        renderBetBar();
    }

    private void renderRing() {
        Wheel wheel = plugin.wheel();
        for (int i = 0; i < RING.length; i++) {
            double m = wheel.multiplier(i);
            boolean on = i == lit;
            String dye = Wheel.dyeFor(m);
            Material material = Items.material(dye + (on ? "_CONCRETE" : "_STAINED_GLASS_PANE"));
            set(RING[i], Items.icon(material, 1, on, "<" + Items.hex(Wheel.rgbFor(m)) + "><bold>" + Wheel.label(m)));
        }
    }

    private void renderPointer() {
        double m = plugin.wheel().multiplier(lit);
        set(POINTER, Items.icon(Material.SPECTRAL_ARROW, 1, true, "<white>Pointing at <" + Items.hex(Wheel.rgbFor(m)) + "><bold>" + Wheel.label(m)));
    }

    @Override
    void play() {
        Wheel wheel = plugin.wheel();
        int result = wheel.spin(plugin.random());
        double m = wheel.multiplier(result);
        long stake = stake();
        Resource resource = resource();
        ResourceCasinoPlugin.Pending bet = plugin.placeBet(player, "the Wheel", resource, stake, wheel.payout(result, stake));
        if (bet == null) {
            denySound();
            render();
            return;
        }
        int start = plugin.wheelTop();
        int total = wheel.size() * 3 + Math.floorMod(result - start, wheel.size());
        int ticks = plugin.wheelTicks();
        boolean big = plugin.casino().bigWheel().spin(start, total, ticks);
        plugin.setWheelTop(result);
        if (big) plugin.tell(player, "<light_purple>The big wheel on the casino floor is spinning for you!");
        sound("block.chain.place", 0.8f, 0.6f);
        last = "<gray>Spinning...";
        render();

        animate(tick -> {
            int position = start + (int) Math.floor(total * Wheel.ease(Math.min(1.0, tick / (double) ticks)));
            int now = Math.floorMod(position, wheel.size());
            if (now != lit) {
                lit = now;
                sound("block.note_block.hat", 0.35f, 1.8f);
                if (viewing()) {
                    renderRing();
                    renderPointer();
                }
            }
            if (tick == ticks) {
                lit = result;
                plugin.settle(bet);
                last = bet.payout > 0
                        ? "<gold><bold>" + Wheel.label(m) + "</bold></gold> <gray>+" + Items.amount(resource, bet.payout)
                        : "<gray>No luck this time";
                result(m > 1, "<light_purple>Wheel:</light_purple> <gray>landed on</gray> <" + Items.hex(Wheel.rgbFor(m)) + "><bold>"
                        + Wheel.label(m) + "</bold></" + Items.hex(Wheel.rgbFor(m)) + ">"
                        + (bet.payout > 0 ? " <gray>You get</gray> " + Items.amount(resource, bet.payout)
                        : " <gray>You lost</gray> " + Items.amount(resource, stake)));
                if (m >= wheel.maxMultiplier()) sound("ui.toast.challenge_complete", 0.9f, 1.0f);
                if (viewing()) render();
                return false;
            }
            return true;
        });
    }
}
