package net.kalob.ResourceCasino;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * ResourceCasino: deposit real resources at the cashier, play roulette, slots,
 * Duck Derby and the wheel, and withdraw what's left. The house always has the edge.
 */
public final class ResourceCasinoPlugin extends JavaPlugin {

    static final String PREFIX = "<gold><bold>Casino</bold></gold> <dark_gray>»</dark_gray> ";

    private final CasinoData data = new CasinoData();
    private final Map<Resource, Long> maxBet = new EnumMap<>(Resource.class);
    private final Map<UUID, Prefs> prefs = new HashMap<>();
    private final Set<Pending> pending = new LinkedHashSet<>();

    private CasinoWorld casino;
    private NamespacedKey entityKey;
    private Wheel wheel = Wheel.defaults();
    private boolean playAnywhere = true;
    private String worldName = "resourcecasino";
    private boolean keepLoaded = true;
    private double announceMultiplier = 10;
    private double derbyEdge = 0.10;
    private int raceSeconds = 8;
    private int wheelSeconds = 6;
    private int wheelTop;

    /** What a player last chose to bet with, so every game remembers it. */
    static final class Prefs {
        Resource resource = Resource.IRON;
        long stake = 1;
    }

    /** A bet that's been paid for but whose animation hasn't finished yet. */
    static final class Pending {
        final UUID player;
        final String playerName;
        final Resource resource;
        final long stake;
        final long payout;
        final String game;

        Pending(UUID player, String playerName, Resource resource, long stake, long payout, String game) {
            this.player = player;
            this.playerName = playerName;
            this.resource = resource;
            this.stake = stake;
            this.payout = payout;
            this.game = game;
        }
    }

    @Override
    public void onEnable() {
        entityKey = new NamespacedKey(this, "casino_entity");
        saveDefaultConfig();
        reload();
        try {
            data.load(dataFile());
        } catch (Exception e) {
            getLogger().severe("Could not read data.yml (" + e + "). Keeping a copy and starting fresh.");
            File broken = new File(getDataFolder(), "data.yml.broken-" + System.currentTimeMillis());
            if (!dataFile().renameTo(broken)) getLogger().severe("Could not back up data.yml either!");
        }

        casino = new CasinoWorld(this);
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getServer().getPluginManager().registerEvents(new CasinoListener(this), this);

        PluginCommand command = getCommand("casino");
        if (command != null) {
            CasinoCommand handler = new CasinoCommand(this);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }

        // Autosave every minute, writing the file off the main thread.
        Bukkit.getScheduler().runTaskTimer(this, () -> save(true), 20L * 60, 20L * 60);

        // Load the casino floor once the default worlds are up.
        Bukkit.getScheduler().runTask(this, () -> {
            if (data.built()) casino.load();
        });

        if (Boolean.getBoolean(SelfTest.PROPERTY)) {
            getLogger().warning("Self-test mode enabled: the server will shut down when the test finishes.");
            new SelfTest(this).start();
        }
    }

