package net.kalob.ResourceCasino;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Villager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * In-server integration test, run by CI against a real Paper server:
 * {@code java -Dresourcecasino.selftest=true -jar paper.jar}.
 * Builds the casino floor, checks money handling and the house edge, spins the
 * big wheel and runs a duck race, then writes
 * {@code plugins/ResourceCasino/selftest-result.txt} and shuts the server down.
 */
final class SelfTest {

    static final String PROPERTY = "resourcecasino.selftest";

    private final ResourceCasinoPlugin plugin;
    private final List<String> failures = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();

    SelfTest(ResourceCasinoPlugin plugin) {
        this.plugin = plugin;
    }

    void start() {
        Bukkit.getScheduler().runTaskLater(plugin, this::runChecks, 60L);
    }

    private void runChecks() {
        plugin.getLogger().info("Starting self-test");
        step("build the casino floor", this::checkBuild);
        step("wallet and bets", this::checkMoney);
        step("data.yml round trip", this::checkPersistence);
        step("house edge holds over many rounds", this::checkHouseEdge);
        step("reload", () -> {
            for (int i = 0; i < 3; i++) plugin.reload();
            expect(plugin.wheel().expectedReturn() < 1, "wheel after reload pays too much");
        });
        step("big wheel spins to the result", this::checkBigWheel);
        step("duck race on the track", this::checkRace);
        Bukkit.getScheduler().runTaskLater(plugin, this::finish, 220L);
    }

    private void checkBuild() {
        expect(plugin.casino().build(), "build() returned false");
        World world = plugin.casino().world();
        expect(world != null, "casino world missing");
        if (world == null) return;
        expect(plugin.data().built(), "built flag not set");
        int y = CasinoWorld.FLOOR;
        expectBlock(world.getBlockAt(14, y + 1, 18), Material.SMOOTH_QUARTZ, "cashier counter");
        expectBlock(world.getBlockAt(-11, y + 1, 0), Material.ENCHANTING_TABLE, "roulette wheel");
        expectBlock(world.getBlockAt(0, y, DuckTrack.LANE_Z[2]), Material.WATER, "duck lane");
        expectBlock(world.getBlockAt(-21, y + 2, 22), Material.DROPPER, "slot machine");
        expectBlock(world.getBlockAt(0, CasinoWorld.CEILING, 1), Material.BLACK_STAINED_GLASS, "ceiling");
        expectBlock(world.getBlockAt(CasinoWorld.HALF, y + 3, 1), Material.DARK_OAK_PLANKS, "wall");
        for (CasinoWorld.Station station : CasinoWorld.Station.values()) {
            boolean found = false;
            for (int x = -CasinoWorld.HALF; x <= CasinoWorld.HALF && !found; x++) {
                for (int z = -CasinoWorld.HALF; z <= CasinoWorld.HALF && !found; z++) {
                    for (int by = y; by <= y + 3 && !found; by++) {
                        found = plugin.casino().stationAt(world.getBlockAt(x, by, z)) == station;
                    }
                }
            }
            expect(found, "no clickable block for " + station);
            expect(plugin.casino().stationSpot(station) != null, "no spot for " + station);
        }
        expect(plugin.casino().stationAt(world.getBlockAt(0, y, 0)) == null, "plain floor shouldn't be a station");
        expect(plugin.casino().bigWheel().size() == plugin.wheel().size(), "big wheel has " + plugin.casino().bigWheel().size() + " segments");
        long cashiers = world.getEntitiesByClass(Villager.class).stream()
                .filter(v -> CasinoWorld.ROLE_CASHIER.equals(plugin.casino().role(v))).count();
        expect(cashiers == 1, "expected 1 cashier, found " + cashiers);
        expect(Files.exists(plugin.dataFile().toPath()), "data.yml not written after build");
        // Rebuilding must not pile up duplicates.
        expect(plugin.casino().build(), "second build() returned false");
        long after = world.getEntitiesByClass(Villager.class).stream()
                .filter(v -> CasinoWorld.ROLE_CASHIER.equals(plugin.casino().role(v))).count();
        expect(after == 1, "rebuild left " + after + " cashiers");
        notes.add("stations: " + plugin.casino().stationCount() + " clickable blocks");
    }

