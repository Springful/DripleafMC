package net.dripleaf.core.core.commands;

import net.dripleaf.core.common.Services;
import net.dripleaf.core.common.command.CommandSpec;
import net.dripleaf.core.common.command.DripleafCommand;
import net.dripleaf.core.common.icon.IconService;
import net.dripleaf.core.common.text.Ctx;
import net.dripleaf.core.common.text.Text;
import net.dripleaf.core.core.CoreModule;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The utility catalogue: virtual workstations, item helpers and the small
 * quality-of-life commands.
 *
 * <p>All of these ship <b>disabled</b>, because EssentialsX provides most of
 * them and two plugins racing to own {@code /fly} is a support ticket rather
 * than a feature. Staff enable them one at a time in {@code core/commands.yml}
 * after disabling the Essentials equivalent.
 */
public final class UtilityCommands {

    private UtilityCommands() {
    }

    /**
     * Every virtual workstation is this one class with a different {@link Station}.
     *
     * <p>These have to go through the dedicated {@code openWorkbench},
     * {@code openAnvil}, {@code openGrindstone} … methods with
     * {@code force = true}. Handing {@code Bukkit.createInventory} an
     * {@link InventoryType} produces a window that looks right and does
     * nothing: it has no block behind it, so no recipe ever resolves. That is
     * why {@code /workbench} and {@code /craft} appeared to open and then
     * refused to craft.
     */
    public static final class Workstation extends DripleafCommand {

        /** Which station to open. Each maps to its own Bukkit call. */
        public enum Station {
            ENDER_CHEST, WORKBENCH, ANVIL, GRINDSTONE, CARTOGRAPHY,
            STONECUTTER, LOOM, SMITHING, ENCHANTING
        }

        private final Station station;

        public Workstation(Services services, CommandSpec spec, Station station) {
            super(services, spec);
            this.station = station;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            // `force = true` is what makes these work with no block present.
            switch (station) {
                case ENDER_CHEST -> player.openInventory(player.getEnderChest());
                case WORKBENCH -> player.openWorkbench(null, true);
                case ANVIL -> player.openAnvil(null, true);
                case GRINDSTONE -> player.openGrindstone(null, true);
                case CARTOGRAPHY -> player.openCartographyTable(null, true);
                case STONECUTTER -> player.openStonecutter(null, true);
                case LOOM -> player.openLoom(null, true);
                case SMITHING -> player.openSmithingTable(null, true);
                case ENCHANTING -> player.openEnchanting(null, true);
            }
            return true;
        }
    }

    /** A disposal window: anything left in it when it closes is gone. */
    public static final class Disposal extends DripleafCommand {

        public Disposal(Services services, CommandSpec spec) {
            super(services, spec);
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            player.openInventory(Bukkit.createInventory(null, 54,
                    Text.parse(services.messages().raw("utility.disposal-title"))));
            return true;
        }
    }

    public static final class Hat extends DripleafCommand {

