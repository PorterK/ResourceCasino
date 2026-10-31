package net.kalob.ResourceCasino;

import java.util.TreeSet;

/**
 * The bet sizes offered as chips for a resource, scaled to its table limit:
 * always 1 and the limit, then the biggest powers of two in between, then
 * in-between values if the limit is small. Up to six chips.
 * <pre>
 *   limit 256 -> 1 16 32 64 128 256
 *   limit  64 -> 1  4  8 16  32  64
 *   limit   8 -> 1  2  3  4   6   8
 * </pre>
 */
public final class BetChips {

    public static final int MAX_CHIPS = 6;

    private BetChips() {}

    public static long[] forLimit(long limit) {
        long max = Math.max(1, limit);
        TreeSet<Long> chips = new TreeSet<>();
        chips.add(1L);
        chips.add(max);
        long power = Long.highestOneBit(max);
        while (chips.size() < MAX_CHIPS && power > 1) {
            chips.add(power);
            power >>= 1;
        }
        // Small limits: fill the gaps with midpoints until there are six (or no room left).
        while (chips.size() < MAX_CHIPS) {
            Long best = null;
            long gap = 1;
            Long previous = null;
            for (long chip : chips) {
                if (previous != null && chip - previous > gap) {
                    gap = chip - previous;
                    best = previous + gap / 2;
                }
                previous = chip;
            }
            if (best == null) break;
            chips.add(best);
        }
        return chips.stream().mapToLong(Long::longValue).toArray();
    }
}
