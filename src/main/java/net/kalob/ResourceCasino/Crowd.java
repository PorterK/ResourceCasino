package net.kalob.ResourceCasino;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Powerable;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The people on the casino floor. Staff stand at every game (right-click them to
 * play), patrons fill the grandstand, the slot machines, the bar and the rail
 * round the roulette wheel. They're all frozen villagers, but they turn their
 * heads to follow the action, cheer and grumble at results, pull slot levers on
 * their own, and a few wander over to the roulette wheel whenever it spins.
 */
final class Crowd {

    static final String GROUP_STAND = "stand", GROUP_ROULETTE = "roulette", GROUP_SLOTS = "slots", GROUP_BAR = "bar",
            GROUP_PLINKO = "plinko", GROUP_WHEEL = "wheel", GROUP_STAFF = "staff";

    /** Role prefix for staff who open a game when right-clicked: "staff:ROULETTE". */
    static final String STAFF = "staff:";
    /** Role prefix for people who just chat when right-clicked: "talk:bartender". */
    static final String TALK = "talk:";

    record Npc(Villager villager, String group, Location home) {}

    private static final Villager.Type[] TYPES = {
            Villager.Type.PLAINS, Villager.Type.DESERT, Villager.Type.JUNGLE, Villager.Type.SAVANNA,
            Villager.Type.SNOW, Villager.Type.SWAMP, Villager.Type.TAIGA};
    private static final Villager.Profession[] OUTFITS = {
            Villager.Profession.NONE, Villager.Profession.FARMER, Villager.Profession.FISHERMAN, Villager.Profession.SHEPHERD,
            Villager.Profession.LEATHERWORKER, Villager.Profession.NITWIT, Villager.Profession.LIBRARIAN,
            Villager.Profession.CARTOGRAPHER, Villager.Profession.MASON, Villager.Profession.BUTCHER};

    private static final Map<String, String[]> LINES = Map.of(
            "bartender", new String[]{
                    "What'll it be? Milk's on the house.",
                    "Lost your shirt at roulette? Happens to everyone.",
                    "Word is somebody hit the 10x on the big wheel last week.",
                    "Pro tip: the house always wins. Drink up."},
            "guard", new String[]{
                    "Nobody gets in the vault. Nobody.",
                    "That's the house's money in there. Some of it used to be yours.",
                    "Move along."},
            "bouncer", new String[]{
                    "Welcome in. Play nice.",
                    "Leaving already? The exit's right behind me."});

    private final ResourceCasinoPlugin plugin;
    private final List<Npc> npcs = new ArrayList<>();
    private final List<Villager> walkers = new ArrayList<>();
    private final Map<String, Long> busyUntil = new HashMap<>();
    private World world;
    private BukkitTask ambient;
    private long clock;

    Crowd(ResourceCasinoPlugin plugin) {
        this.plugin = plugin;
    }

    int size() {
        int alive = 0;
        for (Npc npc : npcs) if (npc.villager().isValid()) alive++;
        return alive;
    }

    int count(String group) {
        int n = 0;
        for (Npc npc : npcs) if (npc.group().equals(group) && npc.villager().isValid()) n++;
        return n;
    }

    int walkers() {
        int n = 0;
        for (Villager walker : walkers) if (walker.isValid()) n++;
        return n;
    }

    /** Head direction (yaw) of one member of a group, for tests. */
    float yawOf(String group) {
        for (Npc npc : npcs) if (npc.group().equals(group)) return npc.villager().getLocation().getYaw();
        return Float.NaN;
    }

    // ---- spawning ----------------------------------------------------------------------------

