package net.kalob.ResourceCasino;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * A real roulette wheel set into the casino floor. 37 pockets (thin red, black
 * and green blocks) sit in a ring around a gold hub, with their numbers floating
 * just inside. On a spin the wheel turns one way while the ball slides round the
 * rim the other way, slows, rattles down off the rim and drops into the pocket
 * the game already chose, then rides round with the wheel as it coasts to a stop.
 */
final class RouletteWheel {

    static final double CENTER_X = -19.5;
    static final double CENTER_Z = 0.5;
    static final double SURFACE_Y = CasinoWorld.FLOOR + 1.0;
    static final double POCKET_RADIUS = 4.0;
    static final double LABEL_RADIUS = 3.15;
    static final double RIM_RADIUS = 5.05;
    static final double STEP = 2 * Math.PI / Roulette.POCKETS;

    private final ResourceCasinoPlugin plugin;
    private final List<BlockDisplay> pockets = new ArrayList<>();
    private final List<TextDisplay> labels = new ArrayList<>();
    private BlockDisplay hub;
    private ItemDisplay ball;
    private World world;
    private BukkitTask task;
    private double wheelAngle;
    private double ballAngle;
    private double ballRadius = RIM_RADIUS;
    private double ballLift;
    private int lastPocket = -1;

    RouletteWheel(ResourceCasinoPlugin plugin) {
        this.plugin = plugin;
    }

    boolean present() {
        return !pockets.isEmpty() && pockets.getFirst().isValid();
    }

    boolean spinning() {
        return task != null && !task.isCancelled();
    }

    /** Pocket number the ball is sitting in (after a spin), or -1. */
    int ballPocket() {
        return lastPocket;
    }

    /** Pocket under the ball right now, worked out from the angles (for tests). */
    int pocketUnderBall() {
        long slot = Math.round((ballAngle - wheelAngle) / STEP);
        return Roulette.WHEEL_ORDER[(int) Math.floorMod(slot, (long) Roulette.POCKETS)];
    }

    double ballRadius() { return ballRadius; }

