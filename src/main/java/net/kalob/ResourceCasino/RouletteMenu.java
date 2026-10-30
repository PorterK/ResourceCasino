package net.kalob.ResourceCasino;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The roulette table. Numbers 1-36 fill the top four rows (the stack size shows
 * the number), outside bets sit underneath. Click to drop a chip, right-click to
 * pick one up, then SPIN.
 * <pre>
 *  1  2  3  4  5  6  7  8  9
 * 10 11 12 13 14 15 16 17 18
 * 19 20 21 22 23 24 25 26 27
 * 28 29 30 31 32 33 34 35 36
 *  0 1st 2nd 3rd RED BLK ODD EVEN history
 * 1-18 19-36 resource chip total clear rebet lobby SPIN
 * </pre>
 */
final class RouletteMenu extends GameMenu {

    static final int ZERO = 36, DOZEN_1 = 37, DOZEN_2 = 38, DOZEN_3 = 39, RED = 40, BLACK = 41, ODD = 42, EVEN = 43,
            HISTORY = 44, LOW = 45, HIGH = 46, RESOURCE_SLOT = 47, CHIP = 48, TOTAL = 49, CLEAR = 50, REBET = 51;

    private static final long[] CHIPS = {1, 2, 5, 10, 25, 50, 100};

    private final Map<Roulette.Bet, Long> bets = new LinkedHashMap<>();
    private Map<Roulette.Bet, Long> lastBets = Map.of();
    private final Deque<Integer> history = new ArrayDeque<>();
    private int chipIndex;
    private int ball = -1;
    private Resource lockedResource;

    RouletteMenu(ResourceCasinoPlugin plugin, Player player) {
        super(plugin, player, "<dark_gray>Casino</dark_gray> <red><bold>Roulette");
    }

    @Override
    String actionName() { return "SPIN"; }

    @Override
    Material actionIcon() { return Material.CLOCK; }

    long chip() { return CHIPS[chipIndex]; }

    long total() {
        long sum = 0;
        for (long amount : bets.values()) sum += amount;
        return sum;
    }

    static int slotOf(int pocket) {
        return pocket == 0 ? ZERO : pocket - 1;
    }

    static Roulette.Bet betAt(int slot) {
        if (slot >= 0 && slot < 36) return Roulette.Bet.straight(slot + 1);
        return switch (slot) {
            case ZERO -> Roulette.Bet.straight(0);
            case DOZEN_1 -> Roulette.Bet.dozen(1);
            case DOZEN_2 -> Roulette.Bet.dozen(2);
            case DOZEN_3 -> Roulette.Bet.dozen(3);
            case RED -> Roulette.Bet.of(Roulette.Kind.RED);
            case BLACK -> Roulette.Bet.of(Roulette.Kind.BLACK);
            case ODD -> Roulette.Bet.of(Roulette.Kind.ODD);
            case EVEN -> Roulette.Bet.of(Roulette.Kind.EVEN);
            case LOW -> Roulette.Bet.of(Roulette.Kind.LOW);
            case HIGH -> Roulette.Bet.of(Roulette.Kind.HIGH);
            default -> null;
        };
    }