    void spawn(World world) {
        this.world = world;
        forget();
        int y = CasinoWorld.FLOOR + 1;
        int h = CasinoWorld.HALF;
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Staff: right-click to play.
        staff(25.5, y, h - 4.5, 180, "Cashier", Villager.Profession.CLERIC, Villager.Type.PLAINS, CasinoWorld.Station.CASHIER);
        staff(RouletteWheel.CENTER_X, y, RouletteWheel.CENTER_Z - 7.2, 0, "Croupier", Villager.Profession.LIBRARIAN,
                Villager.Type.TAIGA, CasinoWorld.Station.ROULETTE);
        staff(-15.5, y, RaceTrack.CENTER_Z + RaceTrack.TRACK_OUTER + 1.3, 180, "Race Caller", Villager.Profession.CARTOGRAPHER,
                Villager.Type.SAVANNA, CasinoWorld.Station.HORSES);
        staff(PlinkoBoard.PEG_X + 8.5, y, PlinkoBoard.CENTER_Z - 6.5, 90, "Plinko Host", Villager.Profession.TOOLSMITH,
                Villager.Type.JUNGLE, CasinoWorld.Station.PLINKO);
        staff(h - 5.5, y, 6.5, -90, "Wheel Host", Villager.Profession.FLETCHER, Villager.Type.DESERT, CasinoWorld.Station.WHEEL);
        staff(-11.5, y, h - 4.5, 90, "Slots Attendant", Villager.Profession.MASON, Villager.Type.SWAMP, CasinoWorld.Station.SLOTS);
        staff(0.5, y, 16.5, 180, "Host", Villager.Profession.CLERIC, Villager.Type.SNOW, CasinoWorld.Station.LOBBY);
        talker(2.5, y, h - 1.5, 180, "Bouncer", Villager.Profession.WEAPONSMITH, Villager.Type.SNOW, "bouncer");
        talker(Bar.BARTENDER_X, y, Bar.CENTER_Z + 0.5, 90, "Bartender", Villager.Profession.BUTCHER, Villager.Type.PLAINS, "bartender");
        talker(Vault.GATE_X0 - 1.5, y, h - 1.5, 180, "Vault Guard", Villager.Profession.ARMORER, Villager.Type.TAIGA, "guard");

        // The grandstand: three rows of spectators facing the track.
        int front = (int) Math.ceil(RaceTrack.CENTER_Z + RaceTrack.TRACK_OUTER + 1) + 1;
        for (int row = 0; row < 3; row++) {
            for (int x = -13 + row * 2; x <= 14; x += 4 + random.nextInt(2)) {
                patron(x + 0.5, y + row + 0.5, front + row + 0.5, 180, GROUP_STAND);
            }
        }
        // A rail of onlookers round the roulette wheel.
        for (double angle : new double[]{0.3, 1.1, 2.0, 2.9, 3.8, 5.2}) {
            double r = RouletteWheel.RIM_RADIUS + 2.1;
            double x = RouletteWheel.CENTER_X + Math.cos(angle) * r;
            double z = RouletteWheel.CENTER_Z + Math.sin(angle) * r;
            patron(x, y, z, yawTowards(x, z, RouletteWheel.CENTER_X, RouletteWheel.CENTER_Z), GROUP_ROULETTE);
        }
        // Gamblers at four of the slot machines.
        for (int x : new int[]{-30, -26, -22, -16}) patron(x + 0.5, y, h - 3.5, 0, GROUP_SLOTS);
        // Regulars at the bar.
        for (double z : new double[]{Bar.CENTER_Z - 3.5, Bar.CENTER_Z + 0.5, Bar.CENTER_Z + 3.5}) {
            patron(Bar.COUNTER_X - 0.45, y, z, -90, GROUP_BAR);
        }
        // A couple watching the Plinko board and the big wheel.
        patron(PlinkoBoard.PEG_X + 9.5, y, PlinkoBoard.CENTER_Z + 1.5, 90, GROUP_PLINKO);
        patron(PlinkoBoard.PEG_X + 10.5, y, PlinkoBoard.CENTER_Z - 2.5, 90, GROUP_PLINKO);
        patron(h - 9.5, y, -4.5, -90, GROUP_WHEEL);
        patron(h - 10.5, y, 3.5, -90, GROUP_WHEEL);

        if (ambient == null) ambient = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    void forget() {
        if (ambient != null) {
            ambient.cancel();
            ambient = null;
        }
        npcs.clear();
        for (Villager walker : walkers) if (walker.isValid()) walker.remove();
        walkers.clear();
        busyUntil.clear();
    }

    private Villager villager(double x, double y, double z, float yaw, Villager.Profession profession, Villager.Type type,
                              int level, String name, String role) {
        Location at = new Location(world, x, y, z, yaw, 0f);
        return world.spawn(at, Villager.class, v -> {
            v.setAI(false);
            v.setGravity(false);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setPersistent(false);
            v.setRemoveWhenFarAway(false);
            v.setCollidable(false);
            v.setVillagerType(type);
            v.setProfession(profession);
            if (profession != Villager.Profession.NONE && profession != Villager.Profession.NITWIT) v.setVillagerLevel(level);
            if (name != null) {
                v.customName(Items.mm("<gold><bold>" + name));
                v.setCustomNameVisible(true);
            }
            v.setRotation(yaw, 0f);
            plugin.casino().tag(v, role);
        });
    }

    private void staff(double x, double y, double z, float yaw, String name, Villager.Profession profession,
                       Villager.Type type, CasinoWorld.Station station) {
        Villager v = villager(x, y, z, yaw, profession, type, 5, name, STAFF + station.name());
        npcs.add(new Npc(v, GROUP_STAFF, v.getLocation()));
    }

    private void talker(double x, double y, double z, float yaw, String name, Villager.Profession profession,
                        Villager.Type type, String who) {
        Villager v = villager(x, y, z, yaw, profession, type, 5, name, TALK + who);
        npcs.add(new Npc(v, GROUP_STAFF, v.getLocation()));
    }

    private void patron(double x, double y, double z, float yaw, String group) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Villager v = villager(x, y, z, yaw, OUTFITS[random.nextInt(OUTFITS.length)], TYPES[random.nextInt(TYPES.length)],
                1 + random.nextInt(5), null, "patron");
        npcs.add(new Npc(v, group, v.getLocation()));
    }