    private void checkMoney() {
        UUID id = UUID.randomUUID();
        Wallet wallet = plugin.data().wallet();
        wallet.deposit(id, Resource.IRON, 100);
        expect(plugin.betProblem(id, Resource.IRON, 0) != null, "zero bet allowed");
        expect(plugin.betProblem(id, Resource.IRON, 101) != null, "bet above balance allowed");
        expect(plugin.betProblem(id, Resource.IRON, plugin.maxBet(Resource.IRON) + 1) != null, "bet above limit allowed");
        expect(plugin.betProblem(id, Resource.IRON, 10) == null, "valid bet refused");
        expect(plugin.betProblem(id, Resource.DIAMOND, 1) != null, "bet with no diamonds allowed");

        int before = plugin.pendingCount();
        ResourceCasinoPlugin.Pending win = plugin.book(id, "Tester", "Test", Resource.IRON, 10, 25);
        expect(win != null, "book() failed");
        expect(wallet.balance(id, Resource.IRON) == 90, "stake not taken: " + wallet.balance(id, Resource.IRON));
        plugin.settle(win);
        plugin.settle(win);
        expect(wallet.balance(id, Resource.IRON) == 115, "payout wrong (or paid twice): " + wallet.balance(id, Resource.IRON));

        ResourceCasinoPlugin.Pending loss = plugin.book(id, "Tester", "Test", Resource.IRON, 15, 0);
        plugin.settle(loss);
        expect(wallet.balance(id, Resource.IRON) == 100, "loss wrong: " + wallet.balance(id, Resource.IRON));
        expect(plugin.book(id, "Tester", "Test", Resource.IRON, 1000, 0) == null, "overdraw booked");
        expect(wallet.balance(id, Resource.IRON) == 100, "failed booking changed balance");

        ResourceCasinoPlugin.Pending open = plugin.book(id, "Tester", "Test", Resource.IRON, 5, 10);
        plugin.settleAll();
        expect(wallet.balance(id, Resource.IRON) == 105, "settleAll didn't pay out: " + wallet.balance(id, Resource.IRON));
        expect(plugin.pendingCount() == before, "pending bets left over");
        expect(open != null, "booking failed");
        expect(!wallet.withdraw(id, Resource.IRON, 106), "withdrew more than balance");
        expect(wallet.withdraw(id, Resource.IRON, 105) && wallet.balance(id, Resource.IRON) == 0, "full withdrawal failed");
    }

    private void checkPersistence() throws Exception {
        UUID id = UUID.randomUUID();
        plugin.data().wallet().deposit(id, Resource.NETHERITE, 7);
        plugin.data().wallet().deposit(id, Resource.EMERALD, 321);
        String saved = plugin.data().saveToString();
        CasinoData copy = new CasinoData();
        copy.loadFromString(saved);
        expect(copy.wallet().balance(id, Resource.NETHERITE) == 7, "netherite not saved");
        expect(copy.wallet().balance(id, Resource.EMERALD) == 321, "emeralds not saved");
        expect(copy.built() == plugin.data().built(), "built flag not saved");
        expect(copy.ledger().wagered(Resource.IRON) == plugin.data().ledger().wagered(Resource.IRON), "ledger not saved");
        plugin.data().wallet().withdraw(id, Resource.NETHERITE, 7);
        plugin.data().wallet().withdraw(id, Resource.EMERALD, 321);
    }

    private void checkHouseEdge() {
        SplittableRandom random = new SplittableRandom(7);
        int rounds = 300_000;

        double paid = 0;
        Roulette.Bet red = Roulette.Bet.of(Roulette.Kind.RED);
        Roulette.Bet seven = Roulette.Bet.straight(7);
        for (int i = 0; i < rounds; i++) {
            int pocket = Roulette.spin(random);
            paid += red.payout(1, pocket) + seven.payout(1, pocket);
        }
        report("roulette", paid / (2.0 * rounds), 36.0 / 37);

        paid = 0;
        for (int i = 0; i < rounds; i++) paid += Payouts.apply(3, Slots.multiplier(Slots.spin(random)));
        report("slots", paid / (3.0 * rounds), Slots.rtp());

        paid = 0;
        Wheel wheel = plugin.wheel();
        for (int i = 0; i < rounds; i++) paid += wheel.payout(wheel.spin(random), 2);
        report("wheel", paid / (2.0 * rounds), wheel.expectedReturn());

        paid = 0;
        for (int i = 0; i < rounds; i++) {
            DuckDerby.Field field = DuckDerby.newField(random, plugin.derbyEdge());
            int pick = random.nextInt(DuckDerby.DUCKS);
            if (DuckDerby.pickWinner(field, random) == pick) paid += Payouts.apply(10, field.multiplier()[pick]);
        }
        report("duck derby", paid / (10.0 * rounds), 1 - plugin.derbyEdge());
    }

