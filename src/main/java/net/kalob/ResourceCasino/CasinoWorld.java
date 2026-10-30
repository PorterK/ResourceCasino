package net.kalob.ResourceCasino;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * The casino floor: a void world with one big hall built into it by code.
 * <pre>
 *            north (z -25)
 *   +-------------------------------+
 *   | ~~~~~~~ DUCK DERBY TRACK ~~~~ |   five water lanes, start west, finish east
 *   | ===== grandstand =====        |
 *   |                               |
 *   | ROULETTE     lobby    WHEEL ) |   the giant wheel hangs on the east wall
 *   |  table       kiosk    pedestals
 *   |                               |
 *   | SLOTS x5     spawn   CASHIER  |
 *   +-------------- exit -----------+
 *            south (z +25)
 * </pre>
 * Every block is placed from code, so {@code /casino build} always produces the same
 * hall. Clickable blocks ("stations") open the matching casino screen.
 */
final class CasinoWorld {

    static final int FLOOR = 64;
    static final int HALF = 25;
    static final int CEILING = FLOOR + 14;

    enum Station { LOBBY, CASHIER, ROULETTE, SLOTS, DERBY, WHEEL, EXIT }

    record Pos(int x, int y, int z) {}

    static final String ROLE_TITLE = "title", ROLE_CASHIER = "cashier", ROLE_WHEEL = "wheel", ROLE_DUCK = "duck";

    private final ResourceCasinoPlugin plugin;
    private final Map<Pos, Station> stations = new HashMap<>();
    private final Map<Station, Location> stationSpots = new EnumMap<>(Station.class);
    private final BigWheel bigWheel;
    private final DuckTrack track;
    private World world;
    private BukkitTask upkeep;

    // While laying out: whether to actually place blocks, and which station new blocks belong to.
    private boolean placing;
    private Station current;

    CasinoWorld(ResourceCasinoPlugin plugin) {
        this.plugin = plugin;
        this.bigWheel = new BigWheel(plugin);
        this.track = new DuckTrack(plugin);
    }

    World world() { return world; }

    BigWheel bigWheel() { return bigWheel; }

    DuckTrack track() { return track; }

    boolean isCasinoWorld(World other) {
        return world != null && other != null && other.getUID().equals(world.getUID());
    }

    int stationCount() { return stations.size(); }

    Location stationSpot(Station station) {
        Location spot = stationSpots.get(station);
        return spot == null ? null : spot.clone();
    }

    Station stationAt(Block block) {
        if (block == null || !isCasinoWorld(block.getWorld())) return null;
        return stations.get(new Pos(block.getX(), block.getY(), block.getZ()));
    }

    Location spawn() {
        return new Location(world, 0.5, FLOOR + 1, 21.5, 180f, 0f);
    }

    // ---- lifecycle -------------------------------------------------------------------------

    /** Loads (or creates) the casino world and puts the decorations back. */
    boolean load() {
        if (world == null) {
            world = Bukkit.getWorld(plugin.worldName());
            if (world == null) {
                world = new WorldCreator(plugin.worldName())
                        .generator(new VoidGenerator())
                        .environment(World.Environment.NORMAL)
                        .generateStructures(false)
                        .createWorld();
            }
            if (world == null) {
                plugin.getLogger().severe("Could not create the casino world '" + plugin.worldName() + "'.");
                return false;
            }
        }
        world.setSpawnLocation(0, FLOOR + 1, 21);
        if (plugin.keepLoaded()) {
            for (int cx = -2; cx <= 1; cx++) {
                for (int cz = -2; cz <= 1; cz++) world.setChunkForceLoaded(cx, cz, true);
            }
        }
        keepItDaytime();
        if (upkeep == null) upkeep = Bukkit.getScheduler().runTaskTimer(plugin, this::keepItDaytime, 200L, 200L);
        placing = false;
        layout();
        spawnDecorations();
        return true;
    }

