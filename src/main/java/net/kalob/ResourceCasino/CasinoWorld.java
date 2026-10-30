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
 *                    north (z -40)
 *   +---------------------------------------------+
 *   |      ____________________________           |   oval racetrack, five lanes,
 *   |     /       infield (trees)       \          |   finish line in front of the stands
 *   |      ------------|---------------           |
 *   |  $        ===== grandstand =====      $     |   $ = betting lecterns
 *   |     ( roulette )                       ) W  |   W = giant wheel on the east wall
 *   |     (  wheel   )      lobby kiosk      ) W  |
 *   | P   o o o                                   |   P = Plinko board on the west wall
 *   |                               [ cashier ]   |
 *   |   S S S S S S S S S     spawn               |   S = slot machines
 *   +-------------------- exit -------------------+
 *                    south (z +40)
 * </pre>
 * Every block is placed from code, so {@code /casino build} always produces the same
 * hall. Clickable blocks ("stations") open the matching casino screen.
 */
final class CasinoWorld {

    static final int FLOOR = 64;
    static final int HALF = 40;
    static final int CEILING = FLOOR + 24;

    enum Station { LOBBY, CASHIER, ROULETTE, SLOTS, HORSES, PLINKO, WHEEL, EXIT }

    record Pos(int x, int y, int z) {}

    static final String ROLE_TITLE = "title", ROLE_CASHIER = Crowd.STAFF + "CASHIER", ROLE_WHEEL = "wheel", ROLE_HORSE = "horse",
            ROLE_ROULETTE = "roulette", ROLE_PLINKO = "plinko";

    private final ResourceCasinoPlugin plugin;
    private final Map<Pos, Station> stations = new HashMap<>();
    private final Map<Station, Location> stationSpots = new EnumMap<>(Station.class);
    private final BigWheel bigWheel;
    private final RaceTrack track;
    private final RouletteWheel rouletteWheel;
    private final PlinkoBoard plinko;
    private final Crowd crowd;
    private final Vault vault;
    private World world;
    private BukkitTask upkeep;

    // While laying out: whether to actually place blocks, and which station new blocks belong to.
    private boolean placing;
    private Station current;

    CasinoWorld(ResourceCasinoPlugin plugin) {
        this.plugin = plugin;
        this.bigWheel = new BigWheel(plugin);
        this.track = new RaceTrack(plugin);
        this.rouletteWheel = new RouletteWheel(plugin);
        this.plinko = new PlinkoBoard(plugin);
        this.crowd = new Crowd(plugin);
        this.vault = new Vault(plugin);
    }

    World world() { return world; }

    BigWheel bigWheel() { return bigWheel; }

    RaceTrack track() { return track; }

    RouletteWheel rouletteWheel() { return rouletteWheel; }

    PlinkoBoard plinko() { return plinko; }

    Crowd crowd() { return crowd; }

