package net.kalob.ResourceCasino;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The Plinko board on the casino's west wall: 8 rows of end-rod pegs fanning
 * out over 9 bins. Each drop sends a ball bouncing peg to peg along the path
 * the game already rolled. Several balls can be in play at once.
 */
final class PlinkoBoard {

    /** Block x of the pegs (they stick out of the wall towards the hall). */
    static final int PEG_X = -CasinoWorld.HALF + 1;
    /** x of the ball, just in front of the peg tips. */
    static final double BALL_X = PEG_X + 1.3;
    static final int CENTER_Z = 20;
    /** Block y of the top peg; each row is 2 blocks lower. */
    static final int TOP_Y = CasinoWorld.FLOOR + 19;
    static final int ROW_GAP = 2;
    static final int BIN_Y = CasinoWorld.FLOOR + 1;

    static final int START_TICKS = 8;
    static final int HOP_TICKS = 5;
    static final int FALL_TICKS = 9;
    static final int HOLD_TICKS = 30;
    static final int MAX_BALLS = 8;

    private final ResourceCasinoPlugin plugin;
    private int active;
    private int lastBin = -1;

    PlinkoBoard(ResourceCasinoPlugin plugin) {
        this.plugin = plugin;
    }

    /** Ticks from release until the ball lands in its bin. */
    static int landingTicks() {
        return START_TICKS + Plinko.ROWS * HOP_TICKS - HOP_TICKS + FALL_TICKS;
    }

    static int pegY(int row) { return TOP_Y - row * ROW_GAP; }

    /** Block z of the peg (or bin) at half-peg offset {@code offset}. */
    static int columnZ(int offset) { return CENTER_Z + offset; }

    int activeBalls() { return active; }

    int lastBin() { return lastBin; }

    /** Releases a ball that follows {@code drop}. Returns false if the board is full or not built. */
    boolean drop(Plinko.Drop drop) {
        World world = plugin.casino().world();
        if (world == null || !plugin.data().built() || active >= MAX_BALLS) return false;
        active++;
        Location start = point(world, 0, pegY(0) + 3.5);
        ItemDisplay ball = world.spawn(start, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.HEART_OF_THE_SEA));
            d.setBillboard(Display.Billboard.CENTER);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(0.55f, 0.55f, 0.55f), new Quaternionf()));
            d.setTeleportDuration(1);
            d.setPersistent(false);
            plugin.casino().tag(d, CasinoWorld.ROLE_PLINKO);
        });
        world.playSound(start, Sound.BLOCK_DISPENSER_LAUNCH, 0.7f, 1.4f);
        int land = landingTicks();
        int[] tick = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            int t = ++tick[0];
            if (!ball.isValid()) {
                active--;
                task.cancel();
                return;
            }
            if (t <= land) ball.teleport(position(world, drop, t));
            if (t > START_TICKS && t < land && (t - START_TICKS) % HOP_TICKS == 0) {
                world.playSound(ball.getLocation(), Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.6f, 1.2f + (t % 7) * 0.08f);
            }
            if (t == land) {
                lastBin = drop.bin();
                Location at = ball.getLocation();
                double multiplier = drop.multiplier();
                world.playSound(at, multiplier >= 3 ? Sound.ENTITY_PLAYER_LEVELUP : Sound.BLOCK_NOTE_BLOCK_PLING, 0.9f,
                        multiplier >= 1 ? 1.4f : 0.7f);
                world.spawnParticle(multiplier >= 3 ? Particle.TOTEM_OF_UNDYING : Particle.CRIT, at, multiplier >= 3 ? 40 : 10,
                        0.2, 0.3, 0.2, 0.2);
            }
            if (t >= land + HOLD_TICKS) {
                ball.remove();
                active--;
                task.cancel();
            }
        }, 1L, 1L);
        return true;
    }

    private static Location point(World world, double offset, double y) {
        return new Location(world, BALL_X, y, columnZ(0) + 0.5 + offset);
    }

    /** Where the ball is {@code t} ticks after release. */
    static Location position(World world, Plinko.Drop drop, int t) {
        double restAbove = 1.05;
        if (t <= START_TICKS) {
            double f = t / (double) START_TICKS;
            return point(world, 0, pegY(0) + 3.5 - (3.5 - restAbove) * f * f);
        }
        int hop = (t - START_TICKS) / HOP_TICKS;
        double f = ((t - START_TICKS) % HOP_TICKS) / (double) HOP_TICKS;
        if (hop < Plinko.ROWS - 1) {
            double from = drop.offsetAfter(hop);
            double to = drop.offsetAfter(hop + 1);
            double y0 = pegY(hop) + restAbove;
            double y1 = pegY(hop + 1) + restAbove;
            double y = y0 + (y1 - y0) * f * f + 0.6 * 4 * f * (1 - f);
            return point(world, from + (to - from) * f, y);
        }
        // Off the last row of pegs and down into the bin.
        int fallTick = t - START_TICKS - (Plinko.ROWS - 1) * HOP_TICKS;
        double g = Math.min(1.0, fallTick / (double) FALL_TICKS);
        double from = drop.offsetAfter(Plinko.ROWS - 1);
        double to = drop.offsetAfter(Plinko.ROWS);
        double y0 = pegY(Plinko.ROWS - 1) + restAbove;
        double y1 = BIN_Y + 0.35;
        double y = y0 + (y1 - y0) * g * g + 0.5 * 4 * g * (1 - g);
        return point(world, from + (to - from) * Math.min(1.0, g * 1.6), y);
    }

    /** Where the ball rests once it has landed (for tests). */
    static double binZ(int bin) {
        return columnZ(bin * 2 - Plinko.ROWS) + 0.5;
    }
}
