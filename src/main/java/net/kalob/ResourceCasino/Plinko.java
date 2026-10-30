package net.kalob.ResourceCasino;

import java.util.random.RandomGenerator;

/**
 * Plinko: the ball drops through 8 rows of pegs, going left or right at each one
 * (50/50), and lands in one of 9 bins. The middle bins are the likeliest and pay
 * least; the edges are rare and pay 12x. On average it returns 95.9%.
 */
public final class Plinko {

    public static final int ROWS = 8;
    public static final int BINS = ROWS + 1;

    /** Bin payouts, left to right. */
    public static final double[] MULTIPLIERS = {12, 3, 1.2, 0.7, 0.4, 0.7, 1.2, 3, 12};

    private Plinko() {}

    /** One drop: which way the ball went at each row. */
    public record Drop(boolean[] rights) {

        /** Bin index 0..8 (the number of times it went right). */
        public int bin() {
            int bin = 0;
            for (boolean right : rights) if (right) bin++;
            return bin;
        }

        /**
         * Horizontal offset after {@code rows} bounces, in half-peg units:
         * 0 = centre, -rows..rows in steps of 2.
         */
        public int offsetAfter(int rows) {
            int offset = 0;
            for (int i = 0; i < Math.min(rows, rights.length); i++) offset += rights[i] ? 1 : -1;
            return offset;
        }

        public double multiplier() {
            return MULTIPLIERS[bin()];
        }
    }

    public static Drop drop(RandomGenerator random) {
        boolean[] rights = new boolean[ROWS];
        for (int i = 0; i < ROWS; i++) rights[i] = random.nextBoolean();
        return new Drop(rights);
    }

    /** Chance of landing in each bin (binomial). */
    public static double chance(int bin) {
        return binomial(ROWS, bin) / Math.pow(2, ROWS);
    }

    public static double expectedReturn() {
        double sum = 0;
        for (int bin = 0; bin < BINS; bin++) sum += chance(bin) * MULTIPLIERS[bin];
        return sum;
    }

    static long binomial(int n, int k) {
        long result = 1;
        for (int i = 1; i <= k; i++) result = result * (n - k + i) / i;
        return result;
    }
}