    void spawn(World world) {
        this.world = world;
        forget();
        Location center = new Location(world, CENTER_X, SURFACE_Y, CENTER_Z);
        for (int i = 0; i < Roulette.POCKETS; i++) {
            int number = Roulette.WHEEL_ORDER[i];
            Material material = number == 0 ? Material.LIME_CONCRETE
                    : Roulette.isRed(number) ? Material.RED_CONCRETE : Material.BLACK_CONCRETE;
            pockets.add(world.spawn(center, BlockDisplay.class, d -> {
                d.setBlock(material.createBlockData());
                d.setBrightness(new Display.Brightness(15, 15));
                d.setPersistent(false);
                plugin.casino().tag(d, CasinoWorld.ROLE_ROULETTE);
            }));
            String color = number == 0 ? "<green>" : Roulette.isRed(number) ? "<red>" : "<white>";
            labels.add(world.spawn(center, TextDisplay.class, d -> {
                d.text(Items.mm(color + "<bold>" + number));
                d.setBillboard(Display.Billboard.CENTER);
                d.setBackgroundColor(org.bukkit.Color.fromARGB(150, 0, 0, 0));
                d.setBrightness(new Display.Brightness(15, 15));
                d.setTransformation(scale(0.7f));
                d.setTeleportDuration(1);
                d.setPersistent(false);
                plugin.casino().tag(d, CasinoWorld.ROLE_ROULETTE);
            }));
        }
        hub = world.spawn(center, BlockDisplay.class, d -> {
            d.setBlock(Material.GOLD_BLOCK.createBlockData());
            d.setBrightness(new Display.Brightness(15, 15));
            d.setPersistent(false);
            plugin.casino().tag(d, CasinoWorld.ROLE_ROULETTE);
        });
        ball = world.spawn(center, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.SNOWBALL));
            d.setBillboard(Display.Billboard.CENTER);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(scale(0.45f));
            d.setTeleportDuration(1);
            d.setPersistent(false);
            plugin.casino().tag(d, CasinoWorld.ROLE_ROULETTE);
        });
        ballAngle = wheelAngle + Math.PI / 2;
        ballRadius = RIM_RADIUS;
        apply(0);
    }

    void forget() {
        stop();
        pockets.clear();
        labels.clear();
        hub = null;
        ball = null;
    }

    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private static Transformation scale(float s) {
        return new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(s, s, s), new Quaternionf());
    }

    /** A flat block of size (radial, height, tangential) centred at {@code radius} along {@code angle}. */
    private static Transformation ringPiece(double angle, double radius, float radial, float height, float tangential) {
        Quaternionf rotation = new Quaternionf().rotateY((float) -angle);
        Vector3f half = rotation.transform(new Vector3f(radial / 2f, 0, tangential / 2f));
        Vector3f position = new Vector3f((float) (Math.cos(angle) * radius), 0.01f, (float) (Math.sin(angle) * radius));
        return new Transformation(position.sub(half), rotation, new Vector3f(radial, height, tangential), new Quaternionf());
    }

    private void apply(int interpolation) {
        for (int i = 0; i < pockets.size(); i++) {
            double angle = wheelAngle + i * STEP;
            BlockDisplay pocket = pockets.get(i);
            if (pocket.isValid()) {
                pocket.setInterpolationDelay(0);
                pocket.setInterpolationDuration(interpolation);
                pocket.setTransformation(ringPiece(angle, POCKET_RADIUS, 1.05f, 0.14f, (float) (STEP * POCKET_RADIUS * 0.92)));
            }
            TextDisplay label = labels.get(i);
            if (label.isValid()) {
                label.teleport(new Location(world, CENTER_X + Math.cos(angle) * LABEL_RADIUS, SURFACE_Y + 0.12,
                        CENTER_Z + Math.sin(angle) * LABEL_RADIUS));
            }
        }
        if (hub != null && hub.isValid()) {
            Quaternionf rotation = new Quaternionf().rotateY((float) -wheelAngle);
            Vector3f half = rotation.transform(new Vector3f(0.7f, 0, 0.7f));
            hub.setInterpolationDelay(0);
            hub.setInterpolationDuration(interpolation);
            hub.setTransformation(new Transformation(new Vector3f().sub(half), rotation, new Vector3f(1.4f, 0.7f, 1.4f), new Quaternionf()));
        }
        if (ball != null && ball.isValid()) {
            ball.teleport(new Location(world, CENTER_X + Math.cos(ballAngle) * ballRadius, SURFACE_Y + 0.32 + ballLift,
                    CENTER_Z + Math.sin(ballAngle) * ballRadius));
        }
    }

    /**
     * Spins so the ball ends in {@code pocket} at tick {@code ticks}. Returns false if the
     * wheel is already spinning for someone else (or isn't built).
     */
    boolean spin(int pocket, int ticks, boolean playerWon) {
        if (!present() || spinning() || ball == null) return false;
        int index = 0;
        for (int i = 0; i < Roulette.WHEEL_ORDER.length; i++) if (Roulette.WHEEL_ORDER[i] == pocket) index = i;
        double target = index * STEP;
        double wheel0 = wheelAngle;
        double relative0 = ballAngle - wheelAngle;
        // The ball laps the wheel backwards five-and-a-bit times relative to it, then settles in the pocket.
        double delta = -(2 * Math.PI * 5 + mod(relative0 - target));
        double omega = 0.07;
        int coast = 60;
        int total = ticks + coast;
        lastPocket = -1;
        int[] tick = {0};
        int[] lastClick = {0};
        Location sound = new Location(world, CENTER_X, SURFACE_Y + 0.5, CENTER_Z);
        world.playSound(sound, Sound.BLOCK_GRINDSTONE_USE, 0.6f, 1.6f);
        Crowd crowd = plugin.casino().crowd();
        crowd.gather(Math.max(20, ticks - 20));
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int t = ++tick[0];
            wheelAngle = wheel0 + omega * (t - t * (double) t / (2.0 * total));
            double x = Math.min(1.0, t / (double) ticks);
            double relative = relative0 + delta * Wheel.ease(x);
            ballAngle = wheelAngle + relative;
            ballLift = 0;
            if (x < 0.55) {
                ballRadius = RIM_RADIUS;
            } else if (x < 0.88) {
                double fall = (x - 0.55) / 0.33;
                ballRadius = RIM_RADIUS + (POCKET_RADIUS - RIM_RADIUS) * fall;
                ballLift = Math.abs(Math.sin(fall * Math.PI * 4)) * 0.35 * (1 - fall);
                int bounce = (int) (fall * 4);
                if (bounce != lastClick[0]) {
                    lastClick[0] = bounce;
                    world.playSound(sound, Sound.BLOCK_BAMBOO_HIT, 0.8f, 1.8f);
                }
            } else {
                ballRadius = POCKET_RADIUS;
            }
            if (t % 6 == 0 && x < 0.9) world.playSound(sound, Sound.BLOCK_NOTE_BLOCK_HAT, 0.25f, 0.8f + (float) x);
            if (t == ticks) {
                ballAngle = wheelAngle + target;
                lastPocket = pocket;
                crowd.staffHop(CasinoWorld.Station.ROULETTE);
                if (playerWon) crowd.cheer(Crowd.GROUP_ROULETTE);
                else crowd.grumble(Crowd.GROUP_ROULETTE);
                world.playSound(sound, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.4f);
                world.spawnParticle(Particle.FIREWORK, new Location(world, CENTER_X + Math.cos(ballAngle) * POCKET_RADIUS,
                        SURFACE_Y + 0.5, CENTER_Z + Math.sin(ballAngle) * POCKET_RADIUS), 20, 0.2, 0.2, 0.2, 0.03);
            }
            if (t > ticks) ballAngle = wheelAngle + target;
            apply(2);
            if (t % 3 == 0 && t < ticks + 20) crowd.watch(Crowd.GROUP_ROULETTE, ball.getLocation());
            if (t >= total) stop();
        }, 1L, 1L);
        return true;
    }

    private static double mod(double angle) {
        double m = angle % (2 * Math.PI);
        return m < 0 ? m + 2 * Math.PI : m;
    }
}
