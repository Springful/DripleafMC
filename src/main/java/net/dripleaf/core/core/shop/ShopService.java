package net.dripleaf.core.core.shop;

import net.dripleaf.core.api.CurrencyService;
import net.dripleaf.core.api.CurrencyType;
import net.dripleaf.core.common.Services;
import net.dripleaf.core.common.sound.SoundService;
import net.dripleaf.core.common.text.Ctx;
import net.dripleaf.core.common.text.Glyphs;
import net.dripleaf.core.common.text.Palette;
import net.dripleaf.core.common.ui.ButtonStyle;
import net.dripleaf.core.common.ui.ChestLayout;
import net.dripleaf.core.common.ui.Screen;
import net.dripleaf.core.common.ui.ScreenButton;
import net.dripleaf.core.common.ui.ScreenInput;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One engine, three shops.
 *
 * <p>Every screen here is built as a {@link Screen} and handed to the UI
 * router, so the whole shop is a dialog for a Java player and a chest GUI for a
 * Bedrock one, from the same code. Search is a text field in a dialog and a
 * chat prompt in a chest; neither the shop nor its config knows the difference.
 *
 * <p>The purchase flow is deliberately paranoid, in this order:
 * <ol>
 *   <li>a confirmation showing <em>exact</em> figures and the resulting balance,
 *       never an abbreviated one;</li>
 *   <li>a fresh server-side balance check at confirm time — the check at
 *       menu-open time is not the check that matters;</li>
 *   <li>an inventory space check <em>before</em> the transaction, refusing
 *       rather than dropping items on the ground;</li>
 *   <li>withdraw, give, log, sound, message.</li>
 * </ol>
 */
public final class ShopService {

    private final Services services;
    private final Map<String, ShopDefinition> shops = new LinkedHashMap<>(3);
    private QuickBuyService quickBuy;

    public ShopService(Services services) {
        this.services = services;
    }

    /** Set once during module enable; the two services are mutually recursive. */
    public void quickBuy(QuickBuyService quickBuy) {
        this.quickBuy = quickBuy;
    }

    /** Reparses all three shop files. Never partially applies: each shop is atomic. */
    public void load() {
        shops.clear();
        register("server", "core/shops/server-shop.yml");
        register("shard", "core/shops/shard-shop.yml");
        register("soul", "core/shops/soul-shop.yml");
    }

    private void register(String id, String resource) {
        ShopDefinition definition = new ShopDefinition(id, resource);
        services.configs().load(resource);
        definition.load(services.configs().view(resource), services.amounts(),
                services.configs().log());
        shops.put(id, definition);

        for (String warning : definition.warnings()) {
            services.plugin().getLogger().warning('[' + resource + "] " + warning);
        }
    }

    public ShopDefinition shop(String id) {
        return shops.get(id);
    }

    public java.util.Collection<ShopDefinition> shops() {
        return shops.values();
    }

    /** Every material any shop references — fed to the icon validator at start-up. */
    public List<Material> referencedMaterials() {
        List<Material> out = new ArrayList<>(256);
        for (ShopDefinition shop : shops.values()) {
            for (ShopCategory category : shop.categories()) {
                out.add(category.icon());
            }
            for (ShopItem item : shop.allItems()) {
                out.add(item.material());
            }
        }
        return out;
    }

    // --------------------------------------------------------------- screens

