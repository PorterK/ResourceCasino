package net.kalob.ResourceCasino;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Horse;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * The oval racetrack at the north end of the casino floor. A race sends five
 * saddled horses in their lane colours once round the track (anticlockwise,
 * starting and finishing on the line in front of the grandstand), following
 * the race the betting screen already decided, frame for frame.
 * <pre>
 *        ________________________
 *      /   ____________________   \      north straight (running west)
 *     |   /      infield       \   |
 *     |   \____________________/   |
 *      \__________|_____________/        south straight (running east), finish line at x = 0
 *              grandstand
 * </pre>
 */
final class RaceTrack {

    static final double CENTER_X = 0.5;
    static final double CENTER_Z = -24.5;
    /** Half the length of each straight. */
    static final double STRAIGHT = 16;
    /** Lane radii, lane 1 (inside) to lane 5 (outside). */
    static final double[] LANE_RADIUS = {6.5, 8.1, 9.7, 11.3, 12.9};
    static final double TRACK_INNER = 5.6;
    static final double TRACK_OUTER = 13.8;
    static final double HORSE_Y = CasinoWorld.FLOOR + 0.95;

    private static final Horse.Color[] COATS = {
            Horse.Color.CHESTNUT, Horse.Color.WHITE, Horse.Color.BLACK, Horse.Color.BROWN, Horse.Color.GRAY};

    private final ResourceCasinoPlugin plugin;
    private final List<Horse> horses = new ArrayList<>();
    private BukkitTask race;
    private int lastWinner = -1;
    private int finishedFrames = -1;

    RaceTrack(ResourceCasinoPlugin plugin) {
        this.plugin = plugin;
    }

    boolean racing() {
        return race != null && !race.isCancelled();
    }

    int lastWinner() { return lastWinner; }

    int finishedAt() { return finishedFrames; }

    int horseCount() {
        int alive = 0;
        for (Horse horse : horses) if (horse.isValid()) alive++;
        return alive;
    }

    Location horseLocation(int lane) {
        return horses.get(lane).getLocation();
    }

    /** Distance from a point to the track's centre line (the segment between the two bend centres). */
    static double distanceFromSpine(double x, double z) {
        double clampedX = Math.max(CENTER_X - STRAIGHT, Math.min(CENTER_X + STRAIGHT, x));
        return Math.hypot(x - clampedX, z - CENTER_Z);
    }

    /** Length of one lap in a lane. */
    static double lapLength(int lane) {
        return 4 * STRAIGHT + 2 * Math.PI * LANE_RADIUS[lane];
    }

    /**
     * Point and heading {x, z, yaw} a fraction {@code progress} of the way round a lap,
     * starting on the finish line and running anticlockwise (east along the south straight).
     */
    static double[] pointOnLap(int lane, double progress) {
        double r = LANE_RADIUS[lane];
        double s = Math.max(0, Math.min(1, progress)) * lapLength(lane);
        double half = STRAIGHT;
        double x, z, dx, dz;
        if (s < half) {                                   // south straight, east half
            x = CENTER_X + s; z = CENTER_Z + r; dx = 1; dz = 0;
        } else if ((s -= half) < Math.PI * r) {           // east bend, south to north
            double a = s / r;
            x = CENTER_X + STRAIGHT + r * Math.sin(a); z = CENTER_Z + r * Math.cos(a);
            dx = Math.cos(a); dz = -Math.sin(a);
        } else if ((s -= Math.PI * r) < 2 * half) {       // north straight, running west
            x = CENTER_X + STRAIGHT - s; z = CENTER_Z - r; dx = -1; dz = 0;
        } else if ((s -= 2 * half) < Math.PI * r) {       // west bend, north to south
            double a = s / r;
            x = CENTER_X - STRAIGHT - r * Math.sin(a); z = CENTER_Z - r * Math.cos(a);
            dx = -Math.cos(a); dz = Math.sin(a);
        } else {                                          // south straight, west half, home to the line
            s -= Math.PI * r;
            x = CENTER_X - STRAIGHT + s; z = CENTER_Z + r; dx = 1; dz = 0;
        }
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        return new double[]{x, z, yaw};
    }

    private static Location at(World world, int lane, double progress) {
        double[] p = pointOnLap(lane, progress);
        return new Location(world, p[0], HORSE_Y, p[1], (float) p[2], 0f);
    }

