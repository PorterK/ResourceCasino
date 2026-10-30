package net.kalob.ResourceCasino;

import java.util.random.RandomGenerator;

/**
 * A three-reel slot machine. Each reel lands on a symbol independently, weighted
 * so rare ores come up rarely. The paytable is set so the machine returns a bit
 * under what's put in over time ({@link #rtp()} computes it exactly).
 */
public final class Slots {

    public enum Symbol {
        //        item               name          weight  three-of-a-kind pays
        NETHERITE("NETHERITE_INGOT", "Netherite", 1, 150),
        DIAMOND("DIAMOND", "Diamond", 2, 50),
        EMERALD("EMERALD", "Emerald", 3, 25),
        GOLD("GOLD_INGOT", "Gold", 4, 15),
        IRON("IRON_INGOT", "Iron", 5, 10),
        COPPER("COPPER_INGOT", "Copper", 6, 8),
        CHERRY("SWEET_BERRIES", "Berries", 5, 10),
        COAL("COAL", "Coal", 6, 2);

        public final String material;
        public final String displayName;
        public final int weight;
        public final int triple;

        Symbol(String material, String displayName, int weight, int triple) {
            this.material = material;
            this.displayName = displayName;
            this.weight = weight;
            this.triple = triple;
        }
    }

    /** Payout for exactly one / exactly two berries anywhere on the line (when it isn't a triple). */
    public static final double ONE_CHERRY = 1;
    public static final double TWO_CHERRIES = 3;
    /** Three ingots of mixed metals (netherite, gold, iron, copper) pay a small consolation. */
    public static final double ANY_THREE_INGOTS = 2;

    private static final int TOTAL_WEIGHT;

    static {
        int total = 0;
        for (Symbol symbol : Symbol.values()) total += symbol.weight;
        TOTAL_WEIGHT = total;
    }

    private Slots() {}

    public static Symbol spinReel(RandomGenerator random) {
        int roll = random.nextInt(TOTAL_WEIGHT);
        for (Symbol symbol : Symbol.values()) {
            roll -= symbol.weight;
            if (roll < 0) return symbol;
        }
        return Symbol.COAL;
    }

    public static Symbol[] spin(RandomGenerator random) {
        return new Symbol[]{spinReel(random), spinReel(random), spinReel(random)};
    }

    static boolean isIngot(Symbol symbol) {
        return symbol == Symbol.NETHERITE || symbol == Symbol.GOLD || symbol == Symbol.IRON || symbol == Symbol.COPPER;
    }

    /** Total-return multiplier for a line (0 = lose, 1 = stake back, 150 = jackpot). */
    public static double multiplier(Symbol a, Symbol b, Symbol c) {
        if (a == b && b == c) return a.triple;
        if (isIngot(a) && isIngot(b) && isIngot(c)) return ANY_THREE_INGOTS;
        int cherries = (a == Symbol.CHERRY ? 1 : 0) + (b == Symbol.CHERRY ? 1 : 0) + (c == Symbol.CHERRY ? 1 : 0);
        if (cherries == 2) return TWO_CHERRIES;
        if (cherries == 1) return ONE_CHERRY;
        return 0;
    }

    public static double multiplier(Symbol[] line) {
        return multiplier(line[0], line[1], line[2]);
    }

    /** Short description of why a line paid, for the result message. */
    public static String describe(Symbol[] line) {
        Symbol a = line[0], b = line[1], c = line[2];
        if (a == b && b == c) return "Three " + a.displayName + "!";
        if (isIngot(a) && isIngot(b) && isIngot(c)) return "Three ingots";
        int cherries = (a == Symbol.CHERRY ? 1 : 0) + (b == Symbol.CHERRY ? 1 : 0) + (c == Symbol.CHERRY ? 1 : 0);
        if (cherries == 2) return "Two berries";
        if (cherries == 1) return "Berry - stake back";
        return "No win";
    }

    /** Exact return-to-player: the average fraction of each bet paid back, over every possible line. */
    public static double rtp() {
        double total = 0;
        double norm = Math.pow(TOTAL_WEIGHT, 3);
        for (Symbol a : Symbol.values()) {
            for (Symbol b : Symbol.values()) {
                for (Symbol c : Symbol.values()) {
                    total += (double) a.weight * b.weight * c.weight / norm * multiplier(a, b, c);
                }
            }
        }
        return total;
    }

    /** Chance a single spin pays anything at all. */
    public static double hitRate() {
        double total = 0;
        double norm = Math.pow(TOTAL_WEIGHT, 3);
        for (Symbol a : Symbol.values()) {
            for (Symbol b : Symbol.values()) {
                for (Symbol c : Symbol.values()) {
                    if (multiplier(a, b, c) > 0) total += (double) a.weight * b.weight * c.weight / norm;
                }
            }
        }
        return total;
    }

    public static int totalWeight() { return TOTAL_WEIGHT; }
}