    public void openRoot(Player player, String shopId) {
        ShopDefinition shop = shops.get(shopId);
        if (shop == null) {
            services.messages().send(player, "shop.unknown");
            return;
        }
        if (!shop.permission().isBlank() && !player.hasPermission(shop.permission())) {
            services.messages().send(player, "errors.no-permission",
                    Ctx.of("permission", shop.permission()));
            return;
        }
        CurrencyService currency = services.currencies().get(shop.currency());
        if (currency == null || !currency.available()) {
            services.messages().send(player, "shop.currency-unavailable",
                    Ctx.of("currency", shop.currency().id()));
            return;
        }

        Screen.Builder builder = Screen.of("shop-" + shopId, shop.title());
        if (shop.showBalance()) {
            builder.line(line("shop.balance-line", new Ctx()
                    .put("currency", shop.currency().id())
                    .put("balance", services.amounts().formatExact(shop.currency(),
                            currency.balance(player)))));
        }
        builder.line(line("shop.item-count", Ctx.of("count",
                String.valueOf(shop.itemCount()))));
        builder.blank();

        for (ShopCategory category : shop.categories()) {
            List<ShopItem> contents = shop.itemsIn(category.key());
            if (contents.isEmpty()) {
                continue;
            }
            ScreenButton.Builder button = ScreenButton.of("cat-" + category.key(),
                    Palette.brand(category.display()))
                    .style(ButtonStyle.PRIMARY)
                    .material(category.icon())
                    .action(clicker -> openCategory(clicker, shopId, category.key()));
            for (String description : category.description()) {
                button.line(Palette.colour(Palette.MUTED, Glyphs.ITEM + ' ' + description));
            }
            button.line(line("shop.category-count",
                    Ctx.of("count", String.valueOf(contents.size()))));
            builder.button(button.build());
        }

        if (shop.quickBuyEnabled() && quickBuy != null) {
            builder.button(ScreenButton.of("quick-buy",
                            Palette.brand(services.messages().raw("shop.quick-buy")))
                    .style(ButtonStyle.NEUTRAL)
                    .material(Material.HOPPER)
                    .line(services.messages().raw("shop.quick-buy-hint"))
                    .line(applied("shop.quick-buy-count", new Ctx()
                            .put("used", quickBuy.used(player, shopId))
                            .put("slots", shop.quickBuySlots())))
                    .action(clicker -> quickBuy.open(clicker, shopId))
                    .build());
        }
        if (shop.searchEnabled()) {
            builder.button(ScreenButton.of("search",
                            Palette.brand(services.messages().raw("shop.search")))
                    .style(ButtonStyle.NEUTRAL)
                    .material(Material.SPYGLASS)
                    .action(clicker -> openSearch(clicker, shopId))
                    .build());
        }

        services.sounds().play(player, SoundService.MENU_OPEN);
        services.ui().open(player, builder.build());
    }

    public void openCategory(Player player, String shopId, String categoryKey) {
        ShopDefinition shop = shops.get(shopId);
        if (shop == null) {
            return;
        }
        ShopCategory category = shop.category(categoryKey);
        if (category == null) {
            openRoot(player, shopId);
            return;
        }
        List<ShopItem> contents = shop.itemsIn(categoryKey);
        Screen.Builder builder = Screen.of("shop-" + shopId + '-' + categoryKey,
                        Palette.brand(category.display()))
                .line(line("shop.item-count", Ctx.of("count", String.valueOf(contents.size()))))
                .blank()
                .layout(ChestLayout.chest(6));

        for (ShopItem item : contents) {
            builder.button(itemButton(player, shop, item));
        }
        builder.button(backButton("shop-back", clicker -> openRoot(clicker, shopId)));

        services.sounds().play(player, SoundService.MENU_OPEN);
        services.ui().open(player, builder.build());
    }

    private void openSearch(Player player, String shopId) {
        ShopDefinition shop = shops.get(shopId);
        if (shop == null) {
            return;
        }
        Screen screen = Screen.of("shop-" + shopId + "-search",
                        Palette.brand(services.messages().raw("shop.search")))
                .line(services.messages().raw("shop.search-hint"))
                .input(ScreenInput.text("query", services.messages().raw("shop.search-label"),
                        services.messages().raw("shop.search-placeholder")))
                .onSubmit(services.messages().raw("shop.search"), (clicker, values) -> {
                    String query = values.getOrDefault("query", "").trim();
                    if (query.isEmpty()) {
                        openRoot(clicker, shopId);
                        return;
                    }
                    openResults(clicker, shopId, query);
                })
                .button(backButton("shop-back", clicker -> openRoot(clicker, shopId)))
                .build();
        services.ui().open(player, screen);
    }

    private void openResults(Player player, String shopId, String query) {
        ShopDefinition shop = shops.get(shopId);
        if (shop == null) {
            return;
        }
        List<ShopItem> results = shop.search(query);
        Screen.Builder builder = Screen.of("shop-" + shopId + "-results",
                        Palette.brand(services.messages().raw("shop.results-title")))
                .line(line("shop.results-count", new Ctx()
                        .put("query", query)
                        .put("count", String.valueOf(results.size()))))
                .blank()
                .layout(ChestLayout.chest(6));

        if (results.isEmpty()) {
            builder.line(line("shop.no-results", Ctx.of("query", query)));
        }
        for (ShopItem item : results) {
            builder.button(itemButton(player, shop, item));
        }
        builder.button(backButton("shop-back", clicker -> openRoot(clicker, shopId)));
        services.ui().open(player, builder.build());
    }