    Vault vault() { return vault; }

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
        return new Location(world, 0.5, FLOOR + 1, HALF - 3.5, 180f, 0f);
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
        world.setSpawnLocation(0, FLOOR + 1, HALF - 4);
        if (plugin.keepLoaded()) {
            int chunks = (HALF >> 4) + 1;
            for (int cx = -chunks; cx < chunks; cx++) {
                for (int cz = -chunks; cz < chunks; cz++) world.setChunkForceLoaded(cx, cz, true);
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
        rouletteWheel.stop();
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
            if (ok) plugin.tell(player, "<gold>Welcome to the casino floor!</gold> <gray>Slots on your left, cashier on your right. <yellow>/casino leave</yellow> <gray>to go back.");
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
            for (int z = -HALF; z <= Vault.ROOM_Z1 + 1; z++) {
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
            // Walls: dark oak panels between log pillars, two rows of sea lanterns, gold trim on top.
            for (int x = -HALF; x <= HALF; x++) {
                for (int z = -HALF; z <= HALF; z++) {
                    if (Math.abs(x) != HALF && Math.abs(z) != HALF) continue;
                    int along = Math.abs(x) == HALF ? z : x;
                    for (int wy = y; wy < CEILING; wy++) {
                        Material wall;
                        if (wy <= y + 1) wall = Material.POLISHED_BLACKSTONE_BRICKS;
                        else if (wy == CEILING - 1) wall = Material.GOLD_BLOCK;
                        else if (Math.floorMod(along, 6) == 0) wall = Material.STRIPPED_DARK_OAK_LOG;
                        else if ((wy == y + 6 || wy == y + 14) && Math.floorMod(along, 6) == 3) wall = Material.SEA_LANTERN;
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
                for (int z = -HALF + 3; z < HALF; z += 6) {
                    put(x, y + 4, z, "minecraft:light[level=15]");
                    put(x, y + 13, z, "minecraft:light[level=15]");
                }
            }
        }

        buildRacetrack(y);
        buildRoulette(y);
        buildPlinko(y);
        buildWheel(y);
        buildSlots(y);
        buildCashier(y);
        buildVault(y);
        buildBar(y);

        // Lobby kiosk in the middle, exit by the spawn point.
        station(Station.LOBBY);
        put(0, y + 1, 12, Material.GOLD_BLOCK);
        put(0, y + 2, 12, Material.LODESTONE);
        spot(Station.LOBBY, 0.5, y + 1, 14.5);
        station(Station.EXIT);
        put(0, y + 1, HALF - 1, Material.CRYING_OBSIDIAN);
        put(0, y + 2, HALF - 1, Material.CRYING_OBSIDIAN);
        spot(Station.EXIT, 0.5, y + 1, HALF - 2.5);
        current = null;
    }

    private void buildRacetrack(int y) {
        current = null;
        int minX = (int) Math.floor(RaceTrack.CENTER_X - RaceTrack.STRAIGHT - RaceTrack.TRACK_OUTER - 1);
        int maxX = (int) Math.ceil(RaceTrack.CENTER_X + RaceTrack.STRAIGHT + RaceTrack.TRACK_OUTER + 1);
        int minZ = (int) Math.floor(RaceTrack.CENTER_Z - RaceTrack.TRACK_OUTER - 1);
        int maxZ = (int) Math.ceil(RaceTrack.CENTER_Z + RaceTrack.TRACK_OUTER + 1);
        int finishX = (int) Math.floor(RaceTrack.CENTER_X);
        if (placing) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    double d = RaceTrack.distanceFromSpine(x + 0.5, z + 0.5);
                    boolean southStraight = z + 0.5 > RaceTrack.CENTER_Z;
                    if (d < RaceTrack.TRACK_INNER - 0.6) {
                        put(x, y, z, Material.GRASS_BLOCK);
                    } else if (d < RaceTrack.TRACK_INNER || (d > RaceTrack.TRACK_OUTER && d <= RaceTrack.TRACK_OUTER + 0.6)) {
                        put(x, y, z, Material.SMOOTH_QUARTZ);
                        if (x != finishX || !southStraight) put(x, y + 1, z, "minecraft:smooth_quartz_slab[type=bottom]");
                    } else if (d <= RaceTrack.TRACK_OUTER) {
                        if (x == finishX && southStraight) {
                            put(x, y, z, Math.floorMod(z, 2) == 0 ? Material.BLACK_CONCRETE : Material.WHITE_CONCRETE);
                        } else {
                            put(x, y, z, Material.DIRT_PATH);
                        }
                    }
                }
            }
            // A few trees and flowers in the infield.
            for (int x : new int[]{-10, 0, 10}) {
                put(x, y + 1, (int) Math.floor(RaceTrack.CENTER_Z), Material.OAK_LOG);
                put(x, y + 2, (int) Math.floor(RaceTrack.CENTER_Z), Material.OAK_LOG);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        put(x + dx, y + 3, (int) Math.floor(RaceTrack.CENTER_Z) + dz, "minecraft:oak_leaves[persistent=true]");
                    }
                }
                put(x, y + 4, (int) Math.floor(RaceTrack.CENTER_Z), "minecraft:oak_leaves[persistent=true]");
            }
            for (int x = -14; x <= 14; x += 4) {
                put(x, y + 1, (int) Math.floor(RaceTrack.CENTER_Z) + 2, Material.POPPY);
                put(x + 2, y + 1, (int) Math.floor(RaceTrack.CENTER_Z) - 2, Material.DANDELION);
            }
            // Checkered finish posts on both sides of the line.
            for (double r : new double[]{RaceTrack.TRACK_INNER - 0.3, RaceTrack.TRACK_OUTER + 0.3}) {
                int z = (int) Math.floor(RaceTrack.CENTER_Z + r);
                for (int py = y + 1; py <= y + 4; py++) {
                    put(finishX, py, z, Math.floorMod(py, 2) == 0 ? Material.BLACK_CONCRETE : Material.WHITE_CONCRETE);
                }
            }
            // Grandstand, three rows rising to the south.
            int front = maxZ + 1;
            for (int x = -14; x <= 15; x++) {
                for (int row = 0; row < 3; row++) {
                    int z = front + row;
                    for (int fill = 0; fill < row; fill++) put(x, y + 1 + fill, z, Material.DARK_OAK_PLANKS);
                    put(x, y + 1 + row, z, "minecraft:dark_oak_stairs[facing=south]");
                }
            }
        }
        // Betting windows at both ends of the grandstand.
        station(Station.HORSES);
        int z = maxZ + 2;
        for (int x : new int[]{-17, 18}) {
            put(x, y + 1, z, Material.GOLD_BLOCK);
            put(x, y + 2, z, "minecraft:lectern[facing=north]");
        }
        spot(Station.HORSES, 0.5, y + 4, maxZ + 3.5);
        current = null;
    }

    private void buildRoulette(int y) {
        station(Station.ROULETTE);
        double cx = RouletteWheel.CENTER_X, cz = RouletteWheel.CENTER_Z;
        for (int x = (int) Math.floor(cx - 8); x <= (int) Math.ceil(cx + 8); x++) {
            for (int z = (int) Math.floor(cz - 8); z <= (int) Math.ceil(cz + 8); z++) {
                double d = Math.hypot(x + 0.5 - cx, z + 0.5 - cz);
                if (d <= RouletteWheel.RIM_RADIUS + 0.35) {
                    put(x, y, z, d <= RouletteWheel.LABEL_RADIUS - 0.6 ? Material.POLISHED_BLACKSTONE : Material.BLACK_CONCRETE);
                } else if (d <= RouletteWheel.RIM_RADIUS + 1.35) {
                    put(x, y, z, Material.POLISHED_BLACKSTONE);
                    put(x, y + 1, z, "minecraft:dark_oak_slab[type=bottom]");
                } else if (d <= RouletteWheel.RIM_RADIUS + 2.2 && placing) {
                    Station keep = current;
                    current = null;
                    put(x, y, z, Material.GOLD_BLOCK);
                    current = keep;
                }
            }
        }
        spot(Station.ROULETTE, cx, y + 1, cz + 8.5);
        current = null;
    }

    private void buildPlinko(int y) {
        current = null;
        int wall = -HALF;
        int peg = PlinkoBoard.PEG_X;
        int cz = PlinkoBoard.CENTER_Z;
        int half = Plinko.ROWS + 3;
        if (placing) {
            // Black backboard set into the wall, framed in gold.
            for (int z = cz - half; z <= cz + half; z++) {
                for (int wy = y + 1; wy < CEILING - 1; wy++) {
                    boolean frame = z == cz - half || z == cz + half || wy == CEILING - 2;
                    put(wall, wy, z, frame ? Material.GOLD_BLOCK : Material.BLACK_CONCRETE);
                }
            }
            // The release chute at the top.
            put(peg, PlinkoBoard.pegY(0) + 4, cz, "minecraft:dispenser[facing=down]");
            // Pegs: row r has r+1 pegs, two blocks apart, staggered.
            for (int row = 0; row < Plinko.ROWS; row++) {
                for (int offset = -row; offset <= row; offset += 2) {
                    put(peg, PlinkoBoard.pegY(row), PlinkoBoard.columnZ(offset), "minecraft:end_rod[facing=east]");
                }
            }
        }
        // Bins (coloured by payout) and the dividers between them are clickable too.
        station(Station.PLINKO);
        for (int offset = -Plinko.ROWS - 1; offset <= Plinko.ROWS + 1; offset++) {
            int z = PlinkoBoard.columnZ(offset);
            if (Math.floorMod(offset, 2) == 1) {
                for (int wy = y + 1; wy <= y + 3; wy++) put(peg, wy, z, Material.QUARTZ_PILLAR);
            } else {
                int bin = (offset + Plinko.ROWS) / 2;
                Material color = Items.material(PlinkoMenu.binDye(Plinko.MULTIPLIERS[bin]) + "_CONCRETE");
                put(peg, y + 1, z, color);
            }
        }
        for (int z : new int[]{cz - 4, cz, cz + 4}) {
            put(peg + 5, y + 1, z, Material.GOLD_BLOCK);
            put(peg + 5, y + 2, z, Material.TARGET);
        }
        spot(Station.PLINKO, peg + 7.5, y + 1, cz + 0.5);
        current = null;
    }

    private void buildWheel(int y) {
        current = null;
        if (placing) {
            // Round black backboard with a gold rim on the east wall.
            int reach = (int) Math.ceil(BigWheel.RADIUS + 2);
            for (int wy = y + 1; wy < CEILING; wy++) {
                for (int z = -reach; z <= reach; z++) {
                    double d = Math.hypot(wy + 0.5 - BigWheel.CENTER_Y, z + 0.5 - BigWheel.CENTER_Z);
                    if (d <= BigWheel.RADIUS + 1.0) put(HALF - 1, wy, z, Material.BLACK_CONCRETE);
                    else if (d <= BigWheel.RADIUS + 1.8) put(HALF - 1, wy, z, Material.GOLD_BLOCK);
                }
            }
        }
        station(Station.WHEEL);
        for (int z : new int[]{-3, 0, 3}) {
            put(HALF - 7, y + 1, z, Material.CHISELED_QUARTZ_BLOCK);
            put(HALF - 7, y + 2, z, Material.GOLD_BLOCK);
        }
        spot(Station.WHEEL, HALF - 8.5, y + 1, 0.5);
        current = null;
    }

    private void buildSlots(int y) {
        station(Station.SLOTS);
        int z = HALF - 3;
        for (int x = -30; x <= -14; x += 2) {
            put(x, y + 1, z, Material.GOLD_BLOCK);
            put(x, y + 2, z, "minecraft:dropper[facing=north]");
            put(x, y + 3, z, Material.SEA_LANTERN);
            put(x + 1, y + 2, z, "minecraft:lever[face=wall,facing=east]");
        }
        spot(Station.SLOTS, -21.5, y + 1, z - 1.5);
        current = null;
    }

    private void buildCashier(int y) {
        station(Station.CASHIER);
        int front = HALF - 7;
        for (int x = 22; x <= 29; x++) put(x, y + 1, front, Material.SMOOTH_QUARTZ);
        for (int z = front + 1; z <= front + 3; z++) {
            put(22, y + 1, z, Material.SMOOTH_QUARTZ);
            put(29, y + 1, z, Material.SMOOTH_QUARTZ);
        }
        put(22, y + 2, front, Material.GOLD_BLOCK);
        put(29, y + 2, front, Material.GOLD_BLOCK);
        spot(Station.CASHIER, 25.5, y + 1, front - 1.5);
        current = null;
    }

    private void buildVault(int y) {
        current = null;
        if (!placing) return;
        // Barred gate in the south wall, with a heavy stone frame.
        for (int x = Vault.GATE_X0 - 1; x <= Vault.GATE_X1 + 1; x++) {
            for (int wy = y + 1; wy <= y + 5; wy++) {
                boolean frame = x == Vault.GATE_X0 - 1 || x == Vault.GATE_X1 + 1 || wy == y + 5;
                put(x, wy, HALF, frame ? Material.POLISHED_DEEPSLATE : Material.IRON_BARS);
            }
        }
        // The room itself, sunk into the void behind the wall.
        for (int x = Vault.ROOM_X0; x <= Vault.ROOM_X1; x++) {
            for (int z = Vault.ROOM_Z0; z <= Vault.ROOM_Z1; z++) {
                boolean edge = x == Vault.ROOM_X0 || x == Vault.ROOM_X1 || z == Vault.ROOM_Z1;
                put(x, y, z, Math.floorMod(x + z, 2) == 0 ? Material.GOLD_BLOCK : Material.POLISHED_DEEPSLATE);
                put(x, Vault.ROOM_TOP, z, Material.POLISHED_DEEPSLATE_BRICKS);
                for (int wy = y + 1; wy < Vault.ROOM_TOP; wy++) {
                    if (edge) put(x, wy, z, wy == y + 3 && !(z == Vault.ROOM_Z1 && x % 3 != 0) ? Material.SEA_LANTERN
                            : Material.POLISHED_DEEPSLATE_BRICKS);
                }
            }
        }
        for (int x = Vault.ROOM_X0 + 1; x < Vault.ROOM_X1; x += 3) {
            put(x, y + 4, Vault.ROOM_Z0 + 3, "minecraft:light[level=15]");
        }
        // Piles of treasure along the back wall.
        int pz = Vault.ROOM_Z1 - 2;
        Material[] piles = {Material.GOLD_BLOCK, Material.EMERALD_BLOCK, Material.DIAMOND_BLOCK};
        int[] px = {22, 26, 29};
        for (int i = 0; i < piles.length; i++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) put(px[i] + dx, y + 1, pz + dz, piles[i]);
            }
            put(px[i], y + 2, pz, piles[i]);
        }
        put(25, y + 1, Vault.ROOM_Z0 + 3, Material.NETHERITE_BLOCK);
        put(24, y + 1, Vault.ROOM_Z0 + 4, Material.NETHERITE_BLOCK);
        put(25, y + 2, Vault.ROOM_Z0 + 4, Material.NETHERITE_BLOCK);
        // Strongboxes down the side walls.
        for (int z = Vault.ROOM_Z0; z <= Vault.ROOM_Z0 + 3; z++) {
            put(Vault.ROOM_X0 + 1, y + 1, z, "minecraft:chest[facing=east]");
            put(Vault.ROOM_X1 - 1, y + 1, z, "minecraft:chest[facing=west]");
            put(Vault.ROOM_X0 + 1, y + 2, z, "minecraft:barrel[facing=up]");
        }
    }

    private void buildBar(int y) {
        current = null;
        if (!placing) return;
        int z0 = Bar.CENTER_Z - Bar.HALF_LENGTH, z1 = Bar.CENTER_Z + Bar.HALF_LENGTH;
        for (int z = z0; z <= z1; z++) {
            put(Bar.COUNTER_X, y + 1, z, Material.STRIPPED_DARK_OAK_WOOD);
            put(Bar.COUNTER_X, y + 2, z, "minecraft:spruce_slab[type=bottom]");
            // Back shelves: barrels with brewing stands and lanterns on top.
            put(Bar.SHELF_X, y + 1, z, "minecraft:barrel[facing=west]");
            put(Bar.SHELF_X, y + 2, z, Math.floorMod(z, 2) == 0 ? "minecraft:brewing_stand" : "minecraft:lantern[hanging=false]");
        }
        for (int x = Bar.COUNTER_X + 1; x < Bar.SHELF_X; x++) {
            put(x, y + 1, z0, Material.STRIPPED_DARK_OAK_WOOD);
            put(x, y + 1, z1, Material.STRIPPED_DARK_OAK_WOOD);
        }
        for (int z = z0; z <= z1; z += 4) put(Bar.COUNTER_X + 2, y + 6, z, "minecraft:light[level=15]");
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
        rouletteWheel.forget();
        crowd.forget();
        vault.forget();
        track.reset();
        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof Player) && isCasinoEntity(entity)) entity.remove();
        }
    }

    private void spawnDecorations() {
        if (world == null) return;
        removeDecorations();
        int y = FLOOR;
        title(0.5, y + 7.5, 12.5, "<gold><bold>RESOURCE CASINO", 3.5f);
        title(0.5, y + 6.2, 12.5, "<gray>Iron • Copper • Emerald • Diamond • Netherite", 1.3f);
        title(0.5, y + 3.4, 12.5, "<yellow>Click the lodestone for the casino menu", 0.8f);

        title(RouletteWheel.CENTER_X, y + 6.0, RouletteWheel.CENTER_Z, "<red><bold>ROULETTE", 2.6f);
        title(RouletteWheel.CENTER_X, y + 5.1, RouletteWheel.CENTER_Z, "<gray>Click the rim to place your bets", 0.9f);

        int cz = PlinkoBoard.CENTER_Z;
        title(PlinkoBoard.PEG_X + 5.5, y + 4.4, cz + 0.5, "<aqua><bold>PLINKO", 2.4f);
        title(PlinkoBoard.PEG_X + 5.5, y + 3.6, cz + 0.5, "<gray>Click a pedestal to drop a ball", 0.8f);
        for (int bin = 0; bin < Plinko.BINS; bin++) {
            double m = Plinko.MULTIPLIERS[bin];
            title(PlinkoBoard.BALL_X + 0.2, y + 2.6, PlinkoBoard.binZ(bin),
                    "<" + Items.hex(PlinkoMenu.binRgb(m)) + "><bold>" + Payouts.format(m), 0.9f);
        }

        title(-21.5, y + 5.4, HALF - 2.5, "<yellow><bold>SLOTS", 2.4f);
        title(-21.5, y + 4.6, HALF - 2.5, "<gray>Click a machine", 0.8f);
        title(25.5, y + 4.4, HALF - 6.5, "<gold><bold>CASHIER", 2.4f);
        title(25.5, y + 3.6, HALF - 6.5, "<gray>Deposit & withdraw", 0.8f);
        title(HALF - 6.5, y + 4.2, 0.5, "<light_purple><bold>SPIN THE WHEEL", 2.0f);
        title(HALF - 6.5, y + 3.5, 0.5, "<gray>Click a pedestal", 0.8f);

        title(RaceTrack.CENTER_X, y + 9.0, RaceTrack.CENTER_Z, "<gold><bold>HORSE RACING", 4.0f);
        title(RaceTrack.CENTER_X, y + 7.6, RaceTrack.CENTER_Z, "<gray>Bet at the lecterns by the grandstand", 1.2f);
        title(Math.floor(RaceTrack.CENTER_X) + 0.5, y + 5.6, RaceTrack.CENTER_Z + (RaceTrack.TRACK_INNER + RaceTrack.TRACK_OUTER) / 2,
                "<white><bold>FINISH", 2.0f);
        double stands = RaceTrack.CENTER_Z + RaceTrack.TRACK_OUTER + 3.5;
        title(-16.5, y + 3.6, stands, "<gold>Horse Racing bets", 0.9f);
        title(18.5, y + 3.6, stands, "<gold>Horse Racing bets", 0.9f);
        title(0.5, y + 3.4, HALF - 0.5, "<green><bold>EXIT", 1.2f);

        title(Bar.COUNTER_X + 2.5, y + 4.6, Bar.CENTER_Z + 0.5, "<gold><bold>BAR", 2.2f);
        title((Vault.GATE_X0 + Vault.GATE_X1) / 2.0 + 0.5, y + 5.0, HALF - 0.6, "<gray><bold>VAULT", 1.4f);

        bigWheel.spawn(world);
        rouletteWheel.spawn(world);
        vault.spawn(world);
        crowd.spawn(world);
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
