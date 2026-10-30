package net.kalob.ResourceCasino;

import java.util.Locale;

/**
 * The five resources the casino trades in. Each one is its own currency:
 * bet iron, win iron. Nothing ever converts between them.
 */
public enum Resource {
    COPPER("Copper", "COPPER_INGOT", "COPPER_BLOCK", 0xE77C56),
    IRON("Iron", "IRON_INGOT", "IRON_BLOCK", 0xE2E2E2),
    EMERALD("Emerald", "EMERALD", "EMERALD_BLOCK", 0x17DD62),
    DIAMOND("Diamond", "DIAMOND", "DIAMOND_BLOCK", 0x4AEDD9),
    NETHERITE("Netherite", "NETHERITE_INGOT", "NETHERITE_BLOCK", 0x9C8A8A);

    private final String displayName;
    private final String itemMaterial;
    private final String blockMaterial;
    private final int color;

    Resource(String displayName, String itemMaterial, String blockMaterial, int color) {
        this.displayName = displayName;
        this.itemMaterial = itemMaterial;
        this.blockMaterial = blockMaterial;
        this.color = color;
    }

    public String displayName() { return displayName; }

    /** Material name of the single unit (ingot or gem). */
    public String itemMaterial() { return itemMaterial; }

    /** Material name of the storage block, worth 9 units. */
    public String blockMaterial() { return blockMaterial; }

    public int color() { return color; }

    public String configKey() { return name().toLowerCase(Locale.ROOT); }

    public Resource next() { return values()[(ordinal() + 1) % values().length]; }

    public Resource previous() { return values()[(ordinal() + values().length - 1) % values().length]; }

    /** Units of this resource a material is worth (1 for the ingot/gem, 9 for the block), or 0. */
    public static int unitsOf(Resource resource, String materialName) {
        if (resource.itemMaterial.equals(materialName)) return 1;
        if (resource.blockMaterial.equals(materialName)) return 9;
        return 0;
    }

    /** The resource a material belongs to, or null if the casino doesn't take it. */
    public static Resource ofMaterial(String materialName) {
        for (Resource resource : values()) {
            if (unitsOf(resource, materialName) > 0) return resource;
        }
        return null;
    }

    public static Resource fromKey(String key) {
        if (key == null) return null;
        for (Resource resource : values()) {
            if (resource.configKey().equalsIgnoreCase(key.trim())) return resource;
        }
        return null;
    }
}
