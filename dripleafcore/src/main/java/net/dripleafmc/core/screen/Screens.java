package net.dripleafmc.core.screen;

import net.dripleafmc.core.DripleafCore;
import net.dripleafmc.core.bounty.Bounties;
import net.dripleafmc.core.homes.Homes;
import net.dripleafmc.core.profile.Profile;
import net.dripleafmc.core.quickbuy.QuickBuy;
import net.dripleafmc.core.sell.Sell;
import net.dripleafmc.core.shards.ShardShop;
import net.dripleafmc.core.shop.Shop;
import net.dripleafmc.core.tp.Rtp;
import net.dripleafmc.core.ui.Menu;
import net.dripleafmc.core.ui.MenuButton;
import net.dripleafmc.core.ui.MenuForm;
import net.dripleafmc.core.util.Num;
import net.dripleafmc.core.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Every screen in the plugin. Screens describe *what* to show; the Ui router decides
 * whether that becomes a dialog or a chest menu, so no feature exists on only one path.
 */
public final class Screens {

    private static final Component BACK = Component.text("Back", NamedTextColor.GRAY);
    private static final Component CLOSE = Component.text("Close", NamedTextColor.GRAY);
    private static final int MAX_BUY = 2304;   // 36 stacks

    private final DripleafCore core;

    public Screens(DripleafCore core) {
        this.core = core;
    }

    private Component money(double amount) {
        return Component.text("$" + Num.money(amount), NamedTextColor.GREEN);
    }

    private Component shardText(long amount) {
        return Component.text(Num.compact(amount) + " shards", NamedTextColor.LIGHT_PURPLE);
    }

    // ------------------------------------------------------------------ shop

    public void shop(Player player) {
        if (!player.hasPermission(core.cfg().shopPermission)) {
            core.lang().send(player, "shop.locked");
            return;
        }
        Menu menu = new Menu(core.shop().title())
                .columns(core.cfg().menuColumns)
                .body(Component.text("Balance: ", NamedTextColor.GRAY).append(money(core.money().balance(player))));

        for (Shop.Category category : core.shop().categories()) {
            menu.add(MenuButton.of(category.display(), category.lore(), category.icon(),
                    p -> shopCategory(p, category)));
        }
        if (core.cfg().quickBuyEnabled) {
            menu.add(MenuButton.of(
                    Component.text("Quick Buy", NamedTextColor.YELLOW),
                    Component.text("Your saved one-click purchases"),
                    Material.HOPPER, this::quickBuy));
        }
        core.ui().open(player, menu);
    }

    public void shopCategory(Player player, Shop.Category category) {
        Menu menu = new Menu(category.display())
                .columns(core.cfg().menuColumns)
                .body(Component.text("Balance: ", NamedTextColor.GRAY).append(money(core.money().balance(player))))
                .back(BACK, this::shop);

        for (Shop.Entry entry : category.items()) {
            Component price = Component.text("$" + Num.money(entry.price()) + " each", NamedTextColor.GRAY);
            menu.add(MenuButton.of(
                    entry.display().color(NamedTextColor.WHITE),
                    List.of(price),
                    entry.material(), 1,
                    p -> shopQuantity(p, category, entry)));
        }
        core.ui().open(player, menu);
    }

    public void shopQuantity(Player player, Shop.Category category, Shop.Entry entry) {
        double balance = core.money().balance(player);
        int affordable = (int) Math.floor(balance / entry.price());
        if (affordable < 1) {
            core.lang().send(player, "economy.insufficient",
                    Text.p("amount", Num.money(entry.price() - balance)));
            return;
        }
        int max = Math.min(MAX_BUY, affordable);

        MenuForm form = new MenuForm(entry.display())
                .body(Component.text("Unit price: ", NamedTextColor.GRAY).append(money(entry.price())))
                .body(Component.text("You can afford ", NamedTextColor.GRAY)
                        .append(Component.text(max, NamedTextColor.WHITE)))
                .number("amount", Component.text("Amount"), 1, max, Math.min(64, max), 1)
                .presets(1, 16, 32, 64, 256)
                .confirm(Component.text("Buy", NamedTextColor.GREEN),
                        (p, values) -> buy(p, entry, values.intVal("amount", 1)))
                .cancel(BACK, p -> shopCategory(p, category));

        core.ui().open(player, form);
    }