    private ScreenButton itemButton(Player player, ShopDefinition shop, ShopItem item) {
        boolean visible = item.permission().isBlank() || player.hasPermission(item.permission());
        ScreenButton.Builder button = ScreenButton.of("item-" + item.key(),
                        Palette.brand(item.display()))
                .material(item.material())
                .style(ButtonStyle.PRIMARY);

        if (item.buyable()) {
            button.line(line("shop.buy-line", Ctx.of("price",
                    services.amounts().formatExact(shop.currency(), item.buy()))));
        }
        if (item.sellable()) {
            button.line(line("shop.sell-line", Ctx.of("price",
                    services.amounts().formatExact(shop.currency(),
                            sellPrice(player, shop, item, 1)))));
        }
        for (String lore : item.lore()) {
            button.line(Palette.colour(Palette.MUTED, Glyphs.ITEM + ' ' + lore));
        }

        if (!visible) {
            button.locked(services.messages().raw("shop.item-locked"));
        } else {
            button.action(clicker -> openItem(clicker, shop.id(), item.key()));
        }
        return button.build();
    }

    public void openItem(Player player, String shopId, String itemKey) {
        ShopDefinition shop = shops.get(shopId);
        if (shop == null) {
            return;
        }
        ShopItem item = shop.item(itemKey);
        if (item == null) {
            openRoot(player, shopId);
            return;
        }

        Screen.Builder builder = Screen.of("shop-item-" + itemKey,
                        Palette.brand(item.display()))
                .hero(new ItemStack(item.material()))
                .line(line("shop.balance-line", new Ctx()
                        .put("currency", shop.currency().id())
                        .put("balance", services.amounts().formatExact(shop.currency(),
                                balance(player, shop)))))
                .blank();

        if (item.buyable()) {
            for (int quantity : item.stackSizes()) {
                BigDecimal total = item.buy().multiply(BigDecimal.valueOf(quantity));
                builder.button(ScreenButton.of("buy-" + quantity, line("shop.buy-button",
                                new Ctx().put("amount", String.valueOf(quantity))
                                        .put("price", services.amounts()
                                                .formatExact(shop.currency(), total))))
                        .style(ButtonStyle.PRIMARY)
                        .material(item.material())
                        .amount(Math.min(64, quantity))
                        .action(clicker -> confirmBuy(clicker, shopId, itemKey, quantity))
                        .build());
            }
        }
        if (item.sellable()) {
            int held = countHeld(player, item.material());
            BigDecimal unit = sellPrice(player, shop, item, 1);
            ScreenButton.Builder sell = ScreenButton.of("sell-all", line("shop.sell-button",
                            new Ctx().put("amount", String.valueOf(held))
                                    .put("price", services.amounts().formatExact(shop.currency(),
                                            unit.multiply(BigDecimal.valueOf(held))))))
                    .style(ButtonStyle.SHARD)
                    .material(Material.HOPPER);
            if (held <= 0) {
                sell.locked(services.messages().raw("shop.nothing-to-sell"));
            } else {
                sell.action(clicker -> confirmSell(clicker, shopId, itemKey, held));
            }
            builder.button(sell.build());
        }

        builder.button(backButton("shop-back",
                clicker -> openCategory(clicker, shopId, item.category())));
        services.ui().open(player, builder.build());
    }

    // -------------------------------------------------------- purchase flow

    private void confirmBuy(Player player, String shopId, String itemKey, int quantity) {
        confirmBuy(player, shopId, itemKey, quantity,
                clicker -> openItem(clicker, shopId, itemKey));
    }

    /**
     * @param returnTo where Cancel and a completed purchase send the player.
     *                 Quick Buy passes its own panel so a shortcut purchase
     *                 does not dump the player into the item screen.
     */
    public void confirmBuy(Player player, String shopId, String itemKey, int quantity,
                           java.util.function.Consumer<Player> returnTo) {
        ShopDefinition shop = shops.get(shopId);
        ShopItem item = shop == null ? null : shop.item(itemKey);
        if (item == null || !item.buyable()) {
            return;
        }
        BigDecimal total = item.buy().multiply(BigDecimal.valueOf(quantity));
        BigDecimal current = balance(player, shop);

        Screen screen = Screen.of("shop-confirm-buy",
                        Palette.brand(services.messages().raw("shop.confirm-title")))
                .hero(new ItemStack(item.material(), Math.min(64, quantity)))
                .line(line("shop.confirm-item", new Ctx()
                        .put("item", item.display())
                        .put("amount", String.valueOf(quantity))))
                .line(line("shop.confirm-unit", Ctx.of("price",
                        services.amounts().formatExact(shop.currency(), item.buy()))))
                .line(line("shop.confirm-total", Ctx.of("price",
                        services.amounts().formatExact(shop.currency(), total))))
                .line(line("shop.confirm-after", Ctx.of("balance",
                        services.amounts().formatExact(shop.currency(),
                                current.subtract(total)))))
                .button(ScreenButton.of("confirm",
                                services.messages().raw("shop.confirm-buy"))
                        .style(ButtonStyle.PRIMARY)
                        .material(Material.LIME_DYE)
                        .action(clicker -> buy(clicker, shopId, itemKey, quantity, returnTo))
                        .build())
                .button(backButton("cancel", returnTo))
                .build();
        services.ui().open(player, screen);
    }

