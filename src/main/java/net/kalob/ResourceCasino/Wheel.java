package net.kalob.ResourceCasino;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Spin the wheel: 24 equal segments, each with a multiplier. You stake, the
 * wheel lands on one segment, you get stake x multiplier back. The segment mix
 * is chosen so the average return stays under 1 (the default is 23/24 = 95.8%).
 */
public final class Wheel {

    public static final int SEGMENTS = 24;

    /** Default layout, clockwise from the top. 15 blanks, 3x 1x, 2x 1.5x, 2x 2x, 1x 3x and the 10x jackpot. */
    public static final List<Double> DEFAULT_LAYOUT = List.of(
            10.0, 0.0, 1.0, 0.0, 2.0, 0.0, 1.5, 0.0, 0.0, 1.0, 0.0, 3.0,
            0.0, 1.5, 0.0, 0.0, 2.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0);

    private final List<Double> segments;

    private Wheel(List<Double> segments) {
        this.segments = List.copyOf(segments);
    }

    public static Wheel defaults() {
        return new Wheel(DEFAULT_LAYOUT);
    }

    /**
     * Builds a wheel from config. Returns null if the layout is unusable: wrong number of
     * segments, a negative multiplier, or an average return of 100% or more (the house must win).
     */
    public static Wheel of(List<Double> layout) {
        if (layout == null || layout.size() != SEGMENTS) return null;
        for (Double multiplier : layout) {
            if (multiplier == null || multiplier < 0 || multiplier.isNaN() || multiplier.isInfinite()) return null;
        }
        Wheel wheel = new Wheel(layout);
        return wheel.expectedReturn() < 1.0 ? wheel : null;
    }

    public int size() { return segments.size(); }

    public double multiplier(int index) { return segments.get(Math.floorMod(index, segments.size())); }

    public List<Double> layout() { return Collections.unmodifiableList(segments); }

    public int spin(RandomGenerator random) { return random.nextInt(segments.size()); }

    public long payout(int index, long stake) { return Payouts.apply(stake, multiplier(index)); }

    public double expectedReturn() {
        double sum = 0;
        for (double multiplier : segments) sum += multiplier;
        return sum / segments.size();
    }

    public double maxMultiplier() {
        return Collections.max(segments);
    }

    /** Distinct multipliers, highest first, for the paytable. */
    public List<Double> tiers() {
        List<Double> tiers = new ArrayList<>(new java.util.TreeSet<>(segments).descendingSet());
        return tiers;
    }

    /** Ease-out: fast at first, then slowing to a stop. Shared by the screen and the big wheel so they agree. */
    public static double ease(double x) {
        double clamped = Math.max(0, Math.min(1, x));
        return 1 - Math.pow(1 - clamped, 3);
    }

    /** Glass/concrete colour for a segment, by how much it pays. */
    public static String dyeFor(double multiplier) {
        if (multiplier <= 0) return "GRAY";
        if (multiplier < 1.5) return "WHITE";
        if (multiplier < 2) return "LIGHT_BLUE";
        if (multiplier < 3) return "LIME";
        if (multiplier < 5) return "ORANGE";
        if (multiplier < 10) return "MAGENTA";
        return "YELLOW";
    }

    /** RGB for the same colour, used for the big wheel on the casino floor. */
    public static int rgbFor(double multiplier) {
        return switch (dyeFor(multiplier)) {
            case "GRAY" -> 0x3A3A3A;
            case "WHITE" -> 0xEEEEEE;
            case "LIGHT_BLUE" -> 0x3AAFD9;
            case "LIME" -> 0x70C21F;
            case "ORANGE" -> 0xF07613;
            case "MAGENTA" -> 0xBE46B4;
            default -> 0xFFD83D;
        };
    }

    /** What's written on a segment. */
    public static String label(double multiplier) {
        return multiplier <= 0 ? "✖" : Payouts.format(multiplier);
    }

    public int count(double multiplier) {
        int count = 0;
        for (double m : segments) if (m == multiplier) count++;
        return count;
    }
}