    /** Builds (or rebuilds) the whole hall. */
    boolean build() {
        if (!load()) return false;
        long started = System.currentTimeMillis();
        removeDecorations();
        placing = true;
        clear();
        layout();
        placing = false;
        spawnDecorations();
        plugin.data().setBuilt(true);
        plugin.save(false);
        plugin.getLogger().info("Casino floor built in " + (System.currentTimeMillis() - started) + " ms ("
                + stations.size() + " clickable blocks).");
        return true;
    }

    void shutdown() {
        if (upkeep != null) {
            upkeep.cancel();
            upkeep = null;
        }
        bigWheel.stop();
        track.reset();
        removeDecorations();
    }

    /** Respawns the decorations if they've gone missing (e.g. the chunks were unloaded). */
    void ensureDecorations() {
        if (world != null && !bigWheel.present()) spawnDecorations();
    }

    void wheelChanged() {
        if (world != null && plugin.data().built()) {
            bigWheel.stop();
            spawnDecorations();
        }
    }

    private void keepItDaytime() {
        if (world == null) return;
        world.setTime(6000);
        world.setStorm(false);
        world.setThundering(false);
    }

    // ---- travel ----------------------------------------------------------------------------

    void enter(Player player) {
        if (!plugin.data().built()) {
            plugin.tell(player, "<gray>The casino floor hasn't been built yet. An admin can run <yellow>/casino build</yellow>.");
            return;
        }
        if (world == null && !load()) {
            plugin.tell(player, "<red>The casino floor couldn't be loaded. Check the server log.");
            return;
        }
        ensureDecorations();
        if (!isCasinoWorld(player.getWorld())) plugin.data().setReturn(player.getUniqueId(), player.getLocation());
        player.teleportAsync(spawn()).thenAccept(ok -> {
            if (ok) plugin.tell(player, "<gold>Welcome to the casino floor!</gold> <gray>Cashier on your left, slots on your right. <yellow>/casino leave</yellow> <gray>to go back.");
        });
    }

    void leave(Player player) {
        Location back = plugin.data().takeReturn(player.getUniqueId());
        if (back == null || back.getWorld() == null || isCasinoWorld(back.getWorld())) {
            back = Bukkit.getWorlds().getFirst().getSpawnLocation();
        }
        player.teleportAsync(back);
    }

    // ---- building --------------------------------------------------------------------------

