package net.kalob.ResourceCasino;

/** Shared payout arithmetic. Fractions always round down, in the house's favour. */
public final class Payouts {

    private Payouts() {}

    /** Total returned to the player (stake included) for a multiplier like 2.5x. */
    public static long apply(long stake, double multiplier) {
        if (multiplier <= 0 || stake <= 0) return 0;
        return (long) Math.floor(stake * multiplier + 1e-9);
    }

    /** "2x", "1.5x", "10x". */
    public static String format(double multiplier) {
        if (multiplier == Math.rint(multiplier)) return (long) multiplier + "x";
        return String.format(java.util.Locale.ROOT, "%.1fx", multiplier);
    }

    /** "97.3%" */
    public static String percent(double fraction) {
        return String.format(java.util.Locale.ROOT, "%.1f%%", fraction * 100);
    }
}