    @Override
    void render() {
        for (int pocket = 0; pocket <= 36; pocket++) renderPocket(pocket);
        renderBet(DOZEN_1, Material.ORANGE_WOOL, "<gold>1st 12");
        renderBet(DOZEN_2, Material.ORANGE_WOOL, "<gold>2nd 12");
        renderBet(DOZEN_3, Material.ORANGE_WOOL, "<gold>3rd 12");
        renderBet(RED, Material.RED_WOOL, "<red>Red");
        renderBet(BLACK, Material.BLACK_WOOL, "<dark_gray>Black");
        renderBet(ODD, Material.WHITE_WOOL, "<white>Odd");
        renderBet(EVEN, Material.LIGHT_GRAY_WOOL, "<white>Even");
        renderBet(LOW, Material.CYAN_WOOL, "<aqua>1-18");
        renderBet(HIGH, Material.BLUE_WOOL, "<aqua>19-36");

        List<String> past = new ArrayList<>();
        for (int pocket : history) past.add(pocketName(pocket));
        if (past.isEmpty()) past.add("<gray>No spins yet");
        set(HISTORY, Items.icon(Material.PAPER, 1, false, "<white>Last spins", past));

        Resource r = resource();
        set(RESOURCE_SLOT, Items.icon(Items.unit(r), 1, true, "<gray>Betting with</gray> <" + Items.hex(r.color()) + "><bold>" + r.displayName(),
                List.of("<gray>Balance: " + Items.amount(r, balance()),
                        total() > 0 ? "<red>Clear your bets to switch" : "<yellow>Click</yellow> <gray>to switch resource")));
        set(CHIP, Items.icon(Material.SUNFLOWER, (int) Math.min(64, chip()), false, "<yellow>Chip: " + Items.amount(r, chip()),
                List.of("<yellow>Click</yellow> <gray>bigger chip", "<yellow>Right-click</yellow> <gray>smaller chip")));
        set(TOTAL, Items.icon(Material.GOLD_INGOT, 1, total() > 0, "<white>On the table: " + Items.amount(r, total()),
                List.of("<gray>Table limit: " + Items.amount(r, plugin.maxBet(r)))));
        set(CLEAR, Items.icon(Material.BUCKET, "<red>Clear bets"));
        set(REBET, Items.icon(Material.REPEATER, "<green>Same bets again", "<gray>Puts back your last spin's chips"));
        set(LOBBY, Items.icon(Material.DARK_OAK_DOOR, "<gray>Back to the lobby"));
        List<String> lore = new ArrayList<>();
        lore.add("<gray>On the table: " + Items.amount(r, total()));
        if (total() > balance()) lore.add("<red>Not enough " + r.displayName() + " - visit the cashier");
        set(ACTION, Items.icon(Material.CLOCK, 1, true, "<gold><bold>SPIN", lore));
    }

    private void renderPocket(int pocket) {
        int slot = slotOf(pocket);
        if (pocket == ball) {
            set(slot, Items.icon(Material.SNOWBALL, 1, true, "<white><bold>" + pocket));
            return;
        }
        Material material = pocket == 0 ? Material.LIME_CONCRETE
                : Roulette.isRed(pocket) ? Material.RED_CONCRETE : Material.BLACK_CONCRETE;
        Roulette.Bet bet = Roulette.Bet.straight(pocket);
        long on = bets.getOrDefault(bet, 0L);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Pays 35 to 1");
        if (on > 0) lore.add("<yellow>Your bet: " + Items.amount(resource(), on));
        set(slot, Items.icon(material, Math.max(1, pocket), on > 0, pocketName(pocket), lore));
    }

    private void renderBet(int slot, Material material, String name) {
        Roulette.Bet bet = betAt(slot);
        long on = bets.getOrDefault(bet, 0L);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Pays " + bet.kind().pays + " to 1");
        if (on > 0) lore.add("<yellow>Your bet: " + Items.amount(resource(), on));
        set(slot, Items.icon(material, 1, on > 0, "<bold>" + name, lore));
    }

    static String pocketName(int pocket) {
        if (pocket == 0) return "<green><bold>0";
        return (Roulette.isRed(pocket) ? "<red><bold>" : "<dark_gray><bold>") + pocket + " " + Roulette.colorName(pocket);
    }

    @Override
    void click(int slot, ClickType click) {
        Roulette.Bet bet = betAt(slot);
        if (bet != null) {
            long current = bets.getOrDefault(bet, 0L);
            if (click.isRightClick()) {
                long next = Math.max(0, current - chip());
                if (next == 0) bets.remove(bet);
                else bets.put(bet, next);
            } else {
                if (total() + chip() > plugin.maxBet(resource())) {
                    denySound();
                    plugin.tell(player, "<red>The table limit is " + Items.amount(resource(), plugin.maxBet(resource())) + "<red>.");
                    return;
                }
                bets.put(bet, current + chip());
            }
            sound("block.stone_button.click_on", 0.5f, 1.8f);
            render();
            return;
        }
        switch (slot) {
            case RESOURCE_SLOT -> {
                if (total() > 0) {
                    denySound();
                    return;
                }
                prefs.resource = click.isRightClick() ? prefs.resource.previous() : prefs.resource.next();
            }
            case CHIP -> chipIndex = click.isRightClick() ? Math.max(0, chipIndex - 1) : Math.min(CHIPS.length - 1, chipIndex + 1);
            case CLEAR -> bets.clear();
            case REBET -> {
                if (!lastBets.isEmpty()) {
                    bets.clear();
                    bets.putAll(lastBets);
                    if (lockedResource != null) prefs.resource = lockedResource;
                }
            }
            case LOBBY -> {
                clickSound();
                switchTo(new LobbyMenu(plugin, player));
                return;
            }
            case ACTION -> {
                play();
                return;
            }
            default -> {
                return;
            }
        }
        clickSound();
        render();
    }

