package net.kalob.ResourceCasino;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Small helpers for building GUI icons and chat text. */
final class Items {

    static final MiniMessage MM = MiniMessage.miniMessage();

    private Items() {}

    static Component mm(String miniMessage) {
        return MM.deserialize(miniMessage).decoration(TextDecoration.ITALIC, false);
    }

    static ItemStack icon(Material material, String name, String... lore) {
        return icon(material, 1, false, name, List.of(lore));
    }

    static ItemStack icon(Material material, int amount, boolean glint, String name) {
        return icon(material, amount, glint, name, List.of());
    }

    static ItemStack icon(Material material, int amount, boolean glint, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material, Math.max(1, Math.min(64, amount)));
        stack.editMeta(meta -> {
            meta.displayName(mm(name));
            if (!lore.isEmpty()) {
                List<Component> lines = new ArrayList<>(lore.size());
                for (String line : lore) lines.add(mm(line));
                meta.lore(lines);
            }
            meta.addItemFlags(ItemFlag.values());
            if (glint) meta.setEnchantmentGlintOverride(true);
        });
        return stack;
    }

    static ItemStack filler(Material pane) {
        return icon(pane, " ");
    }

    static Material material(String name) {
        Material material = Material.matchMaterial(name);
        return material == null ? Material.BARRIER : material;
    }

    static Material unit(Resource resource) {
        return material(resource.itemMaterial());
    }

    static Material block(Resource resource) {
        return material(resource.blockMaterial());
    }

    static Material pane(String dye) {
        return material(dye + "_STAINED_GLASS_PANE");
    }

    static String hex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    /** "<#E2E2E2>12 Iron</#E2E2E2>" */
    static String amount(Resource resource, long amount) {
        String color = hex(resource.color());
        return "<" + color + ">" + amount + " " + resource.displayName() + "</" + color + ">";
    }

    static TextColor color(Resource resource) {
        return TextColor.color(resource.color());
    }
}