    private void buy(Player player, String shopId, String itemKey, int quantity) {
        buy(player, shopId, itemKey, quantity, clicker -> openItem(clicker, shopId, itemKey));
    }

    public void buy(Player player, String shopId, String itemKey, int quantity,
                    java.util.function.Consumer<Player> returnTo) {
        ShopDefinition shop = shops.get(shopId);
        ShopItem item = shop == null ? null : shop.item(itemKey);
        if (item == null || !item.buyable()) {
            return;
        }
        CurrencyService currency = services.currencies().get(shop.currency());
        BigDecimal total = item.buy().multiply(BigDecimal.valueOf(quantity));

        // Re-validated here, not at menu-open time. This is the check that matters.
        if (!currency.has(player, total)) {
            deny(player, "shop.insufficient-funds", new Ctx()
                    .put("price", services.amounts().formatExact(shop.currency(), total))
                    .put("balance", services.amounts().formatExact(shop.currency(),
                            currency.balance(player))));
            return;
        }
        // Space is checked before the transaction, never after: a full inventory
        // must refuse the purchase, not drop the goods on the floor.
        if (item.giveItem() && !hasSpaceFor(player, item.material(), quantity)) {
            deny(player, "shop.inventory-full", new Ctx());
            return;
        }
        if (!currency.withdraw(player, total)) {
            deny(player, "shop.transaction-failed", new Ctx());
            return;
        }

        if (item.giveItem() && item.material() != Material.AIR) {
            player.getInventory().addItem(new ItemStack(item.material(), quantity));
        }
        runCommands(player, item, quantity);

        BigDecimal after = currency.balance(player);
        services.audit().transaction(String.format(
                "BUY  %s (%s) %s x%d unit=%s total=%s balance=%s shop=%s",
                player.getName(), player.getUniqueId(), item.key(), quantity,
                item.buy().toPlainString(), total.toPlainString(), after.toPlainString(),
                shop.id()));

        services.sounds().play(player, SoundService.PURCHASE_SUCCESS);
        services.messages().send(player, "shop.bought", new Ctx()
                .put("amount", String.valueOf(quantity))
                .put("item", item.display())
                .put("price", services.amounts().formatExact(shop.currency(), total))
                .put("balance", services.amounts().formatExact(shop.currency(), after)));
        returnTo.accept(player);
    }

    private void confirmSell(Player player, String shopId, String itemKey, int quantity) {
        ShopDefinition shop = shops.get(shopId);
        ShopItem item = shop == null ? null : shop.item(itemKey);
        if (item == null || !item.sellable()) {
            return;
        }
        BigDecimal total = sellPrice(player, shop, item, quantity);
        Screen screen = Screen.of("shop-confirm-sell",
                        Palette.brand(services.messages().raw("shop.confirm-sell-title")))
                .hero(new ItemStack(item.material(), Math.min(64, quantity)))
                .line(line("shop.confirm-item", new Ctx()
                        .put("item", item.display())
                        .put("amount", String.valueOf(quantity))))
                .line(line("shop.confirm-total", Ctx.of("price",
                        services.amounts().formatExact(shop.currency(), total))))
                .line(line("shop.confirm-after", Ctx.of("balance",
                        services.amounts().formatExact(shop.currency(),
                                balance(player, shop).add(total)))))
                .button(ScreenButton.of("confirm",
                                services.messages().raw("shop.confirm-sell"))
                        .style(ButtonStyle.SHARD)
                        .material(Material.LIME_DYE)
                        .action(clicker -> sell(clicker, shopId, itemKey, quantity))
                        .build())
                .button(backButton("cancel", clicker -> openItem(clicker, shopId, itemKey)))
                .build();
        services.ui().open(player, screen);
    }

