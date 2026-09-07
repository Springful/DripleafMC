package net.dripleaf.core.core.shop;

import net.dripleaf.core.api.CurrencyType;
import net.dripleaf.core.common.config.Cfg;
import net.dripleaf.core.common.config.ValidationLog;
import net.dripleaf.core.common.icon.IconService;
import net.dripleaf.core.common.money.AmountParser;
import net.dripleaf.core.common.money.ParseResult;
import org.bukkit.Material;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One shop, parsed from one yml.
 *
 * <p>The server shop, the shard shop and the soul shop are the same schema
 * parsed by the same code and rendered by the same screens — they differ only
 * in currency and file. That is the single most important structural decision
 * in the core module: adding a fourth shop is a config file, not a class.
 *
 * <p>Validation rules, all of which log by key and keep going:
 * <ul>
 *   <li>An item with neither {@code buy} nor {@code sell} is a config error and
 *       is skipped.</li>
 *   <li>{@code sell} above {@code buy} is a money printer. It is warned about
 *       loudly by name and left alone — staff may have a reason.</li>
 *   <li>An item in a category that does not exist goes into an auto-generated
 *       "Uncategorised" rather than vanishing, so the mistake is visible in
 *       game.</li>
 *   <li>An unknown material is skipped with a named warning, never a stack
 *       trace.</li>
 * </ul>
 */
public final class ShopDefinition {

    private final String id;
    private final String file;

    private String title = "Shop";
    private CurrencyType currency = CurrencyType.MONEY;
    private boolean showBalance = true;
    private boolean searchEnabled = true;
    private boolean quickBuyEnabled = true;
    private int quickBuySlots = 14;
    private boolean quickBuyConfirm = true;
    private double globalSellMultiplier = 1d;
    private String permission = "";

    private final Map<String, ShopCategory> categories = new LinkedHashMap<>(8);
    private final Map<String, ShopItem> items = new LinkedHashMap<>(64);
    private final List<String> warnings = new ArrayList<>();

    public ShopDefinition(String id, String file) {
        this.id = id;
        this.file = file;
    }

    public void load(Cfg root, AmountParser amounts, ValidationLog log) {
        categories.clear();
        items.clear();
        warnings.clear();

        Cfg settings = root.childOrEmpty("settings");
        this.title = settings.string("title", "Shop");
        this.currency = CurrencyType.from(settings.string("currency", "money"),
                CurrencyType.MONEY);
        this.showBalance = settings.bool("show-balance", true);
        this.searchEnabled = settings.bool("search-enabled", true);
        this.quickBuyEnabled = settings.bool("quick-buy-enabled", true);
        this.quickBuySlots = settings.integer("quick-buy-slots", 14, 1, 45);
        this.quickBuyConfirm = settings.bool("quick-buy-confirm", true);
        this.globalSellMultiplier = settings.number("global-sell-multiplier", 1d, 0d, 1000d);
        this.permission = settings.string("permission", "");

        Cfg categorySection = root.childOrEmpty("categories");
        for (String key : categorySection.keys()) {
            Cfg node = categorySection.child(key);
            if (node == null) {
                continue;
            }
            categories.put(key.toLowerCase(Locale.ROOT), new ShopCategory(
                    key.toLowerCase(Locale.ROOT),
                    node.string("display", IconService.pretty(
                            IconService.material(key, Material.CHEST))),
                    IconService.material(node.string("icon", ""), Material.CHEST),
                    List.copyOf(node.stringList("description")),
                    node.integer("order", Integer.MAX_VALUE - 1)));
        }

        Cfg itemSection = root.childOrEmpty("items");
        boolean needsUncategorised = false;
        for (String key : itemSection.keys()) {
            Cfg node = itemSection.child(key);
            if (node == null) {
                continue;
            }
            Material material = IconService.material(node.string("material", key));
            if (material == null) {
                log.add(file, "items." + key, "unknown material, item skipped");
                warnings.add("Unknown material for item \"" + key + '"');
                continue;
            }

            BigDecimal buy = price(node.string("buy", ""), amounts, log, "items." + key + ".buy");
            BigDecimal sell = price(node.string("sell", ""), amounts, log,
                    "items." + key + ".sell");
            if (buy == null && sell == null) {
                log.add(file, "items." + key,
                        "has neither a buy nor a sell price, item skipped");
                warnings.add("Item \"" + key + "\" has no price and was skipped");
                continue;
            }

            String category = node.string("category", "").toLowerCase(Locale.ROOT);
            if (category.isBlank() || !categories.containsKey(category)) {
                if (!category.isBlank()) {
                    warnings.add("Item \"" + key + "\" is in unknown category \""
                            + category + "\"");
                }
                category = ShopCategory.uncategorised().key();
                needsUncategorised = true;
            }

            List<Integer> stacks = node.raw() == null
                    ? List.of()
                    : node.raw().getIntegerList("stack-sizes");
            if (stacks.isEmpty()) {
                stacks = List.of(1, 16, 64);
            }

            ShopItem item = new ShopItem(
                    key.toLowerCase(Locale.ROOT),
                    material,
                    category,
                    node.string("display", IconService.pretty(material)),
                    buy,
                    sell,
                    List.copyOf(stacks),
                    node.string("permission", ""),
                    List.copyOf(node.stringList("lore")),
                    node.integer("max-per-day", 0, 0, 100_000),
                    List.copyOf(node.stringList("commands")),
                    node.bool("give-item", node.stringList("commands").isEmpty()));

            if (item.inverted()) {
                warnings.add("Item \"" + key + "\" sells for more than it buys ("
                        + item.sell().toPlainString() + " > " + item.buy().toPlainString()
                        + ") — this is a money printer");
            }
            items.put(item.key(), item);
        }

        if (needsUncategorised) {
            ShopCategory bucket = ShopCategory.uncategorised();
            categories.put(bucket.key(), bucket);
        }
    }