    void buy(Player player, Shop.Entry entry, int amount) {
        if (amount < 1) return;
        double total = entry.price() * amount;
        Profile profile = core.profiles().get(player.getUniqueId());

        if (!core.money().take(player, total, profile)) {
            core.lang().send(player, "economy.insufficient",
                    Text.p("amount", Num.money(total - core.money().balance(player))));
            return;
        }

        int remaining = amount;
        int stackSize = entry.material().getMaxStackSize();
        List<ItemStack> stacks = new ArrayList<>((amount / stackSize) + 1);
        while (remaining > 0) {
            int size = Math.min(stackSize, remaining);
            stacks.add(new ItemStack(entry.material(), size));
            remaining -= size;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stacks.toArray(new ItemStack[0]));
        if (!leftover.isEmpty()) {
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
            core.lang().send(player, "shop.full-inventory");
        }

        boolean actionBar = core.cfg().buyActionBar && (profile == null || profile.flag(Profile.FLAG_BUY_ACTION));
        if (actionBar) {
            player.sendActionBar(core.lang().get("economy.actionbar-buy", Text.p("price", Num.money(total))));
        } else {
            core.lang().send(player, "economy.bought",
                    Text.p("amount", String.valueOf(amount)),
                    Text.p("item", Text.pretty(entry.material())),
                    Text.p("price", Num.money(total)));
        }
    }


    // ------------------------------------------------------------ quick buy

    /**
     * The Quick Buy panel: always a chest menu, on every client. Five rows of preset
     * slots, empty ones showing the "click to choose" placeholder.
     */
    public void quickBuy(Player player) {
        if (!core.cfg().quickBuyEnabled) return;
        if (!player.hasPermission(core.cfg().shopPermission)) {
            core.lang().send(player, "shop.locked");
            return;
        }
        QuickBuy store = core.quickBuy();
        QuickBuy.Slot[] slots = store.slots(player.getUniqueId());

        Menu menu = new Menu(Component.text("Quick Buy", NamedTextColor.DARK_GRAY))
                .body(Component.text("Balance: ", NamedTextColor.GRAY).append(money(core.money().balance(player))))
                .back(BACK, this::shop);

        for (int i = 0; i < slots.length; i++) {
            final int index = i;
            QuickBuy.Slot slot = slots[i];

            if (slot == null) {
                menu.add(MenuButton.of(
                        Component.text("Empty", NamedTextColor.WHITE),
                        List.of(core.lang().get("quickbuy.empty-hint")),
                        Material.LIGHT_GRAY_STAINED_GLASS_PANE, 1,
                        p -> quickBuyPicker(p, index, null)));
                continue;
            }

            Shop.Entry entry = core.shop().find(slot.item());
            if (entry == null) {
                // The shop stopped selling this — show it as stale rather than buying nothing.
                menu.add(MenuButton.of(
                        Component.text(Text.pretty(slot.item()), NamedTextColor.RED),
                        List.of(core.lang().get("quickbuy.not-sold")),
                        Material.BARRIER, 1,
                        p -> {
                            store.clear(p.getUniqueId(), index);
                            quickBuy(p);
                        }));
                continue;
            }

            double total = entry.price() * slot.amount();
            menu.add(MenuButton.of(
                    Component.text(Text.pretty(slot.item()), NamedTextColor.WHITE),
                    List.of(Component.text("~$ " + Num.compact(total), NamedTextColor.GREEN),
                            Component.text("Left-click to buy", NamedTextColor.DARK_GRAY),
                            Component.text("Right-click to clear", NamedTextColor.DARK_GRAY)),
                    slot.item(), Math.min(64, slot.amount()),
                    p -> buy(p, entry, slot.amount()),
                    p -> {
                        store.clear(p.getUniqueId(), index);
                        core.lang().send(p, "quickbuy.cleared", Text.p("slot", String.valueOf(index + 1)));
                        quickBuy(p);
                    }));
        }
        core.ui().openChest(player, menu);
    }