    private void clear() {
        for (int x = -HALF; x <= HALF; x++) {
            for (int z = -HALF; z <= HALF; z++) {
                for (int y = FLOOR - 1; y <= CEILING + 1; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!block.getType().isAir()) block.setType(Material.AIR, false);
                }
            }
        }
    }

    private void station(Station station) {
        current = station;
    }

    private void put(int x, int y, int z, Material material) {
        if (placing) world.getBlockAt(x, y, z).setType(material, false);
        if (current != null) stations.put(new Pos(x, y, z), current);
    }

    private void put(int x, int y, int z, String blockData) {
        if (placing) {
            BlockData data = Bukkit.createBlockData(blockData);
            world.getBlockAt(x, y, z).setBlockData(data, false);
        }
        if (current != null) stations.put(new Pos(x, y, z), current);
    }

    private void spot(Station station, double x, double y, double z) {
        stationSpots.put(station, new Location(world, x, y, z));
    }

    /** The whole hall. With {@code placing == false} this only records where the stations are. */
    private void layout() {
        stations.clear();
        stationSpots.clear();
        int y = FLOOR;
        current = null;

        if (placing) {
            // Floor: red carpet with gold studs, dark stone border.
            for (int x = -HALF + 1; x < HALF; x++) {
                for (int z = -HALF + 1; z < HALF; z++) {
                    Material floor;
                    if (Math.abs(x) >= HALF - 3 || Math.abs(z) >= HALF - 3) floor = Material.POLISHED_BLACKSTONE_BRICKS;
                    else if (Math.floorMod(x + z, 6) == 0 && Math.floorMod(x - z, 6) == 0) floor = Material.GOLD_BLOCK;
                    else floor = Material.RED_CONCRETE;
                    put(x, y, z, floor);
                }
            }
            // Walls: dark oak panels between log pillars, sea lanterns, gold trim on top.
            for (int x = -HALF; x <= HALF; x++) {
                for (int z = -HALF; z <= HALF; z++) {
                    if (Math.abs(x) != HALF && Math.abs(z) != HALF) continue;
                    int along = Math.abs(x) == HALF ? z : x;
                    for (int wy = y; wy < CEILING; wy++) {
                        Material wall;
                        if (wy <= y + 1) wall = Material.POLISHED_BLACKSTONE_BRICKS;
                        else if (wy == CEILING - 1) wall = Material.GOLD_BLOCK;
                        else if (Math.floorMod(along, 6) == 0) wall = Material.STRIPPED_DARK_OAK_LOG;
                        else if (wy == y + 6 && Math.floorMod(along, 6) == 3) wall = Material.SEA_LANTERN;
                        else wall = Material.DARK_OAK_PLANKS;
                        put(x, wy, z, wall);
                    }
                }
            }
            // Ceiling: tinted glass with a grid of sea lanterns.
            for (int x = -HALF; x <= HALF; x++) {
                for (int z = -HALF; z <= HALF; z++) {
                    boolean lamp = Math.floorMod(x, 5) == 0 && Math.floorMod(z, 5) == 0 && Math.abs(x) < HALF && Math.abs(z) < HALF;
                    put(x, CEILING, z, lamp ? Material.SEA_LANTERN : Material.BLACK_STAINED_GLASS);
                }
            }
            // Invisible light so the floor is bright everywhere.
            for (int x = -HALF + 3; x < HALF; x += 6) {
                for (int z = -HALF + 3; z < HALF; z += 6) put(x, y + 4, z, "minecraft:light[level=15]");
            }
        }

        buildDerby(y);
        buildRoulette(y);
        buildWheel(y);
        buildSlots(y);
        buildCashier(y);

        // Lobby kiosk in the middle, exit by the spawn point.
        station(Station.LOBBY);
        put(0, y + 1, 6, Material.GOLD_BLOCK);
        put(0, y + 2, 6, Material.LODESTONE);
        spot(Station.LOBBY, 0.5, y + 1, 7.5);
        station(Station.EXIT);
        put(0, y + 1, 24, Material.CRYING_OBSIDIAN);
        put(0, y + 2, 24, Material.CRYING_OBSIDIAN);
        spot(Station.EXIT, 0.5, y + 1, 22.5);
        current = null;
    }

    private void buildDerby(int y) {
        current = null;
        int[] lanes = DuckTrack.LANE_Z;
        if (placing) {
            for (int x = -22; x <= 22; x++) {
                for (int z = lanes[0] - 1; z <= lanes[lanes.length - 1] + 1; z++) {
                    boolean lane = false;
                    for (int laneZ : lanes) lane |= z == laneZ;
                    boolean water = lane && x > -22 && x < 22;
                    Material m;
                    if (water) m = Material.WATER;
                    else if (x == DuckTrack.FINISH_BLOCK_X) m = Math.floorMod(z, 2) == 0 ? Material.BLACK_CONCRETE : Material.WHITE_CONCRETE;
                    else if (x == DuckTrack.START_BLOCK_X) m = Material.WHITE_CONCRETE;
                    else m = Material.QUARTZ_BLOCK;
                    put(x, y, z, m);
                }
            }
            // Checkered finish posts at both ends of the line.
            for (int z : new int[]{lanes[0] - 1, lanes[lanes.length - 1] + 1}) {
                for (int py = y + 1; py <= y + 3; py++) {
                    put(DuckTrack.FINISH_BLOCK_X, py, z, Math.floorMod(py + z, 2) == 0 ? Material.BLACK_CONCRETE : Material.WHITE_CONCRETE);
                }
            }
            // Grandstand, three rows rising to the south.
            int front = lanes[lanes.length - 1] + 3;
            for (int x = -17; x <= 17; x++) {
                for (int row = 0; row < 3; row++) {
                    int z = front + row;
                    for (int fill = 0; fill < row; fill++) put(x, y + 1 + fill, z, Material.DARK_OAK_PLANKS);
                    put(x, y + 1 + row, z, "minecraft:dark_oak_stairs[facing=south]");
                }
            }
        }
        // Betting windows at both ends of the grandstand.
        int z = lanes[lanes.length - 1] + 4;
        station(Station.DERBY);
        for (int x : new int[]{-20, 20}) {
            put(x, y + 1, z, Material.GOLD_BLOCK);
            put(x, y + 2, z, "minecraft:lectern[facing=north]");
        }
        spot(Station.DERBY, 0.5, y + 4, lanes[lanes.length - 1] + 5.5);
        current = null;
    }

    private void buildRoulette(int y) {
        station(Station.ROULETTE);
        for (int x = -15; x <= -7; x++) {
            for (int z = -3; z <= 3; z++) {
                boolean edge = x == -15 || x == -7 || z == -3 || z == 3;
                Material m;
                if (edge) m = Material.DARK_OAK_PLANKS;
                else if (x == -11 && z == 0) m = Material.ENCHANTING_TABLE;
                else if (z == 0) m = Math.floorMod(x, 2) == 0 ? Material.RED_WOOL : Material.BLACK_WOOL;
                else m = Material.GREEN_WOOL;
                put(x, y + 1, z, m);
            }
        }
        spot(Station.ROULETTE, -10.5, y + 1, 5.5);
        current = null;
    }

    private void buildWheel(int y) {
        current = null;
        if (placing) {
            // Round black backboard with a gold rim on the east wall.
            for (int wy = y + 1; wy < CEILING; wy++) {
                for (int z = -7; z <= 7; z++) {
                    double d = Math.hypot(wy + 0.5 - BigWheel.CENTER_Y, z + 0.5 - BigWheel.CENTER_Z);
                    if (d <= BigWheel.RADIUS + 1.0) put(HALF - 1, wy, z, Material.BLACK_CONCRETE);
                    else if (d <= BigWheel.RADIUS + 1.8) put(HALF - 1, wy, z, Material.GOLD_BLOCK);
                }
            }
        }
        station(Station.WHEEL);
        for (int z : new int[]{-3, 0, 3}) {
            put(18, y + 1, z, Material.CHISELED_QUARTZ_BLOCK);
            put(18, y + 2, z, Material.GOLD_BLOCK);
        }
        spot(Station.WHEEL, 16.5, y + 1, 0.5);
        current = null;
    }

    private void buildSlots(int y) {
        station(Station.SLOTS);
        for (int x = -21; x <= -13; x += 2) {
            put(x, y + 1, 22, Material.GOLD_BLOCK);
            put(x, y + 2, 22, "minecraft:dropper[facing=north]");
            put(x, y + 3, 22, Material.SEA_LANTERN);
            put(x + 1, y + 2, 22, "minecraft:lever[face=wall,facing=east]");
        }
        spot(Station.SLOTS, -16.5, y + 1, 20.5);
        current = null;
    }

    private void buildCashier(int y) {
        station(Station.CASHIER);
        for (int x = 14; x <= 21; x++) put(x, y + 1, 18, Material.SMOOTH_QUARTZ);
        for (int z = 19; z <= 21; z++) {
            put(14, y + 1, z, Material.SMOOTH_QUARTZ);
            put(21, y + 1, z, Material.SMOOTH_QUARTZ);
        }
        put(14, y + 2, 18, Material.GOLD_BLOCK);
        put(21, y + 2, 18, Material.GOLD_BLOCK);
        spot(Station.CASHIER, 17.5, y + 1, 16.5);
        current = null;
    }

    // ---- floating signs, the cashier, the big wheel -----------------------------------------

    void tag(Entity entity, String role) {
        entity.getPersistentDataContainer().set(plugin.entityKey(), PersistentDataType.STRING, role);
    }

    String role(Entity entity) {
        return entity.getPersistentDataContainer().get(plugin.entityKey(), PersistentDataType.STRING);
    }

    boolean isCasinoEntity(Entity entity) {
        return entity.getPersistentDataContainer().has(plugin.entityKey(), PersistentDataType.STRING);
    }

    void removeDecorations() {
        if (world == null) return;
        bigWheel.forget();
        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof Player) && isCasinoEntity(entity)) entity.remove();
        }
    }

    private void spawnDecorations() {
        if (world == null) return;
        removeDecorations();
        int y = FLOOR;
        title(0.5, y + 6.5, 6.5, "<gold><bold>RESOURCE CASINO", 3.0f);
        title(0.5, y + 5.3, 6.5, "<gray>Iron • Copper • Emerald • Diamond • Netherite", 1.2f);
        title(0.5, y + 3.4, 6.5, "<yellow>Click the lodestone for the casino menu", 0.8f);
        title(-10.5, y + 4.2, 0.5, "<red><bold>ROULETTE", 2.2f);
        title(-10.5, y + 3.4, 0.5, "<gray>Click the table", 0.8f);
        title(-16.5, y + 5.4, 22.5, "<yellow><bold>SLOTS", 2.2f);
        title(-16.5, y + 4.6, 22.5, "<gray>Click a machine", 0.8f);
        title(17.5, y + 4.4, 19.5, "<gold><bold>CASHIER", 2.2f);
        title(17.5, y + 3.6, 19.5, "<gray>Deposit & withdraw", 0.8f);
        title(18.5, y + 4.2, 0.5, "<light_purple><bold>SPIN THE WHEEL", 1.8f);
        title(18.5, y + 3.5, 0.5, "<gray>Click a pedestal", 0.8f);
        int[] lanes = DuckTrack.LANE_Z;
        double mid = (lanes[0] + lanes[lanes.length - 1]) / 2.0 + 0.5;
        title(0.5, y + 7.5, mid, "<aqua><bold>DUCK DERBY", 3.0f);
        title(0.5, y + 6.3, mid, "<gray>Bet at the lecterns by the grandstand", 1.0f);
        title(DuckTrack.FINISH_BLOCK_X + 0.5, y + 4.5, mid, "<white><bold>FINISH", 1.6f);
        title(DuckTrack.START_BLOCK_X + 0.5, y + 3.0, mid, "<white><bold>START", 1.2f);
        for (int lane = 0; lane < lanes.length; lane++) {
            title(-22.0, y + 1.6, lanes[lane] + 0.5, DerbyMenu.duckName(lane), 0.7f);
        }
        for (int x : new int[]{-20, 20}) title(x + 0.5, y + 3.6, lanes[lanes.length - 1] + 4.5, "<aqua>Duck Derby bets", 0.9f);
        title(0.5, y + 3.4, 24.5, "<green><bold>EXIT", 1.2f);

        Location cashierSpot = new Location(world, 17.5, y + 1, 20.5, 180f, 0f);
        world.spawn(cashierSpot, Villager.class, villager -> {
            villager.setAI(false);
            villager.setInvulnerable(true);
            villager.setSilent(true);
            villager.setPersistent(false);
            villager.setRemoveWhenFarAway(false);
            villager.setCollidable(false);
            villager.customName(Items.mm("<gold><bold>Cashier"));
            villager.setCustomNameVisible(true);
            tag(villager, ROLE_CASHIER);
        });

        bigWheel.spawn(world);
    }

    TextDisplay title(double x, double y, double z, String text, float scale) {
        return world.spawn(new Location(world, x, y, z), TextDisplay.class, display -> {
            display.text(Items.mm(text));
            display.setBillboard(Display.Billboard.CENTER);
            display.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
            display.setShadowed(true);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(scale, scale, scale), new Quaternionf()));
            display.setPersistent(false);
            tag(display, ROLE_TITLE);
        });
    }
}