    private BigDecimal price(String raw, AmountParser amounts, ValidationLog log, String path) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (raw.trim().equals("-1")) {
            return null;
        }
        ParseResult result = amounts.parse(raw);
        if (!result.ok()) {
            log.add(file, path, '"' + raw + "\" is not a valid amount, disabled");
            return null;
        }
        return result.get();
    }

    // ------------------------------------------------------------- accessors

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public CurrencyType currency() {
        return currency;
    }

    public boolean showBalance() {
        return showBalance;
    }

    public boolean searchEnabled() {
        return searchEnabled;
    }

    public boolean quickBuyEnabled() {
        return quickBuyEnabled;
    }

    /** How many shortcut slots each player gets in this shop. */
    public int quickBuySlots() {
        return quickBuySlots;
    }

    /**
     * Whether a quick buy still shows a confirmation. On by default: spending
     * money on one click is exactly the kind of thing players report as a bug.
     */
    public boolean quickBuyConfirm() {
        return quickBuyConfirm;
    }

    public double globalSellMultiplier() {
        return globalSellMultiplier;
    }

    public String permission() {
        return permission;
    }

    /** Sorted by {@code order}, then by key, so the root screen is stable. */
    public List<ShopCategory> categories() {
        List<ShopCategory> out = new ArrayList<>(categories.values());
        out.sort(Comparator.comparingInt(ShopCategory::order).thenComparing(ShopCategory::key));
        return out;
    }

    public ShopCategory category(String key) {
        return categories.get(key.toLowerCase(Locale.ROOT));
    }

    public List<ShopItem> itemsIn(String categoryKey) {
        List<ShopItem> out = new ArrayList<>(16);
        for (ShopItem item : items.values()) {
            if (item.category().equalsIgnoreCase(categoryKey)) {
                out.add(item);
            }
        }
        return out;
    }

    public java.util.Collection<ShopItem> allItems() {
        return items.values();
    }

    public ShopItem item(String key) {
        return items.get(key.toLowerCase(Locale.ROOT));
    }

    /** Case-insensitive substring match over key and display name. */
    public List<ShopItem> search(String query) {
        String needle = query.toLowerCase(Locale.ROOT).trim();
        List<ShopItem> out = new ArrayList<>(16);
        for (ShopItem item : items.values()) {
            if (item.key().contains(needle)
                    || item.display().toLowerCase(Locale.ROOT).contains(needle)
                    || item.material().name().toLowerCase(Locale.ROOT).contains(needle)) {
                out.add(item);
            }
        }
        return out;
    }

    public int itemCount() {
        return items.size();
    }

    /** Config problems worth showing a human, beyond the validation log. */
    public List<String> warnings() {
        return List.copyOf(warnings);
    }
}
