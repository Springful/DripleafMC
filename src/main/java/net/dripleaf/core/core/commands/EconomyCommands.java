package net.dripleaf.core.core.commands;

import net.dripleaf.core.api.CurrencyService;
import net.dripleaf.core.api.CurrencyType;
import net.dripleaf.core.common.Services;
import net.dripleaf.core.common.command.CommandSpec;
import net.dripleaf.core.common.command.DripleafCommand;
import net.dripleaf.core.common.money.ParseResult;
import net.dripleaf.core.common.text.Ctx;
import net.dripleaf.core.core.CoreModule;
import net.dripleaf.core.core.leaderboard.LeaderboardService;
import net.dripleaf.core.core.shop.ShopItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Balances, payments, shops and the currency admin commands.
 *
 * <p>Every amount argument on every command here goes through
 * {@link net.dripleaf.core.common.money.AmountParser} — {@code /pay Steve 2.5m}
 * works, and so does {@code all} and {@code half}.
 */
public final class EconomyCommands {

    private EconomyCommands() {
    }

    public static final class Balance extends DripleafCommand {

        public Balance(Services services, CommandSpec spec) {
            super(services, spec);
        }

        @Override
        protected boolean playerOnly() {
            return false;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            OfflinePlayer target = player;
            if (args.length > 0) {
                if (!sender.hasPermission("dripleaf.balance.other")) {
                    services.messages().send(sender, "errors.no-permission",
                            Ctx.of("permission", "dripleaf.balance.other"));
                    return false;
                }
                target = Bukkit.getOfflinePlayer(args[0]);
            }
            if (target == null) {
                services.messages().send(sender, "errors.players-only");
                return false;
            }
            CurrencyService money = services.currencies().money();
            services.messages().send(sender, "economy.balance", new Ctx()
                    .put("player", target.getName() == null ? args[0] : target.getName())
                    .put("balance", services.amounts().formatExact(CurrencyType.MONEY,
                            money.balance(target))));
            return true;
        }

        @Override
        protected Collection<String> complete(CommandSender sender, String[] args) {
            return args.length <= 1 ? onlineNames(args) : List.of();
        }
    }

    /** Served from the cached leaderboard; never computed on request. */
    public static final class Baltop extends DripleafCommand {

        private final CoreModule core;

        public Baltop(Services services, CommandSpec spec, CoreModule core) {
            super(services, spec);
            this.core = core;
        }

        @Override
        protected boolean playerOnly() {
            return false;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            int page = parsePage(args);
            List<LeaderboardService.Entry> board = core.leaderboards().balances();
            services.messages().send(sender, "economy.baltop-header", new Ctx()
                    .put("page", String.valueOf(page))
                    .put("pages", String.valueOf(core.leaderboards().pages(board))));

            List<LeaderboardService.Entry> entries = core.leaderboards().page(board, page);
            int rank = (page - 1) * 10 + 1;
            for (LeaderboardService.Entry entry : entries) {
                services.messages().send(sender, "economy.baltop-entry", new Ctx()
                        .put("rank", String.valueOf(rank++))
                        .put("player", entry.name())
                        .put("balance", services.amounts()
                                .formatExact(CurrencyType.MONEY, entry.value())));
            }
            if (entries.isEmpty()) {
                services.messages().send(sender, "economy.baltop-empty");
            }
            return true;
        }
    }

    public static final class Pay extends DripleafCommand {

