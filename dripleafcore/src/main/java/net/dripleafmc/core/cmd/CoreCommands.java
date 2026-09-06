package net.dripleafmc.core.cmd;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.dripleafmc.core.DripleafCore;
import net.dripleafmc.core.homes.Homes;
import net.dripleafmc.core.profile.Profile;
import net.dripleafmc.core.tp.Rtp;
import net.dripleafmc.core.tp.Tpa;
import net.dripleafmc.core.util.Num;
import net.dripleafmc.core.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

/**
 * Command registration.
 *
 * Everything registers through Paper's lifecycle registrar so commands.yml can turn a
 * command off entirely rather than shadowing another plugin's version of it — which is
 * what you want when Essentials already owns /home on this box.
 */
public final class CoreCommands {

    /** Adapter so each command can be a lambda instead of a class. */
    private record Simple(String permission, BiConsumer<Player, String[]> handler,
                          java.util.function.BiFunction<Player, String[], Collection<String>> completer)
            implements BasicCommand {

        @Override
        public void execute(CommandSourceStack source, String[] args) {
            CommandSender sender = source.getSender();
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("That command is for players.", NamedTextColor.RED));
                return;
            }
            if (permission != null && !player.hasPermission(permission)) {
                player.sendMessage(Component.text("You don't have permission for that.", NamedTextColor.RED));
                return;
            }
            handler.accept(player, args);
        }

        @Override
        public Collection<String> suggest(CommandSourceStack source, String[] args) {
            if (completer == null || !(source.getSender() instanceof Player player)) return List.of();
            return completer.apply(player, args);
        }
    }

    private final DripleafCore core;
    private YamlConfiguration toggles;

    public CoreCommands(DripleafCore core) {
        this.core = core;
    }

    public void register() {
        toggles = YamlConfiguration.loadConfiguration(new File(core.getDataFolder(), "commands.yml"));

        core.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();

            if (on("SHOP")) add(registrar, "shop", "Open the server shop", List.of(),
                    new Simple(null, (p, a) -> core.screens().shop(p), null));

            if (on("QUICKBUY")) add(registrar, "quickbuy", "Your saved one-click purchases", List.of("qb"),
                    new Simple(null, this::quickBuy, (p, a) -> List.of("clear")));

            if (on("SELL")) add(registrar, "sell", "Sell items", List.of("sellchest"),
                    new Simple(null, this::sell, (p, a) -> List.of("hand", "all")));

            if (on("WORTH")) add(registrar, "worth", "Check an item's price", List.of("price"),
                    new Simple(null, this::worth, null));

            if (on("SELLHISTORY")) add(registrar, "sellhistory", "Your recent sales", List.of(),
                    new Simple(null, (p, a) -> core.screens().sellHistory(p), null));

            if (on("SELLMULTI")) add(registrar, "sellmulti", "Your sell multiplier", List.of(),
                    new Simple(null, (p, a) -> p.sendMessage(core.sell().multiplierLine(p)), null));

            if (on("SHARDS")) add(registrar, "shards", "Check shards", List.of("shard"),
                    new Simple(null, this::shards, null));

            if (on("SHARDSHOP")) add(registrar, "shardshop", "Spend shards", List.of("sshop"),
                    new Simple(null, (p, a) -> core.screens().shardShop(p), null));

            if (on("HOME")) {
                add(registrar, "home", "Go to a home", List.of(),
                        new Simple(null, this::home, this::homeNames));
                add(registrar, "homes", "List your homes", List.of(),
                        new Simple(null, (p, a) -> core.screens().homes(p), null));
                add(registrar, "sethome", "Set a home", List.of(),
                        new Simple(null, (p, a) -> {
                            if (a.length == 0) core.screens().setHomeForm(p);
                            else setHome(p, a[0]);
                        }, null));
                add(registrar, "delhome", "Delete a home", List.of(),
                        new Simple(null, (p, a) -> {
                            if (a.length == 0) core.screens().deleteHomeMenu(p);
                            else if (core.homes().delete(p.getUniqueId(), a[0])) {
                                core.lang().send(p, "home.deleted", Text.p("name", a[0]));
                            } else {
                                core.lang().send(p, "home.unknown", Text.p("name", a[0]));
                            }
                        }, this::homeNames));
            }

            if (on("RTP")) add(registrar, "rtp", "Random teleport", List.of("wild"),
                    new Simple(null, this::rtp, null));

            if (on("TPA")) {
                add(registrar, "tpa", "Ask to teleport to someone", List.of(),
                        new Simple(null, this::tpa, (p, a) -> onlineNames()));
                add(registrar, "tpaccept", "Accept a teleport request", List.of("tpayes"),
                        new Simple(null, (p, a) -> acceptTpa(p), null));
                add(registrar, "tpdeny", "Deny a teleport request", List.of("tpano"),
                        new Simple(null, (p, a) -> {
                            core.tpa().take(p.getUniqueId());
                            core.lang().send(p, "tpa.denied");
                        }, null));
            }

            if (on("SPAWN")) add(registrar, "spawn", "Go to spawn", List.of(),
                    new Simple(null, (p, a) -> core.teleports().warp(p,
                            p.getWorld().getSpawnLocation(), core.cfg().homeWarmup), null));

            if (on("SETTINGS")) add(registrar, "settings", "Your preferences", List.of("prefs"),
                    new Simple(null, (p, a) -> core.screens().settings(p), null));

            if (on("STATS")) add(registrar, "stats", "Player stats", List.of("profile"),
                    new Simple(null, this::stats, (p, a) -> onlineNames()));

            if (on("BOUNTY")) add(registrar, "bounty", "Bounties", List.of("bounties"),
                    new Simple(null, this::bounty, (p, a) -> onlineNames()));

            if (on("LEADERBOARD")) add(registrar, "leaderboard", "Server leaderboards", List.of("lb", "top"),
                    new Simple(null, (p, a) -> core.screens().leaderboards(p), null));

            if (on("RULES")) add(registrar, "rules", "Server rules", List.of(),
                    new Simple(null, (p, a) -> core.screens().rules(p), null));

            if (on("DLHELP")) add(registrar, "dlhelp", "DripleafMC menu", List.of("guide", "menu"),
                    new Simple(null, (p, a) -> core.screens().help(p), null));

            add(registrar, "dripleafcore", "Admin commands", List.of("dlcore"),
                    new Simple("dripleaf.admin", (p, a) -> {
                        if (a.length > 0 && a[0].equalsIgnoreCase("reload")) {
                            core.reloadAll();
                            p.sendMessage(Component.text("DripleafCore reloaded.", NamedTextColor.GREEN));
                        } else {
                            p.sendMessage(Component.text("/dripleafcore reload", NamedTextColor.GRAY));
                        }
                    }, (p, a) -> List.of("reload")));
        });
    }

    private boolean on(String key) {
        return toggles.getBoolean("COMMANDS." + key, true);
    }

    private void add(Commands registrar, String label, String description, Collection<String> aliases,
                     BasicCommand command) {
        registrar.register(label, description, aliases, command);
    }

    // ------------------------------------------------------------- handlers

    private void quickBuy(Player player, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("clear")) {
            core.quickBuy().clearAll(player.getUniqueId());
            core.lang().send(player, "quickbuy.cleared-all");
            return;
        }
        core.screens().quickBuy(player);
    }

    private void sell(Player player, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("hand")) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType().isAir()) return;
            org.bukkit.inventory.Inventory temp = Bukkit.createInventory(null, 9);
            temp.addItem(hand.clone());
            var result = core.sell().sell(player, temp);
            if (result.total() > 0) {
                player.getInventory().setItemInMainHand(null);
                core.sell().announce(player, result);
            } else {
                core.lang().send(player, "economy.sold-nothing");
            }
            return;
        }
        core.sell().openChest(player);
    }

    private void worth(Player player, String[] args) {
        Material material = null;
        if (args.length > 0) {
            material = Material.matchMaterial(args[0]);
        } else {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (!hand.getType().isAir()) material = hand.getType();
        }
        if (material == null) {
            core.screens().worthBook(player);
            return;
        }
        Profile profile = core.profiles().get(player.getUniqueId());
        double multiplier = core.worth().multiplier(player, profile);
        double sell = core.worth().sellPrice(material) * multiplier;
        player.sendMessage(Component.text(Text.pretty(material) + ": ", NamedTextColor.GRAY)
                .append(Component.text("buy $" + Num.money(core.worth().buyPrice(material)), NamedTextColor.WHITE))
                .append(Component.text("  sell $" + Num.money(sell), NamedTextColor.GREEN)));
    }

    private void shards(Player player, String[] args) {
        Profile profile = core.profiles().get(player.getUniqueId());
        if (args.length > 0) {
            Player other = Bukkit.getPlayerExact(args[0]);
            if (other == null) {
                core.lang().send(player, "unknown-player", Text.p("name", args[0]));
                return;
            }
            profile = core.profiles().get(other.getUniqueId());
        }
        if (profile == null) return;
        core.lang().send(player, "shards.balance", Text.p("amount", Num.money(profile.shards)));
    }

    private void home(Player player, String[] args) {
        if (core.combat().deny(player)) return;
        var homes = core.homes().homes(player.getUniqueId());
        if (args.length == 0) {
            if (homes.size() == 1) {
                Homes.Home only = homes.values().iterator().next();
                core.teleports().warp(player, only.toLocation(), core.cfg().homeWarmup);
            } else {
                core.screens().homes(player);
            }
            return;
        }
        Homes.Home home = core.homes().get(player.getUniqueId(), args[0]);
        if (home == null) {
            core.lang().send(player, "home.unknown", Text.p("name", args[0]));
            return;
        }
        core.teleports().warp(player, home.toLocation(), core.cfg().homeWarmup);
    }

    /** Shared by the command and the dialog form. */
    public void setHome(Player player, String name) {
        if (!Homes.validName(name)) {
            core.lang().send(player, "home.name-invalid");
            return;
        }
        var homes = core.homes().homes(player.getUniqueId());
        boolean replacing = core.homes().get(player.getUniqueId(), name) != null;
        if (!replacing && homes.size() >= core.homes().slots(player)) {
            core.lang().send(player, "home.limit", Text.p("count", String.valueOf(core.homes().slots(player))));
            return;
        }
        core.homes().set(player, name);
        core.lang().send(player, "home.set", Text.p("name", name));
    }

    private Collection<String> homeNames(Player player, String[] args) {
        List<String> names = new ArrayList<>();
        for (Homes.Home home : core.homes().homes(player.getUniqueId()).values()) names.add(home.name());
        return names;
    }

    private void rtp(Player player, String[] args) {
        if (core.combat().deny(player)) return;
        if (args.length > 0) {
            for (Rtp.Region region : core.rtp().regions()) {
                if (region.id().equalsIgnoreCase(args[0])) {
                    core.rtp().go(player, region);
                    return;
                }
            }
        }
        core.screens().rtp(player);
    }

    private void tpa(Player player, String[] args) {
        if (args.length == 0) {
            core.lang().send(player, "tpa.none");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            core.lang().send(player, "unknown-player", Text.p("name", args[0]));
            return;
        }
        if (target.equals(player)) {
            core.lang().send(player, "tpa.self");
            return;
        }
        Profile targetProfile = core.profiles().get(target.getUniqueId());
        if (targetProfile != null && !targetProfile.flag(Profile.FLAG_TPA_ALLOWED)) {
            core.lang().send(player, "tpa.denied");
            return;
        }
        core.tpa().send(player.getUniqueId(), target.getUniqueId());
        core.lang().send(player, "tpa.sent", Text.p("name", target.getName()));
        core.screens().tpaPrompt(target, player);
    }

    public void acceptTpa(Player player) {
        Tpa.Request request = core.tpa().take(player.getUniqueId());
        if (request == null) {
            core.lang().send(player, "tpa.none");
            return;
        }
        Player requester = Bukkit.getPlayer(request.from());
        if (requester == null) {
            core.lang().send(player, "tpa.expired");
            return;
        }
        core.lang().send(player, "tpa.accepted");
        core.teleports().warp(requester, player.getLocation(), core.cfg().tpaWarmup);
    }

    private void stats(Player player, String[] args) {
        if (args.length == 0) {
            core.screens().stats(player, player, core.profiles().get(player.getUniqueId()));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            core.lang().send(player, "unknown-player", Text.p("name", args[0]));
            return;
        }
        core.screens().stats(player, target, core.profiles().get(target.getUniqueId()));
    }

    private void bounty(Player player, String[] args) {
        if (!core.cfg().bountyEnabled) return;
        if (args.length == 0) {
            core.screens().bounties(player);
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            core.lang().send(player, "unknown-player", Text.p("name", args[0]));
            return;
        }
        if (args.length == 1) {
            core.screens().bountyAmount(player, target);
            return;
        }
        double amount = Num.parse(args[1]);
        if (amount <= 0) {
            core.lang().send(player, "number-invalid", Text.p("input", args[1]));
            return;
        }
        placeBounty(player, target, amount);
    }

    /** Shared by the command and the dialog form. */
    public void placeBounty(Player placer, Player target, double amount) {
        if (placer.equals(target)) {
            core.lang().send(placer, "bounty.self");
            return;
        }
        if (amount < core.cfg().bountyMin) {
            core.lang().send(placer, "bounty.too-small", Text.p("amount", Num.money(core.cfg().bountyMin)));
            return;
        }
        if (amount > core.cfg().bountyMax) {
            core.lang().send(placer, "bounty.too-large", Text.p("amount", Num.money(core.cfg().bountyMax)));
            return;
        }
        Profile profile = core.profiles().get(placer.getUniqueId());
        if (!core.money().take(placer, amount, profile)) {
            core.lang().send(placer, "economy.insufficient",
                    Text.p("amount", Num.money(amount - core.money().balance(placer))));
            return;
        }
        core.bounties().add(target, amount, placer.getName());
        Bukkit.broadcast(core.lang().prefixed("bounty.set",
                Text.p("amount", Num.money(amount)),
                Text.p("name", target.getName())));
    }

    private Collection<String> onlineNames() {
        List<String> names = new ArrayList<>(Bukkit.getOnlinePlayers().size());
        for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
        return names;
    }
}
