package net.dripleafmc.rebirth.ui.menu;

import net.dripleafmc.rebirth.RebirthPlugin;
import net.dripleafmc.rebirth.config.ConfigFile;
import net.dripleafmc.rebirth.core.CheckResult;
import net.dripleafmc.rebirth.tier.RebirthPath;
import net.dripleafmc.rebirth.tier.RebirthTier;
import net.dripleafmc.rebirth.ui.RebirthUI;
import net.dripleafmc.rebirth.util.Ctx;
import net.dripleafmc.rebirth.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Chest-GUI front end (ui.mode: MENU).
 * <p>
 * Layout, materials, names and lore all come from menus.yml; this class only
 * decides which variant of each item to show and wires the click actions.
 */
public final class MenuUI implements RebirthUI {

    private static final List<Component> EMPTY = List.of();

    private final RebirthPlugin plugin;
    private YamlConfiguration cfg;

    public MenuUI(RebirthPlugin plugin) {
        this.plugin = plugin;
        this.cfg = ConfigFile.load(plugin, "menus.yml");
    }

    @Override
    public void invalidate() {
        this.cfg = ConfigFile.load(plugin, "menus.yml");
    }

    // ------------------------------------------------------------ main menu

    @Override
    public void openMain(Player player) {
        ConfigurationSection root = cfg.getConfigurationSection("main");
        if (root == null) {
            plugin.getLogger().warning("menus.yml is missing the 'main:' section.");
            return;
        }

        if (plugin.service().maxed(player)) {
            openMaxed(player, root);
            return;
        }

        RebirthTier tier = plugin.tiers().get(plugin.service().nextTier(player));
        if (tier == null) {
            plugin.lang().send(player, "rebirth.maxed");
            return;
        }

        Map<RebirthPath, CheckResult> results = new HashMap<>(2);
        for (RebirthPath path : RebirthPath.values()) {
            results.put(path, plugin.service().check(player, path));
        }

        Ctx headerCtx = plugin.service().context(player, tier, null, tier.cost(RebirthPath.STANDARD));
        RebirthHolder holder = new RebirthHolder();
        Inventory inventory = create(holder, root, headerCtx);

        ConfigurationSection items = root.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection node = items.getConfigurationSection(key);
                if (node != null) {
                    renderMainItem(player, holder, inventory, root, node, tier, results);
                }
            }
        }

        player.openInventory(inventory);
        plugin.service().playSound(player, plugin.settings().soundOpen());
    }

    private void renderMainItem(Player player, RebirthHolder holder, Inventory inventory,
                                ConfigurationSection root, ConfigurationSection node,
                                RebirthTier tier, Map<RebirthPath, CheckResult> results) {

        String action = node.getString("action", "NONE").toUpperCase(Locale.ROOT);

        switch (action) {
            case "PATH_STANDARD" -> renderPath(player, holder, inventory, root, node,
                    tier, results.get(RebirthPath.STANDARD), RebirthPath.STANDARD);
            case "PATH_SOUL" -> renderPath(player, holder, inventory, root, node,
                    tier, results.get(RebirthPath.SOUL), RebirthPath.SOUL);
            case "COMMAND" -> {
                Ctx ctx = plugin.service().context(player, tier, null, 0d);
                place(inventory, node, Items.build(
                        Items.material(node.getString("material"), Material.PAPER),
                        Text.item(node.getString("name", ""), ctx.resolver()),
                        plugin.renderer().parseAll(node.getStringList("lore"), ctx)));
                String command = node.getString("command", "");
                bind(holder, node, target -> {
                    target.closeInventory();
                    if (!command.isBlank()) {
                        target.performCommand(command);
                    }
                });
            }
            case "CLOSE" -> {
                Ctx ctx = plugin.service().context(player, tier, null, 0d);
                place(inventory, node, Items.build(
                        Items.material(node.getString("material"), Material.BARRIER),
                        Text.item(node.getString("name", ""), ctx.resolver()),
                        plugin.renderer().parseAll(node.getStringList("lore"), ctx)));
                bind(holder, node, Player::closeInventory);
            }
            default -> {
                Ctx ctx = plugin.service().context(player, tier, null, 0d);
                place(inventory, node, Items.build(
                        Items.material(node.getString("material"), Material.GRAY_STAINED_GLASS_PANE),
                        Text.item(node.getString("name", "<white>"), ctx.resolver()),
                        plugin.renderer().parseAll(node.getStringList("lore"), ctx)));
            }
        }
    }

    private void renderPath(Player player, RebirthHolder holder, Inventory inventory,
                            ConfigurationSection root, ConfigurationSection node,
                            RebirthTier tier, CheckResult result, RebirthPath path) {

        boolean unlocked = result.allowed();
        Ctx ctx = plugin.service().context(player, tier, path, result.cost());

        List<Component> rewardBlock = new ArrayList<>(12);
        rewardBlock.addAll(plugin.renderer().parseAll(root.getStringList("reward-preview"), ctx));
        if (path == RebirthPath.SOUL) {
            rewardBlock.addAll(plugin.renderer()
                    .parseAll(root.getStringList("soul-reward-preview"), ctx));
        }

        List<Component> clickBlock = EMPTY;
        String clickLine = node.getString("click-line", "");
        if (unlocked && !clickLine.isBlank()) {
            clickBlock = List.of(Component.empty(), Text.item(clickLine, ctx.resolver()));
        }

        Map<String, List<Component>> expansions = Map.of(
                "requirements", plugin.renderer().requirements(result),
                "rewards", rewardBlock,
                "unlocks", plugin.renderer().parseAll(tier.unlocks(), ctx),
                "click", clickBlock);

        String rawName = unlocked
                ? node.getString("name", "")
                : node.getString("locked-name", node.getString("name", "")
                + plugin.lang().raw("components.locked-suffix"));

        Material material = unlocked
                ? Items.material(node.getString("material"), Material.LIME_DYE)
                : Items.material(node.getString("locked-material"), Material.GRAY_DYE);

        place(inventory, node, Items.build(material,
                Text.item(rawName, ctx.resolver()),
                plugin.renderer().expand(node.getStringList("lore"), ctx, expansions)));

        bind(holder, node, target -> {
            if (!unlocked) {
                plugin.service().playSound(target, plugin.settings().soundDenied());
                denied(target, result, path);
                return;
            }
            target.closeInventory();
            openConfirm(target, path);
        });
    }

    private void denied(Player player, CheckResult result, RebirthPath path) {
        Ctx ctx = new Ctx()
                .put("path", plugin.lang().raw("components.path-name-" + path.id()))
                .put("cost", plugin.settings().numbers().display(result.cost()))
                .put("time", plugin.service().formatDuration(result.cooldownRemaining()));
        switch (result.status()) {
            case COOLDOWN -> plugin.lang().send(player, "rebirth.cooldown", ctx);
            case INSUFFICIENT_FUNDS -> plugin.lang().send(player, "rebirth.insufficient-funds", ctx);
            case MAXED -> plugin.lang().send(player, "rebirth.maxed", ctx);
            default -> plugin.lang().send(player, "rebirth.locked", ctx);
        }
    }

    private void openMaxed(Player player, ConfigurationSection root) {
        ConfigurationSection node = root.getConfigurationSection("maxed");
        RebirthHolder holder = new RebirthHolder();
        Ctx ctx = new Ctx()
                .put("player", player.getName())
                .put("max_tier", plugin.tiers().highest());
        Inventory inventory = create(holder, root, ctx);
        if (node != null) {
            place(inventory, node, Items.build(
                    Items.material(node.getString("material"), Material.LIME_DYE),
                    Text.item(node.getString("name", ""), ctx.resolver()),
                    plugin.renderer().parseAll(node.getStringList("lore"), ctx)));
        }
        player.openInventory(inventory);
    }

    // --------------------------------------------------------- confirm menu

    @Override
    public void openConfirm(Player player, RebirthPath path) {
        ConfigurationSection root = cfg.getConfigurationSection("confirm");
        if (root == null) {
            plugin.getLogger().warning("menus.yml is missing the 'confirm:' section.");
            return;
        }

        CheckResult result = plugin.service().check(player, path);
        if (!result.allowed()) {
            denied(player, result, path);
            return;
        }

        RebirthTier tier = result.tier();
        Ctx ctx = plugin.service().context(player, tier, path, result.cost());

        RebirthHolder holder = new RebirthHolder();
        Inventory inventory = create(holder, root, ctx);

        ConfigurationSection items = root.getConfigurationSection("items");
        if (items == null) {
            player.openInventory(inventory);
            return;
        }

        for (String key : items.getKeys(false)) {
            ConfigurationSection node = items.getConfigurationSection(key);
            if (node == null) {
                continue;
            }
            String action = node.getString("action", "NONE").toUpperCase(Locale.ROOT);
            boolean soul = path == RebirthPath.SOUL;

            String rawName = soul
                    ? node.getString("soul-name", node.getString("name", ""))
                    : node.getString("name", "");
            Material material = soul
                    ? Items.material(node.getString("soul-material", node.getString("material")),
                    Material.PURPLE_STAINED_GLASS_PANE)
                    : Items.material(node.getString("material"), Material.LIME_STAINED_GLASS_PANE);

            place(inventory, node, Items.build(material,
                    Text.item(rawName, ctx.resolver()),
                    plugin.renderer().parseAll(node.getStringList("lore"), ctx)));

            switch (action) {
                case "CONFIRM" -> bind(holder, node, target -> {
                    target.closeInventory();
                    confirm(target, path);
                });
                case "BACK" -> bind(holder, node, target -> {
                    target.closeInventory();
                    plugin.lang().send(target, "rebirth.cancelled");
                    openMain(target);
                });
                case "CANCEL", "CLOSE" -> bind(holder, node, Player::closeInventory);
                default -> {
                    // decorative
                }
            }
        }

        player.openInventory(inventory);
    }

    private void confirm(Player player, RebirthPath path) {
        CheckResult.Status status = plugin.service().perform(player, path);
        if (status == CheckResult.Status.OK) {
            return;
        }
        CheckResult fresh = plugin.service().check(player, path);
        plugin.service().playSound(player, plugin.settings().soundDenied());
        denied(player, fresh, path);
    }

    // ---------------------------------------------------------------- utils

    private Inventory create(RebirthHolder holder, ConfigurationSection root, Ctx ctx) {
        Component title = Text.parse(root.getString("title", "Rebirth"), ctx.resolver());
        String type = root.getString("type", "HOPPER").toUpperCase(Locale.ROOT);

        Inventory inventory;
        if ("CHEST".equals(type)) {
            int rows = Math.max(1, Math.min(6, root.getInt("rows", 3)));
            inventory = Bukkit.createInventory(holder, rows * 9, title);
        } else {
            InventoryType inventoryType;
            try {
                inventoryType = InventoryType.valueOf(type);
            } catch (IllegalArgumentException ex) {
                inventoryType = InventoryType.HOPPER;
            }
            inventory = Bukkit.createInventory(holder, inventoryType, title);
        }
        holder.bind(inventory);
        return inventory;
    }

    private static void place(Inventory inventory, ConfigurationSection node,
                              org.bukkit.inventory.ItemStack stack) {
        for (int slot : slots(node)) {
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, stack);
            }
        }
    }

    private static void bind(RebirthHolder holder, ConfigurationSection node,
                             java.util.function.Consumer<Player> action) {
        for (int slot : slots(node)) {
            holder.action(slot, action);
        }
    }

    private static List<Integer> slots(ConfigurationSection node) {
        if (node.contains("slots")) {
            return node.getIntegerList("slots");
        }
        return List.of(node.getInt("slot", 0));
    }
}