    /**
     * Choose Item: a dialog with a search box above a grid of buttons. Built from
     * shop.yml and nothing else, so a preset can never name something the server
     * doesn't sell.
     */
    public void quickBuyPicker(Player player, int index, String filter) {
        List<Shop.Entry> items = core.shop().allItems();
        String needle = filter == null ? null : filter.trim().toLowerCase(java.util.Locale.ROOT);
        boolean filtering = needle != null && !needle.isEmpty();

        Menu menu = new Menu(Component.text("Choose Item", NamedTextColor.WHITE))
                .columns(4)
                .textInput("query", Component.text("Search"), filter == null ? "" : filter, 32)
                .back(Component.text("Cancel", NamedTextColor.RED), p -> quickBuy(p));

        // First button, matching the vanilla picker layout: applies whatever is typed above.
        menu.add(MenuButton.submitting(
                Component.text("Search", NamedTextColor.WHITE),
                Component.text("Filter the list by name"),
                Material.COMPASS,
                p -> askSearch(p, index),
                (p, values) -> quickBuyPicker(p, index, values.text("query", ""))));

        int shown = 0;
        for (Shop.Entry entry : items) {
            String name = Text.pretty(entry.material());
            if (filtering && !name.toLowerCase(java.util.Locale.ROOT).contains(needle)) continue;
            shown++;
            menu.add(MenuButton.of(
                    Component.text(name, NamedTextColor.WHITE),
                    List.of(Component.text("$" + Num.money(entry.price()) + " each", NamedTextColor.GRAY)),
                    entry.material(), 1,
                    p -> quickBuyAmount(p, index, entry)));
        }

        menu.body(filtering
                ? Component.text(shown + " of " + items.size() + " items match \"" + filter + "\"",
                        NamedTextColor.GRAY)
                : Component.text(items.size() + " items in the server shop", NamedTextColor.GRAY));
        if (shown == 0) menu.body(Component.text("Nothing matched.", NamedTextColor.RED));

        core.ui().openDialog(player, menu);
    }

    /** Chest fallback for the picker's search button: ask in chat instead. */
    private void askSearch(Player player, int index) {
        player.closeInventory();
        core.ui().prompt().ask(player, input -> quickBuyPicker(player, index, input));
    }

    /**
     * "How many to buy?" — a dialog with a typed amount box, not a slider. Saves the
     * preset; it does not purchase.
     */
    public void quickBuyAmount(Player player, int index, Shop.Entry entry) {
        int max = core.cfg().quickBuyMaxPerPurchase;

        MenuForm form = new MenuForm(Component.text("How many to buy?", NamedTextColor.WHITE))
                .icon(new ItemStack(entry.material()))
                .body(Component.text("Max per purchase: ", NamedTextColor.GRAY)
                        .append(Component.text(max, NamedTextColor.WHITE)))
                .body(Component.text("$" + Num.money(entry.price()) + " each", NamedTextColor.DARK_GRAY))
                .text("amount", Component.text("Amount"), String.valueOf(Math.min(64, max)), 8)
                .presets(1, 8, 16, 32, 64)
                .confirm(Component.text("Add to Quick Buy", NamedTextColor.WHITE), (p, values) -> {
                    double parsed = Num.parse(values.text("amount", ""));
                    if (parsed < 1) {
                        core.lang().send(p, "number-invalid", Text.p("input", values.text("amount", "")));
                        quickBuyAmount(p, index, entry);
                        return;
                    }
                    int amount = (int) Math.min(max, Math.floor(parsed));
                    core.quickBuy().set(p.getUniqueId(), index, entry.material(), amount);
                    core.lang().send(p, "quickbuy.saved",
                            Text.p("amount", String.valueOf(amount)),
                            Text.p("item", Text.pretty(entry.material())),
                            Text.p("slot", String.valueOf(index + 1)));
                    quickBuy(p);
                })
                .cancel(Component.text("Cancel", NamedTextColor.RED), p -> quickBuyPicker(p, index, null));

        core.ui().openDialog(player, form);
    }