    private void report(String game, double observed, double expected) {
        notes.add(String.format(java.util.Locale.ROOT, "%s: paid back %.2f%% over 300k rounds (target %.2f%%)",
                game, observed * 100, expected * 100));
        expect(observed < 1.0, game + " paid back more than it took: " + observed);
        expect(Math.abs(observed - expected) < 0.03, game + " return " + observed + " far from expected " + expected);
    }

    private void checkBigWheel() {
        BigWheel big = plugin.casino().bigWheel();
        int start = plugin.wheelTop();
        int target = 11;
        int steps = plugin.wheel().size() * 2 + Math.floorMod(target - start, plugin.wheel().size());
        expect(big.spin(start, steps, 40), "big wheel refused to spin");
        expect(!big.spin(start, steps, 40), "big wheel spun twice at once");
        Bukkit.getScheduler().runTaskLater(plugin, () -> step("big wheel stopped on segment " + target, () -> {
            expect(!big.spinning(), "still spinning");
            expect(big.topSegment() == target, "top segment is " + big.topSegment() + ", wanted " + target);
            double topY = big.segmentPosition(target)[0];
            for (int i = 0; i < big.size(); i++) {
                if (i != target) expect(big.segmentPosition(i)[0] < topY, "segment " + i + " is above the winner");
            }
        }), 55L);
    }

    private void checkRace() {
        DuckTrack track = plugin.casino().track();
        int winner = 3;
        DuckDerby.RacePlan plan = DuckDerby.plan(winner, 60, 20, new SplittableRandom(3));
        expect(track.run(plan), "track refused to run");
        expect(!track.run(plan), "two races on one track");
        expect(track.duckCount() == DuckDerby.DUCKS, "expected 5 ducks, got " + track.duckCount());
        Bukkit.getScheduler().runTaskLater(plugin, () -> step("race finished", () -> {
            expect(track.lastWinner() == winner, "duck " + track.lastWinner() + " crossed first, wanted " + winner);
            expect(track.finishedAt() == plan.raceFrames(), "winner crossed at frame " + track.finishedAt());
            expect(Math.abs(track.duckX(winner) - DuckTrack.FINISH_X) < 0.01, "winner isn't on the line");
        }), 70L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> step("ducks cleaned up", () -> {
            expect(!track.racing(), "race still running");
            expect(track.duckCount() == 0, track.duckCount() + " ducks left on the track");
        }), 175L);
    }

    private void expectBlock(Block block, Material material, String what) {
        expect(block.getType() == material, what + " at " + block.getX() + "," + block.getY() + "," + block.getZ()
                + " is " + block.getType() + ", expected " + material);
    }

    private interface Check {
        void run() throws Exception;
    }

    private void step(String name, Check check) {
        try {
            check.run();
            notes.add("ok: " + name);
        } catch (Exception | LinkageError e) {
            failures.add(name + " threw " + e);
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Self-test step failed: " + name, e);
        }
    }

    private void expect(boolean condition, String message) {
        if (!condition) failures.add(message);
    }

    private void finish() {
        List<String> lines = new ArrayList<>();
        lines.add(failures.isEmpty() ? "PASS" : "FAIL");
        failures.forEach(f -> lines.add("FAIL: " + f));
        lines.addAll(notes);
        try {
            Path out = plugin.getDataFolder().toPath().resolve("selftest-result.txt");
            Files.createDirectories(out.getParent());
            Files.write(out, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not write self-test result: " + e);
        }
        lines.forEach(plugin.getLogger()::info);
        Bukkit.shutdown();
    }
}