        public Hat(Services services, CommandSpec spec) {
            super(services, spec);
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held.getType() == Material.AIR) {
                services.messages().send(sender, "economy.nothing-in-hand");
                return false;
            }
            ItemStack helmet = player.getInventory().getHelmet();
            player.getInventory().setHelmet(held.clone());
            player.getInventory().setItemInMainHand(helmet);
            services.messages().send(sender, "utility.hat");
            return true;
        }
    }

    /** {@code /repair [all]} — repairs the held item, or every damaged item. */
    public static final class Repair extends DripleafCommand {

        public Repair(Services services, CommandSpec spec) {
            super(services, spec);
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            boolean all = args.length > 0 && args[0].equalsIgnoreCase("all");
            if (all && !player.hasPermission("dripleaf.repair.all")) {
                services.messages().send(sender, "errors.no-permission",
                        Ctx.of("permission", "dripleaf.repair.all"));
                return false;
            }
            int repaired = all
                    ? repairAll(player)
                    : (repair(player.getInventory().getItemInMainHand()) ? 1 : 0);
            if (repaired == 0) {
                services.messages().send(sender, "utility.nothing-to-repair");
                return false;
            }
            services.messages().send(sender, "utility.repaired",
                    Ctx.of("count", String.valueOf(repaired)));
            return true;
        }

        private static int repairAll(Player player) {
            int count = 0;
            for (ItemStack stack : player.getInventory().getContents()) {
                if (repair(stack)) {
                    count++;
                }
            }
            for (ItemStack stack : player.getInventory().getArmorContents()) {
                if (repair(stack)) {
                    count++;
                }
            }
            return count;
        }

        private static boolean repair(ItemStack stack) {
            if (stack == null || stack.getType() == Material.AIR) {
                return false;
            }
            ItemMeta meta = stack.getItemMeta();
            if (!(meta instanceof Damageable damageable) || damageable.getDamage() == 0) {
                return false;
            }
            damageable.setDamage(0);
            stack.setItemMeta(meta);
            return true;
        }
    }

    /**
     * Compacts the inventory using vanilla 3×3 and 2×2 recipes.
     *
     * <p>The recipe registry is walked rather than a materials list being
     * hardcoded, so datapack and modded recipes compact automatically.
     */
    public static final class Condense extends DripleafCommand {

        public Condense(Services services, CommandSpec spec) {
            super(services, spec);
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            Map<Material, Material> compactions = compactionRecipes();
            Map<Material, Integer> costs = compactionCosts();
            int converted = 0;

            for (Map.Entry<Material, Material> entry : compactions.entrySet()) {
                Material from = entry.getKey();
                int cost = costs.getOrDefault(from, 9);
                int held = net.dripleaf.core.core.shop.ShopService.countHeld(player, from);
                int batches = held / cost;
                if (batches <= 0) {
                    continue;
                }
                player.getInventory().removeItem(new ItemStack(from, batches * cost));
                player.getInventory().addItem(new ItemStack(entry.getValue(), batches));
                converted += batches;
            }

            if (converted == 0) {
                services.messages().send(sender, "utility.nothing-to-condense");
                return false;
            }
            services.messages().send(sender, "utility.condensed",
                    Ctx.of("count", String.valueOf(converted)));
            return true;
        }

        /** Cached on first use; the recipe registry does not change at runtime. */
        private static Map<Material, Material> compactions;
        private static Map<Material, Integer> compactionCosts;

        private static synchronized Map<Material, Material> compactionRecipes() {
            if (compactions == null) {
                scan();
            }
            return compactions;
        }

        private static synchronized Map<Material, Integer> compactionCosts() {
            if (compactionCosts == null) {
                scan();
            }
            return compactionCosts;
        }

        private static void scan() {
            Map<Material, Material> found = new HashMap<>(64);
            Map<Material, Integer> costs = new HashMap<>(64);
            Iterator<Recipe> iterator = Bukkit.recipeIterator();
            while (iterator.hasNext()) {
                Recipe recipe = iterator.next();
                Material single = null;
                int count = 0;

                if (recipe instanceof ShapedRecipe shaped) {
                    for (String row : shaped.getShape()) {
                        for (char symbol : row.toCharArray()) {
                            ItemStack ingredient = shaped.getIngredientMap().get(symbol);
                            if (ingredient == null) {
                                continue;
                            }
                            if (single != null && single != ingredient.getType()) {
                                single = null;
                                count = -1;
                                break;
                            }
                            single = ingredient.getType();
                            count++;
                        }
                        if (count < 0) {
                            break;
                        }
                    }
                } else if (recipe instanceof ShapelessRecipe shapeless) {
                    for (ItemStack ingredient : shapeless.getIngredientList()) {
                        if (single != null && single != ingredient.getType()) {
                            single = null;
                            count = -1;
                            break;
                        }
                        single = ingredient.getType();
                        count++;
                    }
                }

                // Only true compactions: N of one thing in, exactly one out.
                if (single != null && (count == 4 || count == 9)
                        && recipe.getResult().getAmount() == 1
                        && recipe.getResult().getType() != single) {
                    found.put(single, recipe.getResult().getType());
                    costs.put(single, count);
                }
            }
            compactions = Map.copyOf(found);
            compactionCosts = Map.copyOf(costs);
        }
    }

    public static final class RecipeLookup extends DripleafCommand {

        public RecipeLookup(Services services, CommandSpec spec) {
            super(services, spec);
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            Material material = args.length > 0
                    ? IconService.material(args[0])
                    : player.getInventory().getItemInMainHand().getType();
            if (material == null || material == Material.AIR) {
                services.messages().send(sender, "utility.recipe-unknown",
                        Ctx.of("item", args.length > 0 ? args[0] : "?"));
                return false;
            }
            List<Recipe> recipes = Bukkit.getRecipesFor(new ItemStack(material));
            if (recipes.isEmpty()) {
                services.messages().send(sender, "utility.recipe-none",
                        Ctx.of("item", material.name()));
                return false;
            }
            services.messages().send(sender, "utility.recipe-header",
                    Ctx.of("item", IconService.pretty(material)));
            for (Recipe recipe : recipes) {
                String ingredients = describe(recipe);
                if (!ingredients.isBlank()) {
                    services.messages().send(sender, "utility.recipe-line",
                            Ctx.of("ingredients", ingredients));
                }
            }
            return true;
        }

        /** Ingredient counts as text — version-safe, and readable on Bedrock. */
        private static String describe(Recipe recipe) {
            Map<Material, Integer> counts = new java.util.LinkedHashMap<>();
            if (recipe instanceof ShapedRecipe shaped) {
                for (String row : shaped.getShape()) {
                    for (char symbol : row.toCharArray()) {
                        ItemStack ingredient = shaped.getIngredientMap().get(symbol);
                        if (ingredient != null) {
                            counts.merge(ingredient.getType(), 1, Integer::sum);
                        }
                    }
                }
            } else if (recipe instanceof ShapelessRecipe shapeless) {
                for (ItemStack ingredient : shapeless.getIngredientList()) {
                    counts.merge(ingredient.getType(), 1, Integer::sum);
                }
            } else {
                return "";
            }
            List<String> parts = new ArrayList<>(counts.size());
            for (Map.Entry<Material, Integer> entry : counts.entrySet()) {
                parts.add(entry.getValue() + "× " + IconService.pretty(entry.getKey()));
            }
            return String.join(", ", parts);
        }
    }

    /** {@code /heal} and {@code /feed} — same shape, different effect. */
    public static final class Restore extends DripleafCommand {

        private final boolean heal;

        public Restore(Services services, CommandSpec spec, boolean heal) {
            super(services, spec);
            this.heal = heal;
        }

        @Override
        protected boolean playerOnly() {
            return false;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            Player target = resolve(services, sender, player, args, 0);
            if (target == null) {
                return false;
            }
            if (heal) {
                target.setHealth(target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)
                        .getValue());
                target.setFireTicks(0);
            } else {
                target.setFoodLevel(20);
                target.setSaturation(20f);
            }
            services.messages().send(sender, heal ? "utility.healed" : "utility.fed",
                    Ctx.of("player", target.getName()));
            return true;
        }

        @Override
        protected Collection<String> complete(CommandSender sender, String[] args) {
            return args.length <= 1 ? onlineNames(args) : List.of();
        }
    }

    public static final class God extends DripleafCommand {

        private final CoreModule core;

        public God(Services services, CommandSpec spec, CoreModule core) {
            super(services, spec);
            this.core = core;
        }

        @Override
        protected boolean playerOnly() {
            return false;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            Player target = resolve(services, sender, player, args, 0);
            if (target == null) {
                return false;
            }
            boolean now = core.social().toggleGod(target);
            target.setInvulnerable(now);
            services.messages().send(sender, now ? "utility.god-on" : "utility.god-off",
                    Ctx.of("player", target.getName()));
            return true;
        }

        @Override
        protected Collection<String> complete(CommandSender sender, String[] args) {
            return args.length <= 1 ? onlineNames(args) : List.of();
        }
    }

    /**
     * {@code /fly}.
     *
     * <p>Consults GriefPrevention through {@code ClaimBridge}: a player holding
     * {@code dripleaf.fly.claims} — the Rebirth XX unlock — keeps flight while
     * inside a claim they have build trust in. Everywhere else the normal rules
     * apply, which is what makes the unlock mean something.
     */
    public static final class Fly extends DripleafCommand {

        private final CoreModule core;

        public Fly(Services services, CommandSpec spec, CoreModule core) {
            super(services, spec);
            this.core = core;
        }

        @Override
        protected boolean playerOnly() {
            return false;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            Player target = resolve(services, sender, player, args, 0);
            if (target == null) {
                return false;
            }
            if (target.getAllowFlight()) {
                core.flight().disable(target, null);
                services.messages().send(sender, "utility.fly-off",
                        Ctx.of("player", target.getName()));
                return true;
            }

            boolean permanent = target.hasPermission("dripleaf.fly.permanent");
            boolean inTrustedClaim = target.hasPermission("dripleaf.fly.claims")
                    && services.hooks().claims().trustedHere(target);
            boolean banked = core.flight().remaining(target) > 0L;

            // Permanent flight and the Rebirth XX claim unlock are free; anyone
            // else spends purchased flight time.
            if (!permanent && !inTrustedClaim && !banked) {
                services.messages().send(sender, "utility.fly-denied");
                return false;
            }
            if (!core.flight().enable(target)) {
                return false;
            }
            services.messages().send(sender, "utility.fly-on",
                    Ctx.of("player", target.getName()));
            return true;
        }

        @Override
        protected Collection<String> complete(CommandSender sender, String[] args) {
            return args.length <= 1 ? onlineNames(args) : List.of();
        }
    }

    public static final class Speed extends DripleafCommand {

        public Speed(Services services, CommandSpec spec) {
            super(services, spec);
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            if (args.length == 0) {
                services.messages().send(sender, "errors.usage",
                        Ctx.of("usage", "/speed <1-10> [player]"));
                return false;
            }
            int level;
            try {
                level = Integer.parseInt(args[0]);
            } catch (NumberFormatException ex) {
                services.messages().send(sender, "errors.not-a-number",
                        Ctx.of("input", args[0]));
                return false;
            }
            if (level < 1 || level > 10) {
                services.messages().send(sender, "utility.speed-range");
                return false;
            }
            Player target = resolve(services, sender, player, args, 1);
            if (target == null) {
                return false;
            }
            float speed = level / 10f;
            if (target.isFlying()) {
                target.setFlySpeed(speed);
            } else {
                target.setWalkSpeed(speed);
            }
            services.messages().send(sender, "utility.speed-set", new Ctx()
                    .put("player", target.getName()).put("level", String.valueOf(level)));
            return true;
        }
    }

    public static final class Vanish extends DripleafCommand {

        private final CoreModule core;

        public Vanish(Services services, CommandSpec spec, CoreModule core) {
            super(services, spec);
            this.core = core;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            boolean now = core.social().toggleVanish(player);
            services.messages().send(sender, now ? "utility.vanish-on" : "utility.vanish-off");
            return true;
        }
    }

    public static final class InvSee extends DripleafCommand {

        public InvSee(Services services, CommandSpec spec) {
            super(services, spec);
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            if (args.length == 0) {
                services.messages().send(sender, "errors.usage",
                        Ctx.of("usage", "/invsee <player>"));
                return false;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                services.messages().send(sender, "errors.player-not-found",
                        Ctx.of("player", args[0]));
                return false;
            }
            player.openInventory(target.getInventory());
            return true;
        }

        @Override
        protected Collection<String> complete(CommandSender sender, String[] args) {
            return args.length <= 1 ? onlineNames(args) : List.of();
        }
    }

    public static final class Near extends DripleafCommand {

        public Near(Services services, CommandSpec spec) {
            super(services, spec);
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            int radius = 100;
            if (args.length > 0) {
                try {
                    radius = Math.max(1, Math.min(1000, Integer.parseInt(args[0])));
                } catch (NumberFormatException ignored) {
                    // Keep the default; a typo should not fail the command.
                }
            }
            Location origin = player.getLocation();
            List<String> found = new ArrayList<>();
            for (Player other : player.getWorld().getPlayers()) {
                if (other.equals(player)) {
                    continue;
                }
                double distance = other.getLocation().distance(origin);
                if (distance <= radius) {
                    found.add(other.getName() + " (" + Math.round(distance) + "m)");
                }
            }
            services.messages().send(sender, found.isEmpty()
                    ? "utility.near-none" : "utility.near", new Ctx()
                    .put("radius", String.valueOf(radius))
                    .put("players", String.join(", ", found)));
            return true;
        }
    }

    public static final class Seen extends DripleafCommand {

        public Seen(Services services, CommandSpec spec) {
            super(services, spec);
        }

        @Override
        protected boolean playerOnly() {
            return false;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            if (args.length == 0) {
                services.messages().send(sender, "errors.usage",
                        Ctx.of("usage", "/seen <player>"));
                return false;
            }
            Player online = Bukkit.getPlayerExact(args[0]);
            if (online != null) {
                services.messages().send(sender, "utility.seen-online",
                        Ctx.of("player", online.getName()));
                return true;
            }
            OfflinePlayer offline = Bukkit.getOfflinePlayer(args[0]);
            // Profile reads touch disk, so the lookup goes to the shared worker.
            services.schedulers().asyncThenSync(
                    () -> services.players().loadOffline(offline.getUniqueId()),
                    data -> {
                        if (data.lastSeen() == 0L) {
                            services.messages().send(sender, "errors.player-not-found",
                                    Ctx.of("player", args[0]));
                            return;
                        }
                        services.messages().send(sender, "utility.seen-offline", new Ctx()
                                .put("player", data.lastKnownName())
                                .put("time", formatDuration(
                                        System.currentTimeMillis() - data.lastSeen())));
                    });
            return true;
        }

        @Override
        protected Collection<String> complete(CommandSender sender, String[] args) {
            return args.length <= 1 ? onlineNames(args) : List.of();
        }
    }

    public static final class Playtime extends DripleafCommand {

        public Playtime(Services services, CommandSpec spec) {
            super(services, spec);
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            Player target = resolve(services, sender, player, args, 0);
            if (target == null) {
                return false;
            }
            services.messages().send(sender, "utility.playtime", new Ctx()
                    .put("player", target.getName())
                    .put("time", formatDuration(
                            services.players().get(target).playtimeMillis())));
            return true;
        }

        @Override
        protected Collection<String> complete(CommandSender sender, String[] args) {
            return args.length <= 1 ? onlineNames(args) : List.of();
        }
    }

    public static final class Ping extends DripleafCommand {

        public Ping(Services services, CommandSpec spec) {
            super(services, spec);
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            Player target = resolve(services, sender, player, args, 0);
            if (target == null) {
                return false;
            }
            services.messages().send(sender, "utility.ping", new Ctx()
                    .put("player", target.getName())
                    .put("ping", String.valueOf(target.getPing())));
            return true;
        }

        @Override
        protected Collection<String> complete(CommandSender sender, String[] args) {
            return args.length <= 1 ? onlineNames(args) : List.of();
        }
    }

    public static final class Afk extends DripleafCommand {

        private final CoreModule core;

        public Afk(Services services, CommandSpec spec, CoreModule core) {
            super(services, spec);
            this.core = core;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            core.social().toggleAfk(player);
            return true;
        }
    }

    public static final class Sudo extends DripleafCommand {

        public Sudo(Services services, CommandSpec spec) {
            super(services, spec);
        }

        @Override
        protected boolean playerOnly() {
            return false;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            if (args.length < 2) {
                services.messages().send(sender, "errors.usage",
                        Ctx.of("usage", "/sudo <player> <command>"));
                return false;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                services.messages().send(sender, "errors.player-not-found",
                        Ctx.of("player", args[0]));
                return false;
            }
            String command = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            target.performCommand(command);
            services.audit().admin(sender.getName() + " sudo " + target.getName()
                    + ": " + command);
            services.messages().send(sender, "utility.sudo", new Ctx()
                    .put("player", target.getName()).put("command", command));
            return true;
        }

        @Override
        protected Collection<String> complete(CommandSender sender, String[] args) {
            return args.length <= 1 ? onlineNames(args) : List.of();
        }
    }

    public static final class Broadcast extends DripleafCommand {

        public Broadcast(Services services, CommandSpec spec) {
            super(services, spec);
        }

        @Override
        protected boolean playerOnly() {
            return false;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            if (args.length == 0) {
                services.messages().send(sender, "errors.usage",
                        Ctx.of("usage", "/broadcast <message>"));
                return false;
            }
            String message = String.join(" ", args);
            Bukkit.broadcast(Text.parse(
                    Ctx.of("message", message)
                            .applyRaw(services.messages().raw("utility.broadcast-format"))));
            return true;
        }
    }

    public static final class GameModeCommand extends DripleafCommand {

        public GameModeCommand(Services services, CommandSpec spec) {
            super(services, spec);
        }

        @Override
        protected boolean playerOnly() {
            return false;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            if (args.length == 0) {
                services.messages().send(sender, "errors.usage",
                        Ctx.of("usage", "/gm <0-3|survival|creative|adventure|spectator>"));
                return false;
            }
            GameMode mode = parseMode(args[0]);
            if (mode == null) {
                services.messages().send(sender, "utility.gamemode-unknown",
                        Ctx.of("input", args[0]));
                return false;
            }
            Player target = resolve(services, sender, player, args, 1);
            if (target == null) {
                return false;
            }
            target.setGameMode(mode);
            services.messages().send(sender, "utility.gamemode-set", new Ctx()
                    .put("player", target.getName())
                    .put("mode", mode.name().toLowerCase(Locale.ROOT)));
            return true;
        }

        private static GameMode parseMode(String raw) {
            return switch (raw.toLowerCase(Locale.ROOT)) {
                case "0", "s", "survival" -> GameMode.SURVIVAL;
                case "1", "c", "creative" -> GameMode.CREATIVE;
                case "2", "a", "adventure" -> GameMode.ADVENTURE;
                case "3", "sp", "spectator" -> GameMode.SPECTATOR;
                default -> null;
            };
        }

        @Override
        protected Collection<String> complete(CommandSender sender, String[] args) {
            if (args.length <= 1) {
                return filter(List.of("survival", "creative", "adventure", "spectator"), args);
            }
            return args.length == 2 ? onlineNames(args) : List.of();
        }
    }

    public static final class More extends DripleafCommand {

        public More(Services services, CommandSpec spec) {
            super(services, spec);
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held.getType() == Material.AIR) {
                services.messages().send(sender, "economy.nothing-in-hand");
                return false;
            }
            held.setAmount(held.getMaxStackSize());
            services.messages().send(sender, "utility.more");
            return true;
        }
    }

    /** {@code /itemname} and {@code /itemlore} — MiniMessage in, component out. */
    public static final class ItemText extends DripleafCommand {

        private final boolean lore;

        public ItemText(Services services, CommandSpec spec, boolean lore) {
            super(services, spec);
            this.lore = lore;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held.getType() == Material.AIR) {
                services.messages().send(sender, "economy.nothing-in-hand");
                return false;
            }
            if (args.length == 0) {
                services.messages().send(sender, "errors.usage", Ctx.of("usage",
                        "/" + id() + " <text>"));
                return false;
            }
            String raw = String.join(" ", args);
            ItemMeta meta = held.getItemMeta();
            if (lore) {
                List<net.kyori.adventure.text.Component> lines = new ArrayList<>();
                for (String line : raw.split("\\|")) {
                    lines.add(Text.item(line.trim()));
                }
                meta.lore(lines);
            } else {
                meta.displayName(Text.item(raw));
            }
            held.setItemMeta(meta);
            services.messages().send(sender, lore ? "utility.lore-set" : "utility.name-set");
            return true;
        }
    }

    /**
     * Resolves an optional player argument at {@code index}, defaulting to the
     * sender.
     *
     * <p>Returns {@code null} — having already explained why — when the console
     * runs a player-targeted command with no argument, or names someone who is
     * not online.
     */
    static Player resolve(Services services, CommandSender sender, Player self, String[] args,
                          int index) {
        if (args.length <= index) {
            if (self == null) {
                services.messages().send(sender, "errors.players-only");
            }
            return self;
        }
        Player target = Bukkit.getPlayerExact(args[index]);
        if (target == null) {
            services.messages().send(sender, "errors.player-not-found",
                    Ctx.of("player", args[index]));
        }
        return target;
    }
}