    // ----------------------------------------------------------- shard shop

    public void shardShop(Player player) {
        Profile profile = core.profiles().get(player.getUniqueId());
        long balance = profile == null ? 0 : profile.shards;

        Menu menu = new Menu(core.shardShop().title())
                .columns(core.cfg().menuColumns)
                .body(Component.text("You have ", NamedTextColor.GRAY).append(shardText(balance)));
        if (profile != null && profile.boosterActive()) {
            long left = (profile.boosterUntil - System.currentTimeMillis()) / 1000L;
            menu.body(core.lang().get("shards.booster-active", Text.p("time", Num.duration(left))));
        }

        for (ShardShop.Entry entry : core.shardShop().entries()) {
            List<Component> lore = new ArrayList<>(entry.lore());
            lore.add(Component.text(Num.compact(entry.price()) + " shards", NamedTextColor.LIGHT_PURPLE));
            menu.add(MenuButton.of(entry.display(), lore, entry.icon(), 1, p -> shardConfirm(p, entry)));
        }
        core.ui().open(player, menu);
    }

    public void shardConfirm(Player player, ShardShop.Entry entry) {
        MenuForm form = new MenuForm(entry.display())
                .body(Component.text("Cost: ", NamedTextColor.GRAY).append(shardText(entry.price())))
                .confirm(Component.text("Buy", NamedTextColor.GREEN), (p, values) -> buyShard(p, entry))
                .cancel(BACK, this::shardShop);
        for (Component line : entry.lore()) form.body(line);
        core.ui().open(player, form);
    }

    private void buyShard(Player player, ShardShop.Entry entry) {
        Profile profile = core.profiles().get(player.getUniqueId());
        if (profile == null) return;
        if (!core.shards().take(profile, entry.price())) {
            core.lang().send(player, "shards.insufficient",
                    Text.p("amount", String.valueOf(entry.price() - profile.shards)));
            return;
        }
        if (entry.kind() == ShardShop.Kind.BOOSTER) {
            core.shards().grantBooster(profile, entry.durationHours());
            core.lang().send(player, "shards.booster-active",
                    Text.p("time", Num.duration(entry.durationHours() * 3600L)));
            return;
        }
        ItemStack copy = entry.stack().clone();
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(copy);
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
    }

    // ---------------------------------------------------------------- worth

    public void worthBook(Player player) {
        Menu menu = new Menu(Component.text("Price Book", NamedTextColor.DARK_GRAY))
                .columns(core.cfg().menuColumns)
                .body(core.sell().multiplierLine(player));

        List<Map.Entry<Material, Double>> sorted = new ArrayList<>(core.worth().all().entrySet());
        sorted.sort(Comparator.comparingDouble((Map.Entry<Material, Double> e) -> e.getValue()).reversed());

        for (Map.Entry<Material, Double> e : sorted) {
            double sell = core.worth().sellPrice(e.getKey());
            menu.add(MenuButton.of(
                    Component.text(Text.pretty(e.getKey()), NamedTextColor.WHITE),
                    List.of(Component.text("Buy $" + Num.money(e.getValue()), NamedTextColor.GRAY),
                            Component.text("Sell $" + Num.money(sell), NamedTextColor.GREEN)),
                    e.getKey(), 1,
                    p -> {}));
        }
        core.ui().open(player, menu);
    }

    // ---------------------------------------------------------------- homes