    /** Runs a race on the track. Returns false if the track is busy or the casino isn't built. */
    boolean run(HorseRace.RacePlan plan) {
        World world = plugin.casino().world();
        if (world == null || !plugin.data().built() || racing()) return false;
        clearHorses();
        lastWinner = -1;
        finishedFrames = -1;
        for (int lane = 0; lane < HorseRace.HORSES; lane++) {
            int index = lane;
            ItemStack armor = new ItemStack(Material.LEATHER_HORSE_ARMOR);
            armor.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(Color.fromRGB(HorseRace.COLORS[index])));
            Horse horse = world.spawn(at(world, lane, 0), Horse.class, h -> {
                h.setAI(false);
                h.setGravity(false);
                h.setInvulnerable(true);
                h.setSilent(true);
                h.setPersistent(false);
                h.setRemoveWhenFarAway(false);
                h.setCollidable(false);
                h.setAdult();
                h.setTamed(true);
                h.setColor(COATS[index]);
                h.setStyle(Horse.Style.NONE);
                h.getInventory().setSaddle(new ItemStack(Material.SADDLE));
                h.getEquipment().setItem(EquipmentSlot.BODY, armor);
                h.customName(Items.mm((index + 1) + " " + HorseRaceMenu.horseName(index)));
                h.setCustomNameVisible(true);
                plugin.casino().tag(h, CasinoWorld.ROLE_HORSE);
            });
            horses.add(horse);
        }
        Location stands = new Location(world, CENTER_X, CasinoWorld.FLOOR + 2, CENTER_Z + TRACK_OUTER);
        world.playSound(stands, Sound.BLOCK_NOTE_BLOCK_BELL, 3f, 1f);
        world.playSound(stands, Sound.ENTITY_HORSE_ANGRY, 2f, 1f);

        int[] frame = {0};
        race = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int t = ++frame[0];
            for (int lane = 0; lane < horses.size(); lane++) {
                Horse horse = horses.get(lane);
                if (!horse.isValid()) continue;
                double progress = plan.at(lane, t);
                horse.teleport(at(world, lane, progress));
                if (progress < 1.0 && t % 3 == lane % 3) {
                    world.spawnParticle(Particle.BLOCK, horse.getLocation(), 4, 0.3, 0.05, 0.3, 0,
                            Material.DIRT_PATH.createBlockData());
                }
                if (finishedFrames < 0 && progress >= 1.0) {
                    finishedFrames = t;
                    lastWinner = lane;
                }
            }
            if (t % 8 == 0 && t < plan.raceFrames()) {
                Location lead = horses.get(plan.leader(t)).getLocation();
                world.playSound(lead, Sound.ENTITY_HORSE_GALLOP, 1.5f, 1f);
            }
            if (t % 5 == 0 && t <= plan.raceFrames() + 20) {
                plugin.casino().crowd().watch(Crowd.GROUP_STAND, horses.get(plan.leader(Math.min(t, plan.raceFrames()))).getLocation());
            }
            call(plan, t);
            if (t == plan.raceFrames()) celebrate(world, plan.winner());
            if (t >= plan.frames() + 80) {
                clearHorses();
                stop();
            }
        }, 1L, 1L);
        return true;
    }

    /** The race caller's commentary at the start, each quarter, and the finish. */
    private void call(HorseRace.RacePlan plan, int t) {
        int frames = plan.raceFrames();
        Crowd crowd = plugin.casino().crowd();
        String leader = HorseRaceMenu.horseName(plan.leader(Math.max(1, t)));
        if (t == 1) crowd.announce("And they're off!");
        else if (t == frames / 4) crowd.announce(leader + " <white>shows early speed down the back straight.");
        else if (t == frames / 2) crowd.announce("Into the far turn, it's " + leader + " <white>in front!");
        else if (t == frames * 3 / 4) crowd.announce(leader + " <white>leads them into the home stretch!");
        else if (t == frames) crowd.announce("And at the line, it's " + HorseRaceMenu.horseName(plan.winner()) + "<white>!");
    }

    private void celebrate(World world, int winner) {
        plugin.casino().crowd().cheer(Crowd.GROUP_STAND);
        plugin.casino().crowd().staffHop(CasinoWorld.Station.HORSES);
        double[] p = pointOnLap(winner, 1.0);
        Location line = new Location(world, p[0], CasinoWorld.FLOOR + 2, p[1]);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, line, 80, 0.6, 0.8, 0.6, 0.4);
        world.playSound(line, Sound.ENTITY_PLAYER_LEVELUP, 2f, 1.2f);
        world.playSound(line, Sound.ENTITY_HORSE_AMBIENT, 2f, 1.1f);
        Firework firework = world.spawn(line.clone().add(0, 3, 0), Firework.class, fw -> {
            FireworkMeta meta = fw.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(Color.fromRGB(HorseRace.COLORS[winner])).withFade(Color.WHITE).trail(true).build());
            meta.setPower(0);
            fw.setFireworkMeta(meta);
            fw.setPersistent(false);
            plugin.casino().tag(fw, CasinoWorld.ROLE_HORSE);
        });
        firework.detonate();
    }

    private void clearHorses() {
        for (Horse horse : horses) if (horse.isValid()) horse.remove();
        horses.clear();
    }

    void stop() {
        if (race != null) {
            race.cancel();
            race = null;
        }
    }

    void reset() {
        stop();
        clearHorses();
    }
}
