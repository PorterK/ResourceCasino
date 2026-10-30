package net.kalob.ResourceCasino;

import java.util.random.RandomGenerator;

/**
 * Horse racing: five horses run one lap, you back one. Each race the horses get a
 * fresh "form" (win chance), and the odds board pays a bit less than fair odds on
 * every horse, so whichever horse you pick the house keeps {@code houseEdge} on average.
 */
public final class HorseRace {

    public static final int HORSES = 5;

    public static final String[] NAMES = {"Thunderhoof", "Lucky Clover", "Midnight Express", "Sir Gallops", "Hay Now"};

    /** Lane colours (RGB), lane 1 to 5. */
    public static final int[] COLORS = {0xFF5555, 0xFFD43B, 0x55FF55, 0x5599FF, 0xC77DFF};

    /** Lane colours as wool / glass names. */
    public static final String[] DYES = {"RED", "YELLOW", "LIME", "LIGHT_BLUE", "PURPLE"};

    /** Win chances handed out each race: a favourite, a couple in the middle, and a long shot. */
    static final double[] FORM = {0.34, 0.26, 0.19, 0.13, 0.08};

    private HorseRace() {}

    /** One race card: each horse's chance to win and what it pays (total return per unit staked). */
    public record Field(double[] winChance, double[] multiplier) {

        public double expectedReturn(int horse) {
            return winChance[horse] * multiplier[horse];
        }

        public int favourite() {
            int best = 0;
            for (int i = 1; i < HORSES; i++) if (winChance[i] > winChance[best]) best = i;
            return best;
        }
    }

    public static Field newField(RandomGenerator random, double houseEdge) {
        double[] chances = FORM.clone();
        for (int i = chances.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            double t = chances[i];
            chances[i] = chances[j];
            chances[j] = t;
        }
        double[] multipliers = new double[HORSES];
        for (int i = 0; i < HORSES; i++) multipliers[i] = multiplierFor(chances[i], houseEdge);
        return new Field(chances, multipliers);
    }

    /** Fair odds minus the house edge, rounded down to a tenth (so the board reads "3.3x"). */
    static double multiplierFor(double winChance, double houseEdge) {
        double fair = (1.0 - houseEdge) / winChance;
        return Math.max(1.1, Math.floor(fair * 10 + 1e-9) / 10.0);
    }

    public static int pickWinner(Field field, RandomGenerator random) {
        double roll = random.nextDouble();
        for (int i = 0; i < HORSES; i++) {
            roll -= field.winChance()[i];
            if (roll < 0) return i;
        }
        return HORSES - 1;
    }

    /**
     * Plans how the race looks, frame by frame, so that {@code winner} crosses the line
     * first at frame {@code raceFrames}. Horses surge and stall at random along the way, so
     * the leader changes and the long shot can look like it's about to steal it.
     */
    public static RacePlan plan(int winner, int raceFrames, int tailFrames, RandomGenerator random) {
        int frames = raceFrames + tailFrames;
        double[][] progress = new double[HORSES][frames + 1];
        for (int horse = 0; horse < HORSES; horse++) {
            double[] steps = new double[raceFrames];
            double sum = 0;
            double surge = 1.0;
            for (int t = 0; t < raceFrames; t++) {
                if (t % 10 == 0) surge = 0.55 + random.nextDouble() * 0.9;
                steps[t] = surge * (0.7 + random.nextDouble() * 0.6);
                sum += steps[t];
            }
            // Where this horse is when the winner crosses the line.
            double finish = horse == winner ? 1.0 : 0.80 + random.nextDouble() * 0.17;
            double acc = 0;
            progress[horse][0] = 0;
            for (int t = 0; t < raceFrames; t++) {
                acc += steps[t];
                progress[horse][t + 1] = acc / sum * finish;
            }
            progress[horse][raceFrames] = finish;
            // After the winner is home, the rest gallop over the line.
            double speed = (1.0 - finish) / Math.max(1, tailFrames - 2) * (1.05 + random.nextDouble() * 0.6);
            for (int t = raceFrames + 1; t <= frames; t++) {
                progress[horse][t] = Math.min(1.0, progress[horse][t - 1] + speed);
            }
        }
        return new RacePlan(winner, raceFrames, progress);
    }

    /** Frame-by-frame positions (0 = start line, 1 = finish line) for every horse. */
    public record RacePlan(int winner, int raceFrames, double[][] progress) {

        public int frames() { return progress[0].length - 1; }

        public double at(int horse, int frame) {
            double[] row = progress[horse];
            return row[Math.max(0, Math.min(row.length - 1, frame))];
        }

        /** Which horse is in front at this frame. */
        public int leader(int frame) {
            int best = 0;
            for (int i = 1; i < HORSES; i++) if (at(i, frame) > at(best, frame)) best = i;
            return best;
        }
    }
}