    public void homes(Player player) {
        Map<String, Homes.Home> homes = core.homes().homes(player.getUniqueId());
        int slots = core.homes().slots(player);

        Menu menu = new Menu(Component.text("Homes", NamedTextColor.DARK_GRAY))
                .columns(core.cfg().menuColumns)
                .body(Component.text(homes.size() + " / " + slots + " slots used", NamedTextColor.GRAY));

        for (Homes.Home home : homes.values()) {
            menu.add(MenuButton.of(
                    Component.text(home.name(), NamedTextColor.WHITE),
                    List.of(Component.text(home.world(), NamedTextColor.GRAY),
                            Component.text("Click to teleport", NamedTextColor.DARK_GRAY)),
                    Material.RED_BED, 1,
                    p -> core.teleports().warp(p, home.toLocation(), core.cfg().homeWarmup)));
        }

        if (homes.size() < slots) {
            menu.add(MenuButton.of(
                    Component.text("Set a home here", NamedTextColor.GREEN),
                    Component.text("Names your current position"),
                    Material.LIME_DYE, this::setHomeForm));
        }
        if (!homes.isEmpty()) {
            menu.add(MenuButton.of(
                    Component.text("Delete a home", NamedTextColor.RED),
                    Component.text("Pick one to remove"),
                    Material.BARRIER, this::deleteHomeMenu));
        }
        core.ui().open(player, menu);
    }

    public void setHomeForm(Player player) {
        MenuForm form = new MenuForm(Component.text("Set Home", NamedTextColor.DARK_GRAY))
                .body(Component.text("1-16 letters, digits or underscores", NamedTextColor.GRAY))
                .text("name", Component.text("Home name"), "home", 16)
                .confirm(Component.text("Save", NamedTextColor.GREEN), (p, values) -> {
                    String name = values.text("name", "").trim();
                    core.commands().setHome(p, name);
                    homes(p);
                })
                .cancel(BACK, this::homes);
        core.ui().open(player, form);
    }

    public void deleteHomeMenu(Player player) {
        Menu menu = new Menu(Component.text("Delete Home", NamedTextColor.DARK_GRAY))
                .columns(core.cfg().menuColumns)
                .back(BACK, this::homes);

        for (Homes.Home home : core.homes().homes(player.getUniqueId()).values()) {
            menu.add(MenuButton.of(
                    Component.text(home.name(), NamedTextColor.RED),
                    Component.text("Click to confirm deletion"),
                    Material.BARRIER,
                    p -> confirmDeleteHome(p, home)));
        }
        core.ui().open(player, menu);
    }

    private void confirmDeleteHome(Player player, Homes.Home home) {
        MenuForm form = new MenuForm(Component.text("Delete " + home.name() + "?", NamedTextColor.RED))
                .body(Component.text("This can't be undone.", NamedTextColor.GRAY))
                .confirm(Component.text("Delete", NamedTextColor.RED), (p, values) -> {
                    if (core.homes().delete(p.getUniqueId(), home.name())) {
                        core.lang().send(p, "home.deleted", Text.p("name", home.name()));
                    }
                    homes(p);
                })
                .cancel(BACK, this::deleteHomeMenu);
        core.ui().open(player, form);
    }

    // ------------------------------------------------------------------ rtp

    public void rtp(Player player) {
        Menu menu = new Menu(Component.text("Random Teleport", NamedTextColor.DARK_GRAY))
                .columns(core.cfg().menuColumns);
        long cooldown = core.rtp().cooldownLeft(player);
        if (cooldown > 0) menu.body(Component.text("Cooldown: " + cooldown + "s", NamedTextColor.RED));

        for (Rtp.Region region : core.rtp().regions()) {
            menu.add(MenuButton.of(
                    Component.text(region.display(), NamedTextColor.WHITE),
                    List.of(Component.text(region.minRadius() + " - " + region.maxRadius() + " blocks out",
                            NamedTextColor.GRAY)),
                    region.icon(), 1,
                    p -> core.rtp().go(p, region)));
        }
        core.ui().open(player, menu);
    }

    // ------------------------------------------------------------------ tpa

    public void tpaPrompt(Player target, Player requester) {
        MenuForm form = new MenuForm(Component.text("Teleport Request", NamedTextColor.DARK_GRAY))
                .body(core.lang().get("tpa.received", Text.p("name", requester.getName())))
                .confirm(Component.text("Accept", NamedTextColor.GREEN), (p, values) -> core.commands().acceptTpa(p))
                .cancel(Component.text("Deny", NamedTextColor.RED), p -> {
                    core.tpa().take(p.getUniqueId());
                    core.lang().send(p, "tpa.denied");
                });
        core.ui().open(target, form);
    }

