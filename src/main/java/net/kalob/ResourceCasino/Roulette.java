package net.kalob.ResourceCasino;

import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * European single-zero roulette. Every bet returns 36/37 of what's staked on
 * average, so the house keeps 2.7%: the green zero beats every outside bet.
 */
public final class Roulette {

    public static final int POCKETS = 37;

    /** Pocket order around a real European wheel, used for the ball animation. */
    public static final int[] WHEEL_ORDER = {
            0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10,
            5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26
    };

    private static final Set<Integer> RED = Set.of(
            1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);

    private Roulette() {}

    public enum Kind {
        STRAIGHT(35), RED(1), BLACK(1), ODD(1), EVEN(1), LOW(1), HIGH(1), DOZEN(2);

        /** Winnings per unit staked, "35 to 1". The stake comes back on top. */
        public final int pays;

        Kind(int pays) { this.pays = pays; }
    }

    /** A bet. {@code value} is the number for STRAIGHT and 1-3 for DOZEN, otherwise 0. */
    public record Bet(Kind kind, int value) {

        public static Bet straight(int number) { return new Bet(Kind.STRAIGHT, number); }

        public static Bet dozen(int which) { return new Bet(Kind.DOZEN, which); }

        public static Bet of(Kind kind) { return new Bet(kind, 0); }

        public boolean wins(int pocket) {
            if (kind == Kind.STRAIGHT) return pocket == value;
            if (pocket == 0) return false;
            return switch (kind) {
                case RED -> isRed(pocket);
                case BLACK -> !isRed(pocket);
                case ODD -> pocket % 2 == 1;
                case EVEN -> pocket % 2 == 0;
                case LOW -> pocket <= 18;
                case HIGH -> pocket >= 19;
                case DOZEN -> (pocket - 1) / 12 + 1 == value;
                case STRAIGHT -> false;
            };
        }

        public String label() {
            return switch (kind) {
                case STRAIGHT -> "Number " + value;
                case RED -> "Red";
                case BLACK -> "Black";
                case ODD -> "Odd";
                case EVEN -> "Even";
                case LOW -> "1-18";
                case HIGH -> "19-36";
                case DOZEN -> switch (value) {
                    case 1 -> "1st 12";
                    case 2 -> "2nd 12";
                    default -> "3rd 12";
                };
            };
        }

        /** Total returned (stake included) if this bet wins on {@code pocket}, else 0. */
        public long payout(long stake, int pocket) {
            return wins(pocket) ? stake * (kind.pays + 1L) : 0;
        }

        /** Average fraction of the stake that comes back. */
        public double expectedReturn() {
            int wins = 0;
            for (int pocket = 0; pocket < POCKETS; pocket++) {
                if (wins(pocket)) wins++;
            }
            return wins * (kind.pays + 1.0) / POCKETS;
        }
    }

    public static boolean isRed(int pocket) { return RED.contains(pocket); }

    public static boolean isBlack(int pocket) { return pocket != 0 && !isRed(pocket); }

    public static int spin(RandomGenerator random) { return random.nextInt(POCKETS); }

    public static String colorName(int pocket) {
        return pocket == 0 ? "Green" : isRed(pocket) ? "Red" : "Black";
    }
}