    private void sell(Player player, String shopId, String itemKey, int quantity) {
        ShopDefinition shop = shops.get(shopId);
        ShopItem item = shop == null ? null : shop.item(itemKey);
        if (item == null || !item.sellable()) {
            return;
        }
        int held = countHeld(player, item.material());
        int selling = Math.min(held, quantity);
        if (selling <= 0) {
            deny(player, "shop.nothing-to-sell", new Ctx());
            return;
        }

        BigDecimal total = sellPrice(player, shop, item, selling);
        player.getInventory().removeItem(new ItemStack(item.material(), selling));

        CurrencyService currency = services.currencies().get(shop.currency());
        currency.deposit(player, total);
        BigDecimal after = currency.balance(player);

        services.audit().transaction(String.format(
                "SELL %s (%s) %s x%d total=%s balance=%s shop=%s",
                player.getName(), player.getUniqueId(), item.key(), selling,
                total.toPlainString(), after.toPlainString(), shop.id()));

        services.sounds().play(player, SoundService.PURCHASE_SUCCESS);
        services.messages().send(player, "shop.sold", new Ctx()
                .put("amount", String.valueOf(selling))
                .put("item", item.display())
                .put("price", services.amounts().formatExact(shop.currency(), total))
                .put("balance", services.amounts().formatExact(shop.currency(), after)));
        openItem(player, shopId, itemKey);
    }

    // --------------------------------------------------------------- pricing

    /**
     * Unit sell price times quantity, with the shop's global multiplier and the
     * player's rebirth multiplier stacked on top — in that order, per §12.4.
     */
    public BigDecimal sellPrice(Player player, ShopDefinition shop, ShopItem item, int quantity) {
        if (!item.sellable()) {
            return BigDecimal.ZERO;
        }
        double multiplier = shop.globalSellMultiplier();
        if (services.rebirth() != null) {
            multiplier *= services.rebirth().sellMultiplier(player);
        }
        return item.sell()
                .multiply(BigDecimal.valueOf(quantity))
                .multiply(BigDecimal.valueOf(multiplier))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** The best sell price for one item across every shop the player may use. */
    public BigDecimal worth(Player player, Material material, int quantity) {
        BigDecimal best = BigDecimal.ZERO;
        for (ShopDefinition shop : shops.values()) {
            if (shop.currency() != CurrencyType.MONEY) {
                continue;
            }
            for (ShopItem item : shop.allItems()) {
                if (item.material() == material && item.sellable()) {
                    best = best.max(sellPrice(player, shop, item, quantity));
                }
            }
        }
        return best;
    }

    /** Finds the money-priced sell entry for a material, or {@code null}. */
    public ShopItem sellableFor(Material material) {
        for (ShopDefinition shop : shops.values()) {
            if (shop.currency() != CurrencyType.MONEY) {
                continue;
            }
            for (ShopItem item : shop.allItems()) {
                if (item.material() == material && item.sellable()) {
                    return item;
                }
            }
        }
        return null;
    }

    public ShopDefinition serverShop() {
        return shops.get("server");
    }

    // ---------------------------------------------------------------- helpers

    private BigDecimal balance(Player player, ShopDefinition shop) {
        CurrencyService currency = services.currencies().get(shop.currency());
        return currency == null ? BigDecimal.ZERO : currency.balance(player);
    }

    private void runCommands(Player player, ShopItem item, int quantity) {
        if (item.commands().isEmpty()) {
            return;
        }
        Ctx ctx = new Ctx()
                .put("player", player.getName())
                .put("amount", String.valueOf(quantity))
                .put("item", item.key());
        for (String command : item.commands()) {
            org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(),
                    ctx.applyRaw(command));
        }
    }

    private void deny(Player player, String key, Ctx ctx) {
        services.sounds().play(player, SoundService.PURCHASE_FAILURE);
        services.messages().send(player, key, ctx);
    }

    private String line(String key, Ctx ctx) {
        return ctx.applyRaw(services.messages().raw(key));
    }

    private String applied(String key, Ctx ctx) {
        return ctx.applyRaw(services.messages().raw(key));
    }

    private ScreenButton backButton(String id, java.util.function.Consumer<Player> action) {
        return ScreenButton.of(id, services.messages().raw("ui.back"))
                .style(ButtonStyle.NEUTRAL)
                .material(Material.ARROW)
                .action(action)
                .build();
    }

    public static int countHeld(Player player, Material material) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && stack.getType() == material) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    /** True when {@code quantity} of {@code material} fits without dropping anything. */
    public static boolean hasSpaceFor(Player player, Material material, int quantity) {
        if (material == Material.AIR) {
            return true;
        }
        int space = 0;
        int max = material.getMaxStackSize();
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType() == Material.AIR) {
                space += max;
            } else if (stack.getType() == material) {
                space += Math.max(0, max - stack.getAmount());
            }
            if (space >= quantity) {
                return true;
            }
        }
        return space >= quantity;
    }
}
