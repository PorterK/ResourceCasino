package net.kalob.ResourceCasino;

import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.*;

/** Every game must favour the house, exactly as advertised. */
class GamesTest {

    @Test
    void everyRouletteBetKeeps2Point7Percent() {
        for (Roulette.Kind kind : Roulette.Kind.values()) {
            int value = kind == Roulette.Kind.STRAIGHT ? 17 : kind == Roulette.Kind.DOZEN ? 2 : 0;
            assertEquals(36.0 / 37, new Roulette.Bet(kind, value).expectedReturn(), 1e-12, kind.name());
        }
        for (int n = 0; n <= 36; n++) assertEquals(36.0 / 37, Roulette.Bet.straight(n).expectedReturn(), 1e-12);
    }

    @Test
    void zeroBeatsEveryOutsideBet() {
        for (Roulette.Kind kind : Roulette.Kind.values()) {
            if (kind == Roulette.Kind.STRAIGHT) continue;
            for (int dozen = 0; dozen <= 3; dozen++) assertFalse(new Roulette.Bet(kind, dozen).wins(0), kind.name());
        }
    }

    @Test
    void rouletteColoursMatchARealWheel() {
        int red = 0;
        for (int n = 1; n <= 36; n++) if (Roulette.isRed(n)) red++;
        assertEquals(18, red);
        assertTrue(Roulette.isRed(1));
        assertTrue(Roulette.isBlack(2));
        assertFalse(Roulette.isRed(0) || Roulette.isBlack(0));
        assertEquals(37, java.util.Arrays.stream(Roulette.WHEEL_ORDER).distinct().count());
    }

    @Test
    void slotsPayBackLessThanTheyTake() {
        double rtp = Slots.rtp();
        assertTrue(rtp < 0.97, "slots too generous: " + rtp);
        assertTrue(rtp > 0.88, "slots too stingy: " + rtp);
        assertEquals(150, Slots.multiplier(Slots.Symbol.NETHERITE, Slots.Symbol.NETHERITE, Slots.Symbol.NETHERITE));
        assertEquals(0, Slots.multiplier(Slots.Symbol.COAL, Slots.Symbol.DIAMOND, Slots.Symbol.EMERALD));
        assertEquals(Slots.ONE_CHERRY, Slots.multiplier(Slots.Symbol.COAL, Slots.Symbol.CHERRY, Slots.Symbol.EMERALD));
    }

    @Test
    void defaultWheelFavoursTheHouse() {
        Wheel wheel = Wheel.defaults();
        assertEquals(Wheel.SEGMENTS, wheel.size());
        assertEquals(23.0 / 24, wheel.expectedReturn(), 1e-12);
        assertEquals(10, wheel.maxMultiplier());
    }

    @Test
    void generousWheelsAreRejected() {
        assertNull(Wheel.of(java.util.Collections.nCopies(24, 1.0)), "break-even wheel accepted");
        assertNull(Wheel.of(java.util.Collections.nCopies(10, 0.5)), "wrong size accepted");
        assertNotNull(Wheel.of(java.util.Collections.nCopies(24, 0.9)));
    }

    @Test
    void everyHorsePaysUnderFairOdds() {
        SplittableRandom random = new SplittableRandom(1);
        for (double edge : new double[]{0.02, 0.10, 0.25}) {
            for (int i = 0; i < 200; i++) {
                HorseRace.Field field = HorseRace.newField(random, edge);
                double sum = 0;
                for (int horse = 0; horse < HorseRace.HORSES; horse++) {
                    sum += field.winChance()[horse];
                    assertTrue(field.expectedReturn(horse) <= 1 - edge + 1e-9,
                            "horse " + horse + " returns " + field.expectedReturn(horse) + " at edge " + edge);
                }
                assertEquals(1.0, sum, 1e-9);
            }
        }
    }

    @Test
    void theChosenWinnerAlwaysCrossesFirst() {
        SplittableRandom random = new SplittableRandom(2);
        for (int i = 0; i < 500; i++) {
            int winner = random.nextInt(HorseRace.HORSES);
            HorseRace.RacePlan plan = HorseRace.plan(winner, 160, 30, random);
            int first = -1;
            for (int t = 0; t <= plan.frames() && first < 0; t++) {
                for (int horse = 0; horse < HorseRace.HORSES; horse++) {
                    if (plan.at(horse, t) >= 1.0) {
                        assertEquals(winner, horse, "wrong horse first at frame " + t);
                        assertEquals(plan.raceFrames(), t);
                        first = horse;
                    }
                }
            }
            for (int horse = 0; horse < HorseRace.HORSES; horse++) {
                assertEquals(1.0, plan.at(horse, plan.frames()), 1e-9, "horse " + horse + " never finished");
                for (int t = 1; t <= plan.frames(); t++) assertTrue(plan.at(horse, t) >= plan.at(horse, t - 1), "horse went backwards");
            }
        }
    }

    @Test
    void payoutsRoundDown() {
        assertEquals(1, Payouts.apply(1, 1.5));
        assertEquals(3, Payouts.apply(2, 1.5));
        assertEquals(0, Payouts.apply(5, 0));
        assertEquals(26, Payouts.apply(10, 2.6));
        assertEquals("1.5x", Payouts.format(1.5));
        assertEquals("10x", Payouts.format(10));
    }

    @Test
    void plinkoFavoursTheHouse() {
        assertEquals(245.6 / 256, Plinko.expectedReturn(), 1e-9);
        double total = 0;
        for (int bin = 0; bin < Plinko.BINS; bin++) total += Plinko.chance(bin);
        assertEquals(1.0, total, 1e-12);
        for (int bin = 0; bin < Plinko.BINS; bin++) {
            assertEquals(Plinko.MULTIPLIERS[bin], Plinko.MULTIPLIERS[Plinko.BINS - 1 - bin], "board isn't symmetric");
        }
    }

    @Test
    void plinkoPathsEndInTheirBin() {
        SplittableRandom random = new SplittableRandom(4);
        for (int i = 0; i < 1000; i++) {
            Plinko.Drop drop = Plinko.drop(random);
            assertEquals(drop.bin() * 2 - Plinko.ROWS, drop.offsetAfter(Plinko.ROWS));
            for (int row = 0; row <= Plinko.ROWS; row++) assertTrue(Math.abs(drop.offsetAfter(row)) <= row);
        }
    }

    @Test
    void betChipsScaleToTheTableLimit() {
        assertArrayEquals(new long[]{1, 16, 32, 64, 128, 256}, BetChips.forLimit(256));
        assertArrayEquals(new long[]{1, 8, 16, 32, 64, 128}, BetChips.forLimit(128));
        assertArrayEquals(new long[]{1, 4, 8, 16, 32, 64}, BetChips.forLimit(64));
        assertArrayEquals(new long[]{1, 2, 4, 8, 16, 32}, BetChips.forLimit(32));
        assertArrayEquals(new long[]{1, 2, 3, 4, 6, 8}, BetChips.forLimit(8));
        assertArrayEquals(new long[]{1}, BetChips.forLimit(1));
        for (long limit = 1; limit <= 1000; limit++) {
            long[] chips = BetChips.forLimit(limit);
            assertEquals(1, chips[0]);
            assertEquals(limit, chips[chips.length - 1]);
            assertTrue(chips.length <= BetChips.MAX_CHIPS);
            assertEquals(Math.min(limit, BetChips.MAX_CHIPS), chips.length, "limit " + limit);
        }
    }
}
