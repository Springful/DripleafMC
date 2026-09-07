package net.dripleaf.core.core.shop;

import net.dripleaf.core.api.CurrencyService;
import net.dripleaf.core.common.Services;
import net.dripleaf.core.common.sound.SoundService;
import net.dripleaf.core.common.storage.PlayerData;
import net.dripleaf.core.common.text.Ctx;
import net.dripleaf.core.common.ui.ButtonTemplate;
import net.dripleaf.core.common.ui.MenuTemplate;
import net.dripleaf.core.common.ui.Screen;
import net.dripleaf.core.common.ui.ScreenButton;
import net.dripleaf.core.common.ui.ScreenInput;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Quick Buy: a player's own saved shortcuts into a shop.
 *
 * <p>Each player gets a grid of slots per shop. An empty slot opens a picker
 * listing every item the shop sells; picking one and typing a quantity binds
 * that item and amount to the slot <em>permanently</em>, until the player
 * removes it. A filled slot buys its binding.
 *
 * <p>This is deliberately not a search box. Search already exists as its own
 * entry — Quick Buy is the thing you set up once and then use without reading
 * anything, which is the whole reason it is worth a slot on the shop's front
 * screen.
 *
 * <p>Bindings live in player data as {@code "<shop>/<slot>" -> "<item>:<amount>"}.
 *
 * <p><b>Manage mode.</b> A chest GUI could distinguish left-click from
 * shift-click, but a dialog cannot, and every screen here has to work both
 * ways. So instead of overloading the click, the panel has a Manage toggle:
 * off, a slot buys; on, a slot opens change/remove. Identical behaviour on
 * both surfaces, and discoverable on neither-mouse Bedrock.
 */
public final class QuickBuyService {

    private final Services services;
    private final ShopService shops;

    public QuickBuyService(Services services, ShopService shops) {
        this.services = services;
        this.shops = shops;
    }

    // ------------------------------------------------------------- bindings

    /** @param amount how many are bought in one click */
    public record Binding(String itemKey, int amount) {