    // ------------------------------------------------------------- settings

    public void settings(Player player) {
        Profile profile = core.profiles().get(player.getUniqueId());
        if (profile == null) return;

        MenuForm form = new MenuForm(Component.text("Settings", NamedTextColor.DARK_GRAY))
                .body(Component.text("Changes save immediately.", NamedTextColor.GRAY))
                .bool("dialogs", Component.text("Use dialog menus"), profile.flag(Profile.FLAG_DIALOGS))
                .bool("tpa", Component.text("Accept teleport requests"), profile.flag(Profile.FLAG_TPA_ALLOWED))
                .bool("sellbar", Component.text("Sell totals on the action bar"), profile.flag(Profile.FLAG_SELL_ACTION))
                .bool("buybar", Component.text("Buy totals on the action bar"), profile.flag(Profile.FLAG_BUY_ACTION))
                .bool("stats", Component.text("Let others see my stats"), profile.flag(Profile.FLAG_PUBLIC_STATS))
                .confirm(Component.text("Save", NamedTextColor.GREEN), (p, values) -> {
                    profile.flag(Profile.FLAG_DIALOGS, values.bool("dialogs", true));
                    profile.flag(Profile.FLAG_TPA_ALLOWED, values.bool("tpa", true));
                    profile.flag(Profile.FLAG_SELL_ACTION, values.bool("sellbar", true));
                    profile.flag(Profile.FLAG_BUY_ACTION, values.bool("buybar", true));
                    profile.flag(Profile.FLAG_PUBLIC_STATS, values.bool("stats", true));
                    p.sendMessage(Component.text("Settings saved.", NamedTextColor.GREEN));
                })
                .cancel(CLOSE, p -> {});
        core.ui().open(player, form);
    }

    // ---------------------------------------------------------------- stats

    public void stats(Player viewer, OfflinePlayer target, Profile profile) {
        if (profile == null) {
            core.lang().send(viewer, "unknown-player", Text.p("name", String.valueOf(target.getName())));
            return;
        }
        if (!profile.flag(Profile.FLAG_PUBLIC_STATS)
                && !viewer.getUniqueId().equals(profile.uuid)
                && !viewer.hasPermission("dripleaf.stats.other")) {
            viewer.sendMessage(Component.text("That player's stats are private.", NamedTextColor.RED));
            return;
        }
        List<Component> lines = List.of(
                line("Money", "$" + Num.money(core.money().balance(target))),
                line("Shards", Num.compact(profile.shards)),
                line("Kills", String.valueOf(profile.kills)),
                line("Deaths", String.valueOf(profile.deaths)),
                line("K/D", String.format(java.util.Locale.US, "%.2f",
                        profile.deaths == 0 ? profile.kills : (double) profile.kills / profile.deaths)),
                line("Killstreak", profile.killStreak + " (best " + profile.bestStreak + ")"),
                line("Mobs killed", String.valueOf(profile.mobKills)),
                line("Blocks broken", Num.compact(profile.blocksBroken)),
                line("Blocks placed", Num.compact(profile.blocksPlaced)),
                line("Earned", "$" + Num.compact(profile.moneyMade)),
                line("Spent", "$" + Num.compact(profile.moneySpent)),
                line("Playtime", Num.duration(profile.totalPlaytime())));

        core.ui().notice(viewer,
                Component.text(profile.name == null ? "Stats" : profile.name, NamedTextColor.WHITE),
                lines, CLOSE, null);
    }

    private static Component line(String label, String value) {
        return Component.text(label + ": ", NamedTextColor.GRAY)
                .append(Component.text(value, NamedTextColor.WHITE));
    }

    // -------------------------------------------------------- sell history

