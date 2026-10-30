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
    void everyDuckPaysUnderFairOdds() {
        SplittableRandom random = new SplittableRandom(1);
        for (double edge : new double[]{0.02, 0.10, 0.25}) {
            for (int i = 0; i < 200; i++) {
                DuckDerby.Field field = DuckDerby.newField(random, edge);
                double sum = 0;
                for (int duck = 0; duck < DuckDerby.DUCKS; duck++) {
                    sum += field.winChance()[duck];
                    assertTrue(field.expectedReturn(duck) <= 1 - edge + 1e-9,
                            "duck " + duck + " returns " + field.expectedReturn(duck) + " at edge " + edge);
                }
                assertEquals(1.0, sum, 1e-9);
            }
        }
    }

    @Test
    void theChosenWinnerAlwaysCrossesFirst() {
        SplittableRandom random = new SplittableRandom(2);
        for (int i = 0; i < 500; i++) {
            int winner = random.nextInt(DuckDerby.DUCKS);
            DuckDerby.RacePlan plan = DuckDerby.plan(winner, 160, 30, random);
            int first = -1;
            for (int t = 0; t <= plan.frames() && first < 0; t++) {
                for (int duck = 0; duck < DuckDerby.DUCKS; duck++) {
                    if (plan.at(duck, t) >= 1.0) {
                        assertEquals(winner, duck, "wrong duck first at frame " + t);
                        assertEquals(plan.raceFrames(), t);
                        first = duck;
                    }
                }
            }
            for (int duck = 0; duck < DuckDerby.DUCKS; duck++) {
                assertEquals(1.0, plan.at(duck, plan.frames()), 1e-9, "duck " + duck + " never finished");
                for (int t = 1; t <= plan.frames(); t++) assertTrue(plan.at(duck, t) >= plan.at(duck, t - 1), "duck went backwards");
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
}
