package net.kalob.ResourceCasino;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Firework;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * The race track on the casino floor. When someone starts a Duck Derby race and
 * the track is free, five "ducks" (chickens with no AI, floating on the water)
 * swim the same race the screen shows, frame for frame.
 */
final class DuckTrack {

    /** Lane centres (block z), lane 1 to 5. */
    static final int[] LANE_Z = {-23, -21, -19, -17, -15};
    static final int START_BLOCK_X = -20;
    static final int FINISH_BLOCK_X = 20;
    static final double START_X = START_BLOCK_X + 0.5;
    static final double FINISH_X = FINISH_BLOCK_X + 0.5;
    static final double WATER_Y = CasinoWorld.FLOOR + 0.72;

    private final ResourceCasinoPlugin plugin;
    private final List<Chicken> ducks = new ArrayList<>();
    private BukkitTask race;
    private int lastWinner = -1;
    private int finishedFrames = -1;

    DuckTrack(ResourceCasinoPlugin plugin) {
        this.plugin = plugin;
    }

    boolean racing() {
        return race != null && !race.isCancelled();
    }

    int lastWinner() { return lastWinner; }

    int duckCount() {
        int alive = 0;
        for (Chicken duck : ducks) if (duck.isValid()) alive++;
        return alive;
    }

    double duckX(int lane) {
        return ducks.get(lane).getLocation().getX();
    }

    /** First frame at which some duck reached the line (for tests), -1 if none yet. */
    int finishedAt() { return finishedFrames; }

    /** Runs a race on the track. Returns false if the track is busy or the casino isn't built. */
    boolean run(DuckDerby.RacePlan plan) {
        World world = plugin.casino().world();
        if (world == null || !plugin.data().built() || racing()) return false;
        clearDucks();
        lastWinner = -1;
        finishedFrames = -1;
        for (int lane = 0; lane < DuckDerby.DUCKS; lane++) {
            int laneIndex = lane;
            Chicken duck = world.spawn(at(world, lane, 0, 0), Chicken.class, c -> {
                c.setAI(false);
                c.setGravity(false);
                c.setInvulnerable(true);
                c.setSilent(true);
                c.setPersistent(false);
                c.setRemoveWhenFarAway(false);
                c.setCollidable(false);
                c.customName(Items.mm((laneIndex + 1) + " " + DerbyMenu.duckName(laneIndex)));
                c.setCustomNameVisible(true);
                plugin.casino().tag(c, CasinoWorld.ROLE_DUCK);
            });
            ducks.add(duck);
        }
        Location stand = new Location(world, 0.5, CasinoWorld.FLOOR + 2, LANE_Z[2] + 0.5);
        world.playSound(stand, Sound.BLOCK_NOTE_BLOCK_BELL, 2f, 1f);

        int[] frame = {0};
        race = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int t = ++frame[0];
            for (int lane = 0; lane < ducks.size(); lane++) {
                Chicken duck = ducks.get(lane);
                if (!duck.isValid()) continue;
                duck.teleport(at(world, lane, plan.at(lane, t), t));
                if (t % 4 == lane % 4 && plan.at(lane, t) < 1.0) {
                    world.spawnParticle(Particle.SPLASH, duck.getLocation().add(-0.4, 0.1, 0), 6, 0.15, 0.05, 0.15, 0);
                }
                if (finishedFrames < 0 && plan.at(lane, t) >= 1.0) {
                    finishedFrames = t;
                    lastWinner = lane;
                }
            }
            if (t == plan.raceFrames()) celebrate(world, plan.winner());
            if (t >= plan.frames() + 60) {
                clearDucks();
                stop();
            }
        }, 1L, 1L);
        return true;
    }

    private static Location at(World world, int lane, double progress, int tick) {
        double x = START_X + (FINISH_X - START_X) * Math.min(1.0, progress);
        double bob = Math.sin((tick + lane * 7) * 0.5) * 0.04;
        return new Location(world, x, WATER_Y + bob, LANE_Z[lane] + 0.5, -90f, 0f);
    }

    private void celebrate(World world, int winner) {
        Location line = new Location(world, FINISH_X, CasinoWorld.FLOOR + 1.5, LANE_Z[winner] + 0.5);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, line, 60, 0.4, 0.6, 0.4, 0.4);
        world.playSound(line, Sound.ENTITY_PLAYER_LEVELUP, 1.5f, 1.3f);
        int rgb = DuckDerby.COLORS[winner];
        Firework firework = world.spawn(line.clone().add(0, 2, 0), Firework.class, fw -> {
            FireworkMeta meta = fw.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(Color.fromRGB(rgb)).withFade(Color.WHITE).trail(true).build());
            meta.setPower(0);
            fw.setFireworkMeta(meta);
            fw.setPersistent(false);
            plugin.casino().tag(fw, CasinoWorld.ROLE_DUCK);
        });
        firework.detonate();
    }

    private void clearDucks() {
        for (Chicken duck : ducks) if (duck.isValid()) duck.remove();
        ducks.clear();
    }

    void stop() {
        if (race != null) {
            race.cancel();
            race = null;
        }
    }

    /** Stops any race and removes the ducks (used on shutdown). */
    void reset() {
        stop();
        clearDucks();
    }
}