    public void sellHistory(Player player) {
        core.sell().history(player.getUniqueId(), core.cfg().historySize).thenAccept(records ->
                Bukkit.getScheduler().runTask(core, () -> {
                    Menu menu = new Menu(Component.text("Sell History", NamedTextColor.DARK_GRAY))
                            .columns(1)
                            .body(core.sell().multiplierLine(player));
                    if (records.isEmpty()) {
                        menu.body(Component.text("Nothing sold yet.", NamedTextColor.GRAY));
                    }
                    for (Sell.Sale sale : records) {
                        long ago = (System.currentTimeMillis() - sale.timestamp()) / 1000L;
                        menu.add(MenuButton.of(
                                Component.text("$" + Num.money(sale.total()), NamedTextColor.GREEN),
                                List.of(Component.text(sale.summary(), NamedTextColor.GRAY),
                                        Component.text(Num.duration(ago) + " ago", NamedTextColor.DARK_GRAY)),
                                Material.CHEST, 1, p -> {}));
                    }
                    core.ui().open(player, menu);
                }));
    }

    // -------------------------------------------------------------- bounty

    public void bounties(Player player) {
        Menu menu = new Menu(Component.text("Bounties", NamedTextColor.DARK_GRAY))
                .columns(core.cfg().menuColumns);
        List<Bounties.Bounty> all = core.bounties().all();
        if (all.isEmpty()) menu.body(core.lang().get("bounty.none"));

        for (Bounties.Bounty bounty : all) {
            menu.add(MenuButton.of(
                    Component.text(bounty.targetName(), NamedTextColor.GOLD),
                    List.of(Component.text("$" + Num.money(bounty.amount()), NamedTextColor.WHITE),
                            Component.text("Placed by " + bounty.placedBy(), NamedTextColor.GRAY)),
                    Material.PLAYER_HEAD, 1, p -> {}));
        }
        menu.add(MenuButton.of(
                Component.text("Place a bounty", NamedTextColor.GREEN),
                Component.text("Pick an online player"),
                Material.GOLD_INGOT, this::bountyTargets));
        core.ui().open(player, menu);
    }

