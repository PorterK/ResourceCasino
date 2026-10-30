package net.kalob.ResourceCasino;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Everything the casino has to remember between restarts, in {@code data.yml}:
 * wallets, the house ledger, where each player was before they came to the
 * casino floor, and whether the floor has been built.
 */
final class CasinoData {

    private final Wallet wallet = new Wallet();
    private final HouseLedger ledger = new HouseLedger();
    private final Map<UUID, Location> returnLocations = new HashMap<>();
    private final Map<UUID, String[]> pendingReturns = new HashMap<>();
    private boolean built;
    private boolean dirty;

    /** Something changed since the last save. */
    boolean dirty() { return dirty || wallet.dirty(); }

    void markClean() {
        dirty = false;
        wallet.markClean();
    }

    Wallet wallet() { return wallet; }

    HouseLedger ledger() { return ledger; }

    boolean built() { return built; }

    void setBuilt(boolean built) {
        this.built = built;
        dirty = true;
    }

    void setReturn(UUID player, Location location) {
        returnLocations.put(player, location.clone());
        pendingReturns.remove(player);
        dirty = true;
    }

    /** Where to send a player who leaves the casino, or null if we don't know. */
    Location takeReturn(UUID player) {
        Location location = returnLocations.remove(player);
        if (location == null) {
            String[] saved = pendingReturns.remove(player);
            if (saved != null) location = parse(saved);
        }
        if (location != null) dirty = true;
        return location;
    }

    String saveToString() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("casino.built", built);
        wallet.snapshot().forEach((id, row) -> {
            for (Resource resource : Resource.values()) {
                if (row[resource.ordinal()] > 0) {
                    yaml.set("wallets." + id + "." + resource.configKey(), row[resource.ordinal()]);
                }
            }
        });
        for (Resource resource : Resource.values()) {
            String base = "house." + resource.configKey() + ".";
            yaml.set(base + "wagered", ledger.wagered(resource));
            yaml.set(base + "paid", ledger.paid(resource));
            yaml.set(base + "rounds", ledger.rounds(resource));
        }
        returnLocations.forEach((id, loc) -> {
            if (loc.getWorld() == null) return;
            yaml.set("returns." + id, String.join(",", loc.getWorld().getName(),
                    Double.toString(loc.getX()), Double.toString(loc.getY()), Double.toString(loc.getZ()),
                    Float.toString(loc.getYaw()), Float.toString(loc.getPitch())));
        });
        pendingReturns.forEach((id, parts) -> yaml.set("returns." + id, String.join(",", parts)));
        return yaml.saveToString();
    }

    void loadFromString(String contents) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(contents);
        wallet.clear();
        returnLocations.clear();
        pendingReturns.clear();
        built = yaml.getBoolean("casino.built", false);
        ConfigurationSection wallets = yaml.getConfigurationSection("wallets");
        if (wallets != null) {
            for (String key : wallets.getKeys(false)) {
                UUID id;
                try {
                    id = UUID.fromString(key);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                for (Resource resource : Resource.values()) {
                    long amount = wallets.getLong(key + "." + resource.configKey(), 0);
                    if (amount > 0) wallet.set(id, resource, amount);
                }
            }
        }
        for (Resource resource : Resource.values()) {
            String base = "house." + resource.configKey() + ".";
            ledger.load(resource, yaml.getLong(base + "wagered"), yaml.getLong(base + "paid"), yaml.getLong(base + "rounds"));
        }
        ConfigurationSection returns = yaml.getConfigurationSection("returns");
        if (returns != null) {
            for (String key : returns.getKeys(false)) {
                try {
                    String[] parts = returns.getString(key, "").split(",");
                    if (parts.length == 6) pendingReturns.put(UUID.fromString(key), parts);
                } catch (IllegalArgumentException ignored) {
                    // skip bad entries
                }
            }
        }
        markClean();
    }

    private static Location parse(String[] parts) {
        try {
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) return null;
            return new Location(world, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]),
                    Double.parseDouble(parts[3]), Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    void load(File file) throws IOException, InvalidConfigurationException {
        if (!file.exists()) return;
        loadFromString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
    }

    /** Writes atomically (temp file, then move) so a crash mid-save can't wipe wallets. */
    static void write(File file, String contents) throws IOException {
        Path target = file.toPath();
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(file.getName() + ".tmp");
        Files.writeString(temp, contents, StandardCharsets.UTF_8);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailed) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