        public Pay(Services services, CommandSpec spec) {
            super(services, spec);
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            if (args.length < 2) {
                services.messages().send(sender, "errors.usage",
                        Ctx.of("usage", "/pay <player> <amount>"));
                return false;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                services.messages().send(sender, "errors.player-not-found",
                        Ctx.of("player", args[0]));
                return false;
            }
            if (target.equals(player)) {
                services.messages().send(sender, "economy.pay-self");
                return false;
            }

            CurrencyService money = services.currencies().money();
            ParseResult parsed = services.amounts().parse(args[1], money.balance(player));
            if (!parsed.ok()) {
                services.messages().send(sender, parsed.errorKey(), Ctx.of("input", args[1]));
                return false;
            }
            BigDecimal amount = parsed.get();
            if (amount.signum() <= 0) {
                services.messages().send(sender, "errors.amount-negative");
                return false;
            }
            if (!money.has(player, amount)) {
                services.messages().send(sender, "economy.insufficient-funds",
                        Ctx.of("amount", services.amounts()
                                .formatExact(CurrencyType.MONEY, amount)));
                return false;
            }
            if (!money.withdraw(player, amount) || !money.deposit(target, amount)) {
                services.messages().send(sender, "economy.transfer-failed");
                return false;
            }

            String formatted = services.amounts().formatExact(CurrencyType.MONEY, amount);
            services.audit().transaction(String.format("PAY  %s -> %s %s",
                    player.getName(), target.getName(), amount.toPlainString()));
            services.messages().send(sender, "economy.pay-sent", new Ctx()
                    .put("player", target.getName()).put("amount", formatted));
            services.messages().send(target, "economy.pay-received", new Ctx()
                    .put("player", player.getName()).put("amount", formatted));
            return true;
        }

        @Override
        protected Collection<String> complete(CommandSender sender, String[] args) {
            return args.length <= 1 ? onlineNames(args) : List.of();
        }
    }

    /** {@code /shop}, {@code /shardshop} and {@code /soulshop} differ by one id. */
    public static final class OpenShop extends DripleafCommand {

        private final CoreModule core;
        private final String shopId;

        public OpenShop(Services services, CommandSpec spec, CoreModule core, String shopId) {
            super(services, spec);
            this.core = core;
            this.shopId = shopId;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            core.shops().openRoot(player, shopId);
            return true;
        }
    }

    /** {@code /sell [hand|all|inventory]}. */
    public static final class Sell extends DripleafCommand {

        private final CoreModule core;

        public Sell(Services services, CommandSpec spec, CoreModule core) {
            super(services, spec);
            this.core = core;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            String mode = args.length == 0 ? "hand" : args[0].toLowerCase(Locale.ROOT);
            BigDecimal total = BigDecimal.ZERO;
            int sold = 0;

            if (mode.equals("hand")) {
                ItemStack held = player.getInventory().getItemInMainHand();
                if (held.getType() == Material.AIR) {
                    services.messages().send(sender, "economy.nothing-in-hand");
                    return false;
                }
                BigDecimal value = sellStack(player, held.getType(), held.getAmount());
                if (value.signum() <= 0) {
                    services.messages().send(sender, "economy.not-sellable",
                            Ctx.of("item", held.getType().name()));
                    return false;
                }
                sold = held.getAmount();
                total = value;
                player.getInventory().setItemInMainHand(null);
            } else {
                for (ItemStack stack : player.getInventory().getStorageContents()) {
                    if (stack == null || stack.getType() == Material.AIR) {
                        continue;
                    }
                    BigDecimal value = sellStack(player, stack.getType(), stack.getAmount());
                    if (value.signum() <= 0) {
                        continue;
                    }
                    total = total.add(value);
                    sold += stack.getAmount();
                    player.getInventory().removeItem(stack.clone());
                }
            }

            if (sold == 0) {
                services.messages().send(sender, "economy.nothing-sold");
                return false;
            }
            services.currencies().money().deposit(player, total);
            services.audit().transaction(String.format("SELL %s bulk x%d total=%s",
                    player.getName(), sold, total.toPlainString()));
            services.messages().send(sender, "economy.sold", new Ctx()
                    .put("amount", String.valueOf(sold))
                    .put("total", services.amounts().formatExact(CurrencyType.MONEY, total)));
            return true;
        }

        private BigDecimal sellStack(Player player, Material material, int amount) {
            ShopItem item = core.shops().sellableFor(material);
            if (item == null) {
                return BigDecimal.ZERO;
            }
            return core.shops().sellPrice(player, core.shops().serverShop(), item, amount);
        }

        @Override
        protected Collection<String> complete(CommandSender sender, String[] args) {
            return args.length <= 1
                    ? filter(List.of("hand", "all", "inventory"), args)
                    : List.of();
        }
    }

