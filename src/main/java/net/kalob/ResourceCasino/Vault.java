package net.kalob.ResourceCasino;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
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
 * The house vault, built into the wall behind the cashier. Through the barred
 * gate you can see the huge round vault door swung open, stacks of gold,
 * emeralds and diamonds, a few netherite blocks, and a sign with what the house
 * has actually won so far.
 */
final class Vault {

    /** The barred gate in the south wall, x from GATE_X0 to GATE_X1. */
    static final int GATE_X0 = 24, GATE_X1 = 27;
    static final int ROOM_X0 = 20, ROOM_X1 = 31;
    static final int ROOM_Z0 = CasinoWorld.HALF + 1, ROOM_Z1 = CasinoWorld.HALF + 8;
    static final int ROOM_TOP = CasinoWorld.FLOOR + 7;

    private final ResourceCasinoPlugin plugin;
    private TextDisplay sign;
    private BukkitTask refresh;

    Vault(ResourceCasinoPlugin plugin) {
        this.plugin = plugin;
    }

    boolean present() {
        return sign != null && sign.isValid();
    }

    void spawn(World world) {
        forget();
        int y = CasinoWorld.FLOOR + 1;
        // The vault door: two big iron squares turned 45 degrees to each other make a round-ish door,
        // swung open against the inside wall, with a gold wheel in the middle.
        Location hinge = new Location(world, GATE_X1 + 1.9, y + 2.95, ROOM_Z0 + 2.9);
        for (float turn : new float[]{0f, (float) Math.PI / 4}) {
            door(world, hinge, Material.IRON_BLOCK, 0.5f, 4.0f, turn, 0f);
        }
        door(world, hinge, Material.POLISHED_DEEPSLATE, 0.55f, 2.6f, 0f, 0f);
        door(world, hinge, Material.GOLD_BLOCK, 0.7f, 1.0f, (float) Math.PI / 4, -0.12f);
        // A few loose ingots and gems glinting on the piles.
        item(world, new Location(world, 22.5, y + 2.3, ROOM_Z1 - 2.5), Material.GOLD_INGOT);
        item(world, new Location(world, 26.5, y + 2.3, ROOM_Z1 - 2.5), Material.EMERALD);
        item(world, new Location(world, 29.5, y + 2.3, ROOM_Z1 - 2.5), Material.DIAMOND);
        item(world, new Location(world, 25.5, y + 1.3, ROOM_Z0 + 3.5), Material.NETHERITE_INGOT);

        sign = world.spawn(new Location(world, (ROOM_X0 + ROOM_X1) / 2.0 + 0.5, y + 4.6, ROOM_Z1 - 1.0), TextDisplay.class, d -> {
            d.setBillboard(Display.Billboard.CENTER);
            d.setBackgroundColor(Color.fromARGB(170, 0, 0, 0));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1.2f, 1.2f, 1.2f), new Quaternionf()));
            d.setPersistent(false);
            plugin.casino().tag(d, CasinoWorld.ROLE_TITLE);
        });
        updateSign();
        refresh = Bukkit.getScheduler().runTaskTimer(plugin, this::updateSign, 200L, 200L);
    }

    private void door(World world, Location hinge, Material material, float thickness, float size, float turn, float out) {
        // The door is open, so it stands edge-on to the gate: its face points along x.
        Quaternionf rotation = new Quaternionf().rotateX(turn);
        Vector3f center = rotation.transform(new Vector3f(thickness / 2f, size / 2f, size / 2f));
        Vector3f translation = new Vector3f(out, 0, 0).sub(center);
        world.spawn(hinge, BlockDisplay.class, d -> {
            d.setBlock(material.createBlockData());
            d.setBrightness(new Display.Brightness(13, 13));
            d.setTransformation(new Transformation(translation, rotation, new Vector3f(thickness, size, size), new Quaternionf()));
            d.setPersistent(false);
            plugin.casino().tag(d, CasinoWorld.ROLE_TITLE);
        });
    }

    private void item(World world, Location at, Material material) {
        world.spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(material));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateY(0.6f).rotateX(0.3f),
                    new Vector3f(0.8f, 0.8f, 0.8f), new Quaternionf()));
            d.setPersistent(false);
            plugin.casino().tag(d, CasinoWorld.ROLE_TITLE);
        });
    }

    /** Shows what the house is up, per resource. */
    void updateSign() {
        if (sign == null || !sign.isValid()) return;
        HouseLedger ledger = plugin.data().ledger();
        List<String> lines = new ArrayList<>();
        lines.add("<gold><bold>HOUSE WINNINGS");
        for (Resource r : Resource.values()) {
            long profit = ledger.profit(r);
            if (ledger.rounds(r) > 0) lines.add(Items.amount(r, Math.max(0, profit)));
        }
        if (lines.size() == 1) lines.add("<gray>Nothing yet. Give it time.");
        sign.text(Items.mm(String.join("\n", lines)));
    }

    void forget() {
        if (refresh != null) {
            refresh.cancel();
            refresh = null;
        }
        sign = null;
    }
}