    @Override
    public void onDisable() {
        // Nobody keeps a casino screen open once the plugin's gone (they could take the icons).
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu) player.closeInventory();
        }
        settleAll();
        if (casino != null) casino.shutdown();
        save(false);
    }

    public void reload() {
        reloadConfig();
        playAnywhere = getConfig().getBoolean("play-anywhere", true);
        worldName = getConfig().getString("world-name", "resourcecasino");
        keepLoaded = getConfig().getBoolean("keep-casino-loaded", true);
        announceMultiplier = getConfig().getDouble("announce-big-wins", 10);
        derbyEdge = Math.max(0.02, Math.min(0.5, getConfig().getDouble("duck-derby.house-edge", 0.10)));
        raceSeconds = Math.max(4, Math.min(30, getConfig().getInt("duck-derby.race-seconds", 8)));
        wheelSeconds = Math.max(3, Math.min(20, getConfig().getInt("wheel.spin-seconds", 6)));
        for (Resource resource : Resource.values()) {
            maxBet.put(resource, Math.max(1, getConfig().getLong("max-bet." + resource.configKey(), 64)));
        }
        List<Double> layout = getConfig().getDoubleList("wheel.segments");
        Wheel configured = Wheel.of(layout);
        if (configured == null) {
            getLogger().warning("wheel.segments must be 24 multipliers averaging under 1 (the house has to win). Using the default wheel.");
            wheel = Wheel.defaults();
        } else {
            wheel = configured;
        }
        if (casino != null) casino.wheelChanged();
    }

    // ---- money ---------------------------------------------------------------------------

    /**
     * Takes a stake from the player's balance for a round whose result is already decided.
     * Returns the pending payout (credited by {@link #settle}), or null if they can't play.
     */
    Pending placeBet(Player player, String game, Resource resource, long stake, long payout) {
        String problem = betProblem(player.getUniqueId(), resource, stake);
        if (problem != null) {
            tell(player, problem);
            return null;
        }
        return book(player.getUniqueId(), player.getName(), game, resource, stake, payout);
    }

    /** Why this bet can't be placed, or null if it can. */
    String betProblem(UUID player, Resource resource, long stake) {
        if (stake <= 0) return "<red>Place a bet first.";
        if (stake > maxBet(resource)) {
            return "<red>The table limit is " + Items.amount(resource, maxBet(resource)) + "<red>.";
        }
        long have = balance(player, resource);
        if (have < stake) {
            return "<red>You need " + Items.amount(resource, stake) + " <red>but have "
                    + Items.amount(resource, have) + "<red>. Visit the cashier.";
        }
        return null;
    }

    /** Takes the stake and books the payout. Returns null if the balance is short. */
    Pending book(UUID player, String name, String game, Resource resource, long stake, long payout) {
        if (!data.wallet().withdraw(player, resource, stake)) return null;
        data.ledger().wager(resource, stake);
        Pending bet = new Pending(player, name, resource, stake, payout, game);
        pending.add(bet);
        return bet;
    }

    /** Pays out a finished round (once; repeat calls do nothing). */
    void settle(Pending bet) {
        if (bet == null || !pending.remove(bet)) return;
        if (bet.payout > 0) {
            data.wallet().deposit(bet.player, bet.resource, bet.payout);
            data.ledger().pay(bet.resource, bet.payout);
        }
        if (announceMultiplier > 0 && bet.payout >= bet.stake * announceMultiplier) {
            Bukkit.getServer().sendMessage(Items.mm(PREFIX + "<yellow>" + bet.playerName + "</yellow> <gray>just won</gray> "
                    + Items.amount(bet.resource, bet.payout) + " <gray>on</gray> <gold>" + bet.game + "</gold><gray>!</gray>"));
        }
    }

    void settleAll() {
        for (Pending bet : new ArrayList<>(pending)) settle(bet);
    }

    int pendingCount() { return pending.size(); }

    long balance(UUID player, Resource resource) {
        return data.wallet().balance(player, resource);
    }

    long maxBet(Resource resource) { return maxBet.getOrDefault(resource, 64L); }

    Prefs prefs(UUID player) { return prefs.computeIfAbsent(player, k -> new Prefs()); }

    // ---- persistence -----------------------------------------------------------------------

    File dataFile() { return new File(getDataFolder(), "data.yml"); }

    void save(boolean async) {
        if (!data.dirty()) return;
        String contents = data.saveToString();
        data.markClean();
        File file = dataFile();
        Runnable write = () -> {
            try {
                CasinoData.write(file, contents);
            } catch (IOException e) {
                getLogger().severe("Could not save data.yml: " + e);
            }
        };
        if (async && isEnabled()) Bukkit.getScheduler().runTaskAsynchronously(this, write);
        else write.run();
    }

    // ---- accessors -------------------------------------------------------------------------

    CasinoData data() { return data; }

    CasinoWorld casino() { return casino; }

    NamespacedKey entityKey() { return entityKey; }

    Wheel wheel() { return wheel; }

    boolean playAnywhere() { return playAnywhere; }

    String worldName() { return worldName; }

    boolean keepLoaded() { return keepLoaded; }

    double derbyEdge() { return derbyEdge; }

    int raceTicks() { return raceSeconds * 20; }

    int wheelTicks() { return wheelSeconds * 20; }

    /** Which wheel segment is currently under the pointer (shared by the GUI and the big wheel). */
    int wheelTop() { return wheelTop; }

    void setWheelTop(int index) { wheelTop = Math.floorMod(index, wheel.size()); }

    RandomGenerator random() { return ThreadLocalRandom.current(); }

    void tell(CommandSender sender, String miniMessage) {
        sender.sendMessage(Items.mm(PREFIX + miniMessage));
    }

    Component text(String miniMessage) { return Items.mm(miniMessage); }
}