    public static final class Worth extends DripleafCommand {

        private final CoreModule core;

        public Worth(Services services, CommandSpec spec, CoreModule core) {
            super(services, spec);
            this.core = core;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            Material material = args.length > 0
                    ? net.dripleaf.core.common.icon.IconService.material(args[0])
                    : player.getInventory().getItemInMainHand().getType();
            if (material == null || material == Material.AIR) {
                services.messages().send(sender, "economy.nothing-in-hand");
                return false;
            }
            BigDecimal unit = core.shops().worth(player, material, 1);
            if (unit.signum() <= 0) {
                services.messages().send(sender, "economy.not-sellable",
                        Ctx.of("item", material.name()));
                return false;
            }
            services.messages().send(sender, "economy.worth", new Ctx()
                    .put("item", material.name())
                    .put("unit", services.amounts().formatExact(CurrencyType.MONEY, unit))
                    .put("stack", services.amounts().formatExact(CurrencyType.MONEY,
                            core.shops().worth(player, material, material.getMaxStackSize()))));
            return true;
        }
    }

    /**
     * {@code /eco}, {@code /shards} and {@code /souls} share one implementation
     * — {@code give|take|set|balance} over whichever currency was passed in.
     */
    public static final class CurrencyAdmin extends DripleafCommand {

        private final CurrencyType currency;

        public CurrencyAdmin(Services services, CommandSpec spec, CurrencyType currency) {
            super(services, spec);
            this.currency = currency;
        }

        @Override
        protected boolean playerOnly() {
            return false;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            if (args.length < 2) {
                services.messages().send(sender, "errors.usage", Ctx.of("usage",
                        "/" + id() + " <give|take|set|balance> <player> [amount]"));
                return false;
            }
            String action = args[0].toLowerCase(Locale.ROOT);
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            CurrencyService service = services.currencies().get(currency);
            if (service == null || !service.available()) {
                services.messages().send(sender, "shop.currency-unavailable",
                        Ctx.of("currency", currency.id()));
                return false;
            }

            if (action.equals("balance")) {
                services.messages().send(sender, "economy.admin-balance", new Ctx()
                        .put("player", args[1])
                        .put("currency", currency.id())
                        .put("balance", services.amounts()
                                .formatExact(currency, service.balance(target))));
                return true;
            }
            if (args.length < 3) {
                services.messages().send(sender, "errors.usage", Ctx.of("usage",
                        "/" + id() + " " + action + " <player> <amount>"));
                return false;
            }

            ParseResult parsed = services.amounts()
                    .parseFor(currency, args[2], service.balance(target));
            if (!parsed.ok()) {
                services.messages().send(sender, parsed.errorKey(), Ctx.of("input", args[2]));
                return false;
            }
            BigDecimal before = service.balance(target);
            BigDecimal amount = parsed.get();

            boolean ok = switch (action) {
                case "give", "add" -> service.deposit(target, amount);
                case "take", "remove" -> service.withdraw(target, amount);
                case "set" -> service.set(target, amount);
                default -> false;
            };
            if (!ok) {
                services.messages().send(sender, "economy.transfer-failed");
                return false;
            }

            BigDecimal after = service.balance(target);
            services.audit().admin(String.format("%s %s %s %s for %s (%s -> %s)",
                    sender.getName(), action, amount.toPlainString(), currency.id(),
                    args[1], before.toPlainString(), after.toPlainString()));
            services.messages().send(sender, "economy.admin-applied", new Ctx()
                    .put("action", action)
                    .put("player", args[1])
                    .put("currency", currency.id())
                    .put("before", services.amounts().formatExact(currency, before))
                    .put("after", services.amounts().formatExact(currency, after)));
            return true;
        }

        @Override
        protected Collection<String> complete(CommandSender sender, String[] args) {
            if (args.length <= 1) {
                return filter(List.of("give", "take", "set", "balance"), args);
            }
            return args.length == 2 ? onlineNames(args) : List.of();
        }
    }

    static int parsePage(String[] args) {
        if (args.length == 0) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(args[0]));
        } catch (NumberFormatException ex) {
            return 1;
        }
    }
}