    @Override
    void play() {
        if (bets.isEmpty()) {
            denySound();
            plugin.tell(player, "<gray>Click the table to place chips first.");
            return;
        }
        int pocket = Roulette.spin(plugin.random());
        long stake = total();
        long payout = 0;
        for (Map.Entry<Roulette.Bet, Long> entry : bets.entrySet()) payout += entry.getKey().payout(entry.getValue(), pocket);
        Resource resource = resource();
        ResourceCasinoPlugin.Pending bet = plugin.placeBet(player, "Roulette", resource, stake, payout);
        if (bet == null) {
            denySound();
            return;
        }
        Map<Roulette.Bet, Long> placed = new LinkedHashMap<>(bets);
        lastBets = placed;
        lockedResource = resource;
        sound("block.chain.place", 0.8f, 0.8f);

        // The ball runs round the real wheel order, slowing down, and drops into the result.
        int startIndex = plugin.random().nextInt(Roulette.POCKETS);
        int resultIndex = 0;
        for (int i = 0; i < Roulette.WHEEL_ORDER.length; i++) if (Roulette.WHEEL_ORDER[i] == pocket) resultIndex = i;
        int steps = Roulette.POCKETS * 2 + Math.floorMod(resultIndex - startIndex, Roulette.POCKETS);
        List<Integer> stepTicks = new ArrayList<>();
        double t = 0;
        for (int i = 0; i <= steps; i++) {
            double progress = i / (double) steps;
            t += 0.6 + 4.5 * progress * progress * progress;
            stepTicks.add((int) Math.round(t));
        }
        int end = stepTicks.getLast();
        if (plugin.casino().rouletteWheel().spin(pocket, end + 6, payout > stake) && !plugin.casino().isCasinoWorld(player.getWorld())) {
            plugin.tell(player, "<red>Roulette:</red> <gray>your ball is spinning on the real wheel on the casino floor.");
        }
        int[] step = {0};
        animate(tick -> {
            while (step[0] <= steps && stepTicks.get(step[0]) <= tick) {
                int previous = ball;
                ball = Roulette.WHEEL_ORDER[Math.floorMod(startIndex + step[0], Roulette.POCKETS)];
                if (viewing()) {
                    if (previous >= 0) {
                        int keep = ball;
                        ball = -1;
                        renderPocket(previous);
                        ball = keep;
                    }
                    renderPocket(ball);
                }
                sound("block.note_block.hat", 0.3f, 1.2f + (float) step[0] / steps);
                step[0]++;
            }
            if (tick == end + 6) {
                plugin.settle(bet);
                history.addFirst(pocket);
                while (history.size() > 8) history.removeLast();
                long net = bet.payout - stake;
                result(net > 0, "<red>Roulette:</red> " + pocketName(pocket) + "<reset><gray>. "
                        + (bet.payout > 0 ? "You get " + Items.amount(resource, bet.payout) + " <gray>back" : "You lost " + Items.amount(resource, stake))
                        + " <dark_gray>(" + (net >= 0 ? "+" : "") + net + ")");
                bets.clear();
                if (viewing()) render();
            }
            if (tick == end + 46) {
                ball = -1;
                if (viewing()) render();
                return false;
            }
            return true;
        });
    }

    @Override
    void closed() {
        if (!busy()) bets.clear();
    }

    // Unused by roulette: it has its own bottom row.
    @Override
    List<String> actionLore() { return List.of(); }
}
