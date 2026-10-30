package net.kalob.ResourceCasino;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class CasinoCommand implements TabExecutor {

    private static final List<String> PLAYER_SUBS = List.of("cashier", "roulette", "slots", "derby", "wheel", "tp", "leave", "balance", "odds");
    private static final List<String> ADMIN_SUBS = List.of("build", "stats", "reload");

    private final ResourceCasinoPlugin plugin;

    CasinoCommand(ResourceCasinoPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);

        if (ADMIN_SUBS.contains(sub)) {
            if (!sender.hasPermission("resourcecasino.admin")) {
                plugin.tell(sender, "<red>You don't have permission to do that.");
                return true;
            }
            switch (sub) {
                case "build" -> {
                    plugin.tell(sender, "<gray>Building the casino floor...");
                    if (plugin.casino().build()) {
                        plugin.tell(sender, "<green>Casino floor built in world <white>" + plugin.worldName()
                                + "</white>. <gray>Players can go with <yellow>/casino tp</yellow>.");
                        if (sender instanceof Player player && !plugin.casino().isCasinoWorld(player.getWorld())) {
                            plugin.casino().enter(player);
                        }
                    } else {
                        plugin.tell(sender, "<red>Couldn't build the casino floor. Check the server log.");
                    }
                }
                case "stats" -> {
                    plugin.tell(sender, "<gold>House results so far:");
                    HouseLedger ledger = plugin.data().ledger();
                    for (Resource r : Resource.values()) {
                        long wagered = ledger.wagered(r);
                        String kept = wagered == 0 ? "-" : Payouts.percent(ledger.profit(r) / (double) wagered);
                        sender.sendMessage(Items.mm("  " + Items.amount(r, wagered) + " <gray>bet over " + ledger.rounds(r)
                                + " rounds, house " + (ledger.profit(r) >= 0 ? "<green>+" : "<red>") + ledger.profit(r)
                                + " <gray>(" + kept + " kept)"));
                    }
                }
                case "reload" -> {
                    plugin.reload();
                    plugin.tell(sender, "<green>Config reloaded.");
                }
                default -> { }
            }
            return true;
        }

        if (sub.equals("odds")) {
            plugin.tell(sender, "<gold>Average paid back per bet (the rest is the house edge):");
            for (String line : LobbyMenu.oddsLore(plugin)) sender.sendMessage(Items.mm("  " + line));
            return true;
        }

        if (!(sender instanceof Player player)) {
            plugin.tell(sender, "<gray>Players only. Console can use <yellow>/casino build|stats|reload|odds</yellow>.");
            return true;
        }
        if (!player.hasPermission("resourcecasino.play")) {
            plugin.tell(player, "<red>You don't have permission to play.");
            return true;
        }

        switch (sub) {
            case "" -> new LobbyMenu(plugin, player).open();
            case "tp" -> plugin.casino().enter(player);
            case "leave" -> {
                if (plugin.casino().isCasinoWorld(player.getWorld())) plugin.casino().leave(player);
                else plugin.tell(player, "<gray>You're not on the casino floor.");
            }
            case "balance" -> {
                List<String> parts = new ArrayList<>();
                for (Resource r : Resource.values()) parts.add(Items.amount(r, plugin.balance(player.getUniqueId(), r)));
                plugin.tell(player, "<gray>Balance:</gray> " + String.join("<dark_gray>, </dark_gray>", parts));
            }
            case "cashier" -> new CashierMenu(plugin, player).open();
            case "roulette", "slots", "derby", "wheel" -> {
                if (!plugin.playAnywhere() && !plugin.casino().isCasinoWorld(player.getWorld())) {
                    plugin.tell(player, "<gray>Games are played on the casino floor: <yellow>/casino tp");
                    return true;
                }
                Menu menu = switch (sub) {
                    case "roulette" -> new RouletteMenu(plugin, player);
                    case "slots" -> new SlotsMenu(plugin, player);
                    case "derby" -> new DerbyMenu(plugin, player);
                    default -> new WheelMenu(plugin, player);
                };
                menu.open();
            }
            default -> plugin.tell(player, "<gray>Usage: <yellow>/casino [" + String.join("|", PLAYER_SUBS) + "]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        List<String> options = new ArrayList<>(PLAYER_SUBS);
        if (sender.hasPermission("resourcecasino.admin")) options.addAll(ADMIN_SUBS);
        String prefix = args[0].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.startsWith(prefix));
        return options;
    }
}
