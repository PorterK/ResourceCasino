package net.kalob.ResourceCasino;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The giant wheel on the casino's east wall: 24 glowing coloured panels (text
 * displays) arranged in a circle, with a gold pointer at the top. A spin from
 * the wheel screen turns it so the winning segment stops under the pointer.
 */
final class BigWheel {

    static final double CENTER_X = CasinoWorld.HALF - 1.55;
    static final double CENTER_Y = CasinoWorld.FLOOR + 7.5;
    static final double CENTER_Z = 0.5;
    static final double RADIUS = 4.0;

    private final ResourceCasinoPlugin plugin;
    private final List<TextDisplay> segments = new ArrayList<>();
    private TextDisplay pointer;
    private World world;
    private BukkitTask spin;
    private double position;

    BigWheel(ResourceCasinoPlugin plugin) {
        this.plugin = plugin;
    }

    boolean present() {
        return !segments.isEmpty() && segments.getFirst().isValid();
    }

    boolean spinning() {
        return spin != null && !spin.isCancelled();
    }

    /** Which segment is under the pointer right now. */
    int topSegment() {
        return Math.floorMod((int) Math.round(position), Math.max(1, segments.size()));
    }

    /** World position of one segment, for tests: (y, z). */
    double[] segmentPosition(int index) {
        TextDisplay display = segments.get(index);
        return new double[]{display.getLocation().getY(), display.getLocation().getZ()};
    }

    int size() { return segments.size(); }

    void spawn(World world) {
        this.world = world;
        forget();
        Wheel wheel = plugin.wheel();
        position = plugin.wheelTop();
        for (int i = 0; i < wheel.size(); i++) {
            double m = wheel.multiplier(i);
            int rgb = Wheel.rgbFor(m);
            String textColor = m <= 0 ? "#FFFFFF" : (rgb == 0xEEEEEE || rgb == 0xFFD83D ? "#202020" : "#FFFFFF");
            TextDisplay display = world.spawn(locationFor(i, position), TextDisplay.class, d -> {
                d.text(Items.mm("<" + textColor + "><bold>" + Wheel.label(m)));
                d.setBillboard(Display.Billboard.CENTER);
                d.setBackgroundColor(Color.fromARGB(235, (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF));
                d.setBrightness(new Display.Brightness(15, 15));
                d.setLineWidth(60);
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                        new Vector3f(2.4f, 2.4f, 2.4f), new Quaternionf()));
                d.setTeleportDuration(2);
                d.setPersistent(false);
                plugin.casino().tag(d, CasinoWorld.ROLE_WHEEL);
            });
            segments.add(display);
        }
        pointer = world.spawn(new Location(world, CENTER_X, CENTER_Y + RADIUS + 0.75, CENTER_Z), TextDisplay.class, d -> {
            d.text(Items.mm("<gold><bold>▼"));
            d.setBillboard(Display.Billboard.CENTER);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(2.6f, 2.6f, 2.6f), new Quaternionf()));
            d.setPersistent(false);
            plugin.casino().tag(d, CasinoWorld.ROLE_WHEEL);
        });
    }

    /** Drops references (the entities themselves are removed by the casino). */
    void forget() {
        stop();
        segments.clear();
        pointer = null;
    }

    void stop() {
        if (spin != null) {
            spin.cancel();
            spin = null;
        }
    }

    /**
     * Where segment {@code index} sits when segment {@code top} (fractional while spinning)
     * is under the pointer. Viewed from inside the hall, segments run clockwise.
     */
    private Location locationFor(int index, double top) {
        double angle = (index - top) * 2 * Math.PI / Math.max(1, plugin.wheel().size());
        double y = CENTER_Y + RADIUS * Math.cos(angle);
        double z = CENTER_Z - RADIUS * Math.sin(angle);
        return new Location(world, CENTER_X, y, z);
    }

    private void place(double top) {
        position = top;
        for (int i = 0; i < segments.size(); i++) {
            TextDisplay display = segments.get(i);
            if (display.isValid()) display.teleport(locationFor(i, top));
        }
    }

    /**
     * Spins from segment {@code start} forward {@code steps} segments over {@code ticks},
     * using the same easing as the wheel screen so both stop together.
     * Returns false if the wheel is already spinning for someone else (or isn't built).
     */
    boolean spin(int start, int steps, int ticks) {
        if (!present() || spinning()) return false;
        int[] tick = {0};
        int[] lastClick = {start};
        place(start);
        spin = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int t = ++tick[0];
            double top = start + steps * Wheel.ease(t / (double) ticks);
            place(top);
            int whole = (int) Math.floor(top);
            if (whole != lastClick[0]) {
                lastClick[0] = whole;
                world.playSound(new Location(world, CENTER_X, CENTER_Y + RADIUS, CENTER_Z),
                        Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.8f);
            }
            if (t >= ticks) {
                place(start + steps);
                Location at = new Location(world, CENTER_X - 0.5, CENTER_Y + RADIUS, CENTER_Z);
                world.spawnParticle(Particle.FIREWORK, at, 40, 0.4, 0.4, 0.4, 0.05);
                world.playSound(at, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);
                stop();
            }
        }, 1L, 1L);
        return true;
    }
}