        static Binding parse(String raw) {
            int colon = raw.lastIndexOf(':');
            if (colon <= 0) {
                return null;
            }
            try {
                return new Binding(raw.substring(0, colon),
                        Integer.parseInt(raw.substring(colon + 1)));
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        String serialise() {
            return itemKey + ':' + amount;
        }
    }

    private static String key(String shopId, int slot) {
        return shopId + '/' + slot;
    }

    public Binding binding(Player player, String shopId, int slot) {
        String raw = services.players().get(player).quickBuys().get(key(shopId, slot));
        return raw == null ? null : Binding.parse(raw);
    }

    public void bind(Player player, String shopId, int slot, String itemKey, int amount) {
        services.players().get(player)
                .quickBuy(key(shopId, slot), new Binding(itemKey, amount).serialise());
    }

    public void unbind(Player player, String shopId, int slot) {
        services.players().get(player).quickBuy(key(shopId, slot), null);
    }

    public int used(Player player, String shopId) {
        PlayerData data = services.players().get(player);
        int count = 0;
        for (String stored : data.quickBuys().keySet()) {
            if (stored.startsWith(shopId + '/')) {
                count++;
            }
        }
        return count;
    }

    // --------------------------------------------------------------- panel

    public void open(Player player, String shopId) {
        open(player, shopId, false);
    }

    private void open(Player player, String shopId, boolean manage) {
        ShopDefinition shop = shops.shop(shopId);
        if (shop == null) {
            return;
        }
        MenuTemplate menu = services.menus().template("quick-buy");
        int slots = shop.quickBuySlots();

        Ctx ctx = new Ctx()
                .put("shop", shop.title())
                .put("used", used(player, shopId))
                .put("slots", slots)
                .put("balance", services.amounts()
                        .formatExact(shop.currency(), balance(player, shop)));

        Screen.Builder builder = menu.screen(ctx);

        for (int slot = 0; slot < slots; slot++) {
            builder.button(slotButton(player, shop, menu, slot, manage));
        }

        // The toggle, not an overloaded click: dialogs have no shift-click.
        ButtonTemplate toggle = menu.button(manage ? "manage-on" : "manage-off");
        builder.button(toggle.build(ctx, clicker -> open(clicker, shopId, !manage)));
        builder.button(menu.button("back")
                .build(ctx, clicker -> shops.openRoot(clicker, shopId)));

        services.sounds().play(player, SoundService.MENU_OPEN);
        services.ui().open(player, builder.build());
    }

    private ScreenButton slotButton(Player player, ShopDefinition shop, MenuTemplate menu,
                                    int slot, boolean manage) {
        String shopId = shop.id();
        Binding binding = binding(player, shopId, slot);
        ShopItem item = binding == null ? null : shop.item(binding.itemKey());

        // A binding whose item was deleted from the shop config: show it as
        // broken rather than silently dropping the player's saved slot.
        if (binding != null && item == null) {
            return menu.button("slot-broken")
                    .builder(new Ctx().put("slot", slot + 1)
                            .put("item", binding.itemKey()))
                    .action(clicker -> {
                        unbind(clicker, shopId, slot);
                        open(clicker, shopId, manage);
                    })
                    .build();
        }

        if (binding == null) {
            return menu.button("slot-empty")
                    .builder(new Ctx().put("slot", slot + 1))
                    .action(clicker -> openPicker(clicker, shopId, slot, 0, ""))
                    .build();
        }

        BigDecimal total = item.buy() == null
                ? BigDecimal.ZERO
                : item.buy().multiply(BigDecimal.valueOf(binding.amount()));
        Ctx ctx = new Ctx()
                .put("slot", slot + 1)
                .put("item", item.display())
                .put("amount", binding.amount())
                .put("price", services.amounts().formatExact(shop.currency(), total))
                .put("unit", services.amounts().formatExact(shop.currency(),
                        item.buy() == null ? BigDecimal.ZERO : item.buy()));

        ButtonTemplate template = menu.button(manage ? "slot-manage" : "slot-filled");
        return template.builder(ctx)
                .material(item.material())
                .amount(Math.min(64, binding.amount()))
                .action(clicker -> {
                    if (manage) {
                        openSlotOptions(clicker, shopId, slot);
                    } else {
                        buy(clicker, shopId, slot);
                    }
                })
                .build();
    }

    // -------------------------------------------------------------- picker

    /**
     * Every item the shop sells, paginated by the renderer, with an optional
     * filter so a big shop is still navigable.
     */
    private void openPicker(Player player, String shopId, int slot, int unusedPage,
                            String filter) {
        ShopDefinition shop = shops.shop(shopId);
        if (shop == null) {
            return;
        }
        MenuTemplate menu = services.menus().template("quick-buy-picker");
        List<ShopItem> items = filter.isBlank()
                ? new ArrayList<>(shop.allItems())
                : shop.search(filter);

        Ctx ctx = new Ctx()
                .put("slot", slot + 1)
                .put("count", items.size())
                .put("filter", filter);
        Screen.Builder builder = menu.screen(ctx);

        for (ShopItem item : items) {
            if (!item.buyable()) {
                continue;
            }
            if (!item.permission().isBlank() && !player.hasPermission(item.permission())) {
                continue;
            }
            Ctx itemCtx = new Ctx()
                    .put("item", item.display())
                    .put("unit", services.amounts()
                            .formatExact(shop.currency(), item.buy()));
            builder.button(menu.button("item").builder(itemCtx)
                    .material(item.material())
                    .action(clicker -> openAmount(clicker, shopId, slot, item.key()))
                    .build());
        }

        builder.input(ScreenInput.text("filter",
                services.messages().raw("quickbuy.filter-label"),
                services.messages().raw("quickbuy.filter-hint")));
        builder.onSubmit(services.messages().raw("quickbuy.filter-apply"),
                (clicker, values) ->
                        openPicker(clicker, shopId, slot, 0,
                                values.getOrDefault("filter", "").trim()));
        builder.button(menu.button("back")
                .build(ctx, clicker -> open(clicker, shopId, false)));

        services.ui().open(player, builder.build());
    }

    /** Asks for the quantity this slot should buy, then saves the binding. */
    private void openAmount(Player player, String shopId, int slot, String itemKey) {
        ShopDefinition shop = shops.shop(shopId);
        ShopItem item = shop == null ? null : shop.item(itemKey);
        if (item == null) {
            return;
        }
        MenuTemplate menu = services.menus().template("quick-buy-amount");
        Ctx ctx = new Ctx()
                .put("slot", slot + 1)
                .put("item", item.display())
                .put("unit", services.amounts().formatExact(shop.currency(), item.buy()));

        Screen.Builder builder = menu.screen(ctx, new ItemStack(item.material()));

        // Offer the item's own stack sizes as one-click choices, so the common
        // case never needs the keyboard at all — which matters on Bedrock.
        for (int quantity : item.stackSizes()) {
            Ctx quantityCtx = new Ctx()
                    .put("amount", quantity)
                    .put("price", services.amounts().formatExact(shop.currency(),
                            item.buy().multiply(BigDecimal.valueOf(quantity))));
            builder.button(menu.button("preset").builder(quantityCtx)
                    .material(item.material())
                    .amount(Math.min(64, quantity))
                    .action(clicker -> save(clicker, shopId, slot, itemKey, quantity))
                    .build());
        }

        builder.input(ScreenInput.text("amount",
                services.messages().raw("quickbuy.amount-label"),
                services.messages().raw("quickbuy.amount-hint")));
        builder.onSubmit(services.messages().raw("quickbuy.amount-save"),
                (clicker, values) -> {
                    int amount;
                    try {
                        amount = Integer.parseInt(values.getOrDefault("amount", "").trim());
                    } catch (NumberFormatException ex) {
                        services.messages().send(clicker, "errors.not-a-number",
                                Ctx.of("input", values.getOrDefault("amount", "")));
                        openAmount(clicker, shopId, slot, itemKey);
                        return;
                    }
                    if (amount < 1 || amount > 2304) {
                        services.messages().send(clicker, "quickbuy.amount-range");
                        openAmount(clicker, shopId, slot, itemKey);
                        return;
                    }
                    save(clicker, shopId, slot, itemKey, amount);
                });

        builder.button(menu.button("back")
                .build(ctx, clicker -> openPicker(clicker, shopId, slot, 0, "")));
        services.ui().open(player, builder.build());
    }

    private void save(Player player, String shopId, int slot, String itemKey, int amount) {
        bind(player, shopId, slot, itemKey, amount);
        ShopDefinition shop = shops.shop(shopId);
        ShopItem item = shop == null ? null : shop.item(itemKey);
        services.sounds().play(player, SoundService.PURCHASE_SUCCESS);
        services.messages().send(player, "quickbuy.saved", new Ctx()
                .put("slot", slot + 1)
                .put("amount", amount)
                .put("item", item == null ? itemKey : item.display()));
        open(player, shopId, false);
    }

    // ------------------------------------------------------- slot options

    private void openSlotOptions(Player player, String shopId, int slot) {
        ShopDefinition shop = shops.shop(shopId);
        Binding binding = binding(player, shopId, slot);
        if (shop == null || binding == null) {
            open(player, shopId, true);
            return;
        }
        ShopItem item = shop.item(binding.itemKey());
        MenuTemplate menu = services.menus().template("quick-buy-slot");
        Ctx ctx = new Ctx()
                .put("slot", slot + 1)
                .put("amount", binding.amount())
                .put("item", item == null ? binding.itemKey() : item.display());

        Screen screen = menu.screen(ctx,
                        item == null ? null : new ItemStack(item.material()))
                .button(menu.button("change")
                        .build(ctx, clicker -> openPicker(clicker, shopId, slot, 0, "")))
                .button(menu.button("remove").build(ctx, clicker -> {
                    unbind(clicker, shopId, slot);
                    services.messages().send(clicker, "quickbuy.removed",
                            Ctx.of("slot", String.valueOf(slot + 1)));
                    open(clicker, shopId, true);
                }))
                .button(menu.button("back")
                        .build(ctx, clicker -> open(clicker, shopId, true)))
                .build();
        services.ui().open(player, screen);
    }

    // ----------------------------------------------------------------- buy

    private void buy(Player player, String shopId, int slot) {
        Binding binding = binding(player, shopId, slot);
        ShopDefinition shop = shops.shop(shopId);
        if (binding == null || shop == null) {
            return;
        }
        ShopItem item = shop.item(binding.itemKey());
        if (item == null || !item.buyable()) {
            services.messages().send(player, "quickbuy.item-gone");
            return;
        }
        if (shop.quickBuyConfirm()) {
            shops.confirmBuy(player, shopId, item.key(), binding.amount(),
                    clicker -> open(clicker, shopId, false));
            return;
        }
        shops.buy(player, shopId, item.key(), binding.amount(),
                clicker -> open(clicker, shopId, false));
    }

    private BigDecimal balance(Player player, ShopDefinition shop) {
        CurrencyService currency = services.currencies().get(shop.currency());
        return currency == null ? BigDecimal.ZERO : currency.balance(player);
    }
}