    public void bountyTargets(Player player) {
        Menu menu = new Menu(Component.text("Place a Bounty", NamedTextColor.DARK_GRAY))
                .columns(core.cfg().menuColumns)
                .back(BACK, this::bounties);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(player)) continue;
            menu.add(MenuButton.of(
                    Component.text(online.getName(), NamedTextColor.WHITE),
                    Component.text("Click to set an amount"),
                    Material.PLAYER_HEAD,
                    p -> bountyAmount(p, online)));
        }
        core.ui().open(player, menu);
    }

    public void bountyAmount(Player player, Player target) {
        double balance = core.money().balance(player);
        double max = Math.min(core.cfg().bountyMax, balance);
        if (max < core.cfg().bountyMin) {
            core.lang().send(player, "bounty.too-small", Text.p("amount", Num.money(core.cfg().bountyMin)));
            return;
        }
        MenuForm form = new MenuForm(Component.text("Bounty on " + target.getName(), NamedTextColor.GOLD))
                .body(Component.text("Your balance: ", NamedTextColor.GRAY).append(money(balance)))
                .body(Component.text((int) (core.cfg().bountyTax * 100) + "% is burned on payout.",
                        NamedTextColor.DARK_GRAY))
                .number("amount", Component.text("Amount"),
                        (float) core.cfg().bountyMin, (float) max, (float) core.cfg().bountyMin, 100)
                .presets(1000, 5000, 25000, 100000)
                .confirm(Component.text("Place", NamedTextColor.GOLD),
                        (p, values) -> core.commands().placeBounty(p, target, values.number("amount", 0)))
                .cancel(BACK, this::bountyTargets);
        core.ui().open(player, form);
    }

    // -------------------------------------------------------- leaderboards

    public void leaderboards(Player player) {
        Menu menu = new Menu(Component.text("Leaderboards", NamedTextColor.DARK_GRAY))
                .columns(core.cfg().menuColumns);
        menu.add(MenuButton.of(Component.text("Shards", NamedTextColor.LIGHT_PURPLE), Material.AMETHYST_SHARD,
                p -> board(p, "shards", "Shards", false)));
        menu.add(MenuButton.of(Component.text("Kills", NamedTextColor.RED), Material.DIAMOND_SWORD,
                p -> board(p, "kills", "Kills", false)));
        menu.add(MenuButton.of(Component.text("Playtime", NamedTextColor.YELLOW), Material.CLOCK,
                p -> board(p, "playtime", "Playtime", true)));
        menu.add(MenuButton.of(Component.text("Blocks broken", NamedTextColor.AQUA), Material.IRON_PICKAXE,
                p -> board(p, "blocks_broken", "Blocks broken", false)));
        menu.add(MenuButton.of(Component.text("Money earned", NamedTextColor.GREEN), Material.EMERALD,
                p -> board(p, "money_made", "Money earned", false)));
        core.ui().open(player, menu);
    }

    private void board(Player player, String column, String title, boolean asDuration) {
        core.profiles().top(column, core.cfg().boardSize).thenAccept(rows ->
                Bukkit.getScheduler().runTask(core, () -> {
                    List<Component> lines = new ArrayList<>(rows.size());
                    int rank = 1;
                    for (Map.Entry<String, Double> row : rows.entrySet()) {
                        String value = asDuration
                                ? Num.duration(row.getValue().longValue())
                                : Num.compact(row.getValue());
                        lines.add(Component.text("#" + rank++ + " ", NamedTextColor.DARK_GRAY)
                                .append(Component.text(row.getKey() + " ", NamedTextColor.WHITE))
                                .append(Component.text(value, NamedTextColor.GREEN)));
                    }
                    if (lines.isEmpty()) lines = List.of(Component.text("No data yet.", NamedTextColor.GRAY));
                    core.ui().notice(player, Component.text(title, NamedTextColor.WHITE), lines, BACK,
                            this::leaderboards);
                }));
    }

    // --------------------------------------------------------- rules / help

    public void rules(Player player) {
        List<Component> lines = new ArrayList<>();
        for (String raw : core.getConfig().getStringList("rules")) lines.add(Text.mm(raw));
        if (lines.isEmpty()) lines = List.of(Component.text("No rules configured.", NamedTextColor.GRAY));
        core.ui().notice(player, Component.text("Server Rules", NamedTextColor.WHITE), lines, CLOSE, null);
    }

    public void help(Player player) {
        Menu menu = new Menu(Component.text("DripleafMC", NamedTextColor.DARK_GRAY))
                .columns(core.cfg().menuColumns);
        menu.add(MenuButton.of(Component.text("Shop", NamedTextColor.GREEN), Material.EMERALD, this::shop));
        menu.add(MenuButton.of(Component.text("Sell items", NamedTextColor.GREEN), Material.CHEST,
                p -> core.sell().openChest(p)));
        menu.add(MenuButton.of(Component.text("Quick Buy", NamedTextColor.YELLOW), Material.HOPPER, this::quickBuy));
        menu.add(MenuButton.of(Component.text("Price book", NamedTextColor.WHITE), Material.BOOK, this::worthBook));
        menu.add(MenuButton.of(Component.text("Shard shop", NamedTextColor.LIGHT_PURPLE),
                Material.AMETHYST_SHARD, this::shardShop));
        menu.add(MenuButton.of(Component.text("Homes", NamedTextColor.WHITE), Material.RED_BED, this::homes));
        menu.add(MenuButton.of(Component.text("Random teleport", NamedTextColor.WHITE),
                Material.ENDER_PEARL, this::rtp));
        menu.add(MenuButton.of(Component.text("Bounties", NamedTextColor.GOLD), Material.GOLD_INGOT, this::bounties));
        menu.add(MenuButton.of(Component.text("Leaderboards", NamedTextColor.YELLOW),
                Material.PLAYER_HEAD, this::leaderboards));
        menu.add(MenuButton.of(Component.text("Your stats", NamedTextColor.WHITE), Material.PAPER,
                p -> stats(p, p, core.profiles().get(p.getUniqueId()))));
        menu.add(MenuButton.of(Component.text("Settings", NamedTextColor.GRAY), Material.COMPARATOR, this::settings));
        menu.add(MenuButton.of(Component.text("Rules", NamedTextColor.GRAY), Material.WRITABLE_BOOK, this::rules));
        core.ui().open(player, menu);
    }
}