    static float yawTowards(double fromX, double fromZ, double toX, double toZ) {
        return (float) Math.toDegrees(Math.atan2(-(toX - fromX), toZ - fromZ));
    }

    // ---- behaviour ---------------------------------------------------------------------------

    /** Every half second: idle glances, and the slot regulars pull their levers now and then. */
    private void tick() {
        clock += 10;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (Npc npc : npcs) {
            Villager v = npc.villager();
            if (!v.isValid() || busy(npc.group()) || random.nextInt(14) != 0) continue;
            float yaw = npc.home().getYaw() + (random.nextFloat() - 0.5f) * 70f;
            v.setRotation(yaw, (random.nextFloat() - 0.5f) * 20f);
        }
        if (random.nextInt(6) == 0) pullLever(random);
    }

    private boolean busy(String group) {
        return busyUntil.getOrDefault(group, 0L) > clock;
    }

    private void pullLever(ThreadLocalRandom random) {
        List<Npc> gamblers = new ArrayList<>();
        for (Npc npc : npcs) if (npc.group().equals(GROUP_SLOTS) && npc.villager().isValid()) gamblers.add(npc);
        if (gamblers.isEmpty()) return;
        Npc gambler = gamblers.get(random.nextInt(gamblers.size()));
        Location at = gambler.home();
        Block lever = world.getBlockAt(at.getBlockX() + 1, CasinoWorld.FLOOR + 2, CasinoWorld.HALF - 3);
        if (!(lever.getBlockData() instanceof Powerable)) return;
        setPowered(lever, true);
        world.playSound(lever.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.5f, 0.8f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            setPowered(lever, false);
            world.playSound(lever.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f, 1.6f);
        }, 12L);
        boolean lucky = random.nextInt(5) == 0;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!gambler.villager().isValid()) return;
            Location head = gambler.villager().getLocation().add(0, 2.1, 0);
            if (lucky) {
                world.spawnParticle(Particle.HAPPY_VILLAGER, head, 8, 0.3, 0.2, 0.3, 0);
                world.playSound(head, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
            } else if (random.nextBoolean()) {
                world.spawnParticle(Particle.ANGRY_VILLAGER, head, 1, 0.1, 0.1, 0.1, 0);
            }
        }, 32L);
    }

    private static void setPowered(Block block, boolean powered) {
        BlockData data = block.getBlockData();
        if (data instanceof Powerable powerable) {
            powerable.setPowered(powered);
            block.setBlockData(powerable, false);
        }
    }

    /** Turns everyone in a group (and nearby staff) to look at {@code target}. */
    void watch(String group, Location target) {
        if (world == null || target == null) return;
        busyUntil.put(group, clock + 20);
        for (Npc npc : npcs) {
            if (!npc.group().equals(group)) continue;
            look(npc.villager(), target);
        }
        for (Villager walker : walkers) if (walker.isValid() && GROUP_ROULETTE.equals(group)) look(walker, target);
    }

    private static void look(Villager v, Location target) {
        if (!v.isValid()) return;
        Location eye = v.getEyeLocation();
        double dx = target.getX() - eye.getX(), dy = target.getY() - eye.getY(), dz = target.getZ() - eye.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        v.setRotation(yaw, Math.max(-60, Math.min(60, pitch)));
    }

    /** Happy particles and a cheer over a group. */
    void cheer(String group) {
        react(group, true);
    }

    /** Angry particles and a groan over a group (the house won). */
    void grumble(String group) {
        react(group, false);
    }

    private void react(String group, boolean happy) {
        if (world == null) return;
        boolean sound = true;
        for (Npc npc : npcs) {
            if (!npc.group().equals(group) || !npc.villager().isValid()) continue;
            Location head = npc.villager().getLocation().add(0, 2.2, 0);
            world.spawnParticle(happy ? Particle.HAPPY_VILLAGER : Particle.ANGRY_VILLAGER, head, happy ? 6 : 1, 0.3, 0.2, 0.3, 0);
            if (happy) hop(npc.villager());
            if (sound) {
                world.playSound(head, happy ? Sound.ENTITY_VILLAGER_CELEBRATE : Sound.ENTITY_VILLAGER_NO, 1.2f, 1f);
                sound = false;
            }
        }
    }

    /** The whole floor cheers (a big win). */
    void cheerAll() {
        for (String group : List.of(GROUP_STAND, GROUP_ROULETTE, GROUP_SLOTS, GROUP_BAR, GROUP_PLINKO, GROUP_WHEEL, GROUP_STAFF)) {
            cheer(group);
        }
    }

    /** A quick little jump. */
    void hop(Villager v) {
        Location home = v.getLocation();
        v.teleport(home.clone().add(0, 0.35, 0));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (v.isValid()) v.teleport(home);
        }, 4L);
    }

    /** The croupier's nod when the ball drops. */
    void staffHop(CasinoWorld.Station station) {
        for (Npc npc : npcs) {
            if (npc.villager().isValid() && (STAFF + station.name()).equals(plugin.casino().role(npc.villager()))) {
                hop(npc.villager());
            }
        }
    }

    /**
     * A few people wander over to the roulette wheel while it spins, then drift off again.
     * Walk-in takes 30 ticks; they leave {@code stayTicks} after arriving.
     */
    void gather(int stayTicks) {
        if (world == null) return;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double cx = RouletteWheel.CENTER_X, cz = RouletteWheel.CENTER_Z;
        for (int i = 0; i < 3; i++) {
            double angle = Math.PI * (0.15 + 0.45 * i) + random.nextDouble() * 0.3;
            double r = RouletteWheel.RIM_RADIUS + 1.5;
            Location spot = new Location(world, cx + Math.cos(angle) * r, CasinoWorld.FLOOR + 1, cz + Math.sin(angle) * r);
            Location from = new Location(world, cx + Math.cos(angle) * (r + 10), CasinoWorld.FLOOR + 1, cz + Math.sin(angle) * (r + 10));
            float in = yawTowards(from.getX(), from.getZ(), spot.getX(), spot.getZ());
            Villager walker = villager(from.getX(), from.getY(), from.getZ(), in,
                    OUTFITS[random.nextInt(OUTFITS.length)], TYPES[random.nextInt(TYPES.length)], 1, null, "patron");
            walkers.add(walker);
            walk(walker, from, spot, in, 30, 0);
            float out = yawTowards(spot.getX(), spot.getZ(), from.getX(), from.getZ());
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (walker.isValid()) walk(walker, spot, from, out, 30, 0);
            }, 30L + stayTicks);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                walker.remove();
                walkers.remove(walker);
            }, 62L + stayTicks);
        }
    }

    private void walk(Villager v, Location from, Location to, float yaw, int ticks, int delay) {
        int[] t = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!v.isValid() || t[0] >= ticks) {
                task.cancel();
                return;
            }
            double f = ++t[0] / (double) ticks;
            Location at = from.clone().add(to.clone().subtract(from).toVector().multiply(f));
            at.setYaw(yaw);
            v.teleport(at);
        }, delay + 1L, 1L);
    }

    /** Someone right-clicked a chatty villager. */
    void talk(Player player, String who) {
        String[] lines = LINES.get(who);
        if (lines == null) return;
        String name = Character.toUpperCase(who.charAt(0)) + who.substring(1);
        player.sendMessage(Items.mm("<gold>" + (who.equals("guard") ? "Vault Guard" : name) + "</gold> <dark_gray>»</dark_gray> <white>"
                + lines[ThreadLocalRandom.current().nextInt(lines.length)]));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_AMBIENT, 0.8f, 1f);
    }

    /** The race caller's commentary, sent to everyone on the casino floor. */
    void announce(String line) {
        if (world == null) return;
        for (Player player : world.getPlayers()) {
            player.sendMessage(Items.mm("<gold>Race Caller</gold> <dark_gray>»</dark_gray> <white>" + line));
        }
    }
}
