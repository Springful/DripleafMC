package net.dripleafmc.rebirth.ui.dialog;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.dripleafmc.rebirth.RebirthPlugin;
import net.dripleafmc.rebirth.config.ConfigFile;
import net.dripleafmc.rebirth.core.CheckResult;
import net.dripleafmc.rebirth.tier.RebirthPath;
import net.dripleafmc.rebirth.tier.RebirthTier;
import net.dripleafmc.rebirth.ui.RebirthUI;
import net.dripleafmc.rebirth.util.Ctx;
import net.dripleafmc.rebirth.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Native Paper Dialog front end (ui.mode: DIALOG, Minecraft 26.x).
 * <p>
 * Buttons use {@link DialogAction#customClick(io.papermc.paper.registry.data.dialog.action.DialogActionCallback,
 * ClickCallback.Options)} with a single use, so no listener has to demux keys and
 * no callback outlives the screen it belongs to.
 */
public final class DialogUI implements RebirthUI {

    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(java.time.Duration.ofMinutes(5))
            .build();

    private final RebirthPlugin plugin;
    private YamlConfiguration cfg;

    public DialogUI(RebirthPlugin plugin) {
        this.plugin = plugin;
        this.cfg = ConfigFile.load(plugin, "dialogs.yml");
    }

    @Override
    public void invalidate() {
        this.cfg = ConfigFile.load(plugin, "dialogs.yml");
    }

    // ------------------------------------------------------------ main view

    @Override
    public void openMain(Player player) {
        if (plugin.service().maxed(player)) {
            openMaxed(player);
            return;
        }

        RebirthTier tier = plugin.tiers().get(plugin.service().nextTier(player));
        if (tier == null) {
            plugin.lang().send(player, "rebirth.maxed");
            return;
        }

        ConfigurationSection root = cfg.getConfigurationSection("main");
        if (root == null) {
            plugin.getLogger().warning("dialogs.yml is missing the 'main:' section.");
            return;
        }

        Map<RebirthPath, CheckResult> results = new EnumMap<>(RebirthPath.class);
        for (RebirthPath path : RebirthPath.values()) {
            results.put(path, plugin.service().check(player, path));
        }

        CheckResult standard = results.get(RebirthPath.STANDARD);
        CheckResult soul = results.get(RebirthPath.SOUL);

        Ctx ctx = plugin.service().context(player, tier, null, standard.cost());
        ctx.put("soul_cost", plugin.settings().numbers().display(soul.cost()));
        ctx.put("max_tier", plugin.tiers().highest());

        Map<String, List<Component>> expansions = Map.of(
                "requirements", plugin.renderer().requirements(standard),
                "rewards", plugin.renderer().parseAll(root.getStringList("rewards"), ctx),
                "unlocks", plugin.renderer().parseAll(tier.unlocks(), ctx));

        List<DialogBody> body = bodies(
                plugin.renderer().expand(root.getStringList("body"), ctx, expansions));

        List<ActionButton> buttons = new ArrayList<>(3);
        ConfigurationSection buttonSection = root.getConfigurationSection("buttons");
        if (buttonSection != null) {
            buttons.add(pathButton(buttonSection.getConfigurationSection("standard"),
                    ctx, standard, RebirthPath.STANDARD));
            buttons.add(pathButton(buttonSection.getConfigurationSection("soul"),
                    ctx, soul, RebirthPath.SOUL));

            ConfigurationSection shop = buttonSection.getConfigurationSection("soulshop");
            if (shop != null) {
                String command = shop.getString("command", "");
                buttons.add(button(shop, ctx, false, target -> {
                    target.closeDialog();
                    if (!command.isBlank()) {
                        target.performCommand(command);
                    }
                }));
            }
        }

        ActionButton exit = null;
        ConfigurationSection exitSection = buttonSection == null
                ? null
                : buttonSection.getConfigurationSection("exit");
        if (exitSection != null) {
            exit = ActionButton.builder(Text.parse(exitSection.getString("label", "Close"),
                    ctx.resolver())).build();
        }

        int columns = Math.max(1, root.getInt("columns", 1));
        int width = clampWidth(root.getInt("button-width", 220));
        boolean escape = root.getBoolean("can-close-with-escape", true);

        ActionButton finalExit = exit;
        player.showDialog(Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Text.parse(root.getString("title", "Rebirth"), ctx.resolver()))
                        .canCloseWithEscape(escape)
                        .body(body)
                        .build())
                .type(DialogType.multiAction(sized(buttons, width))
                        .exitAction(finalExit)
                        .columns(columns)
                        .build())));

        plugin.service().playSound(player, plugin.settings().soundOpen());
    }

    private ActionButton pathButton(ConfigurationSection node, Ctx ctx,
                                    CheckResult result, RebirthPath path) {
        boolean unlocked = result.allowed();
        return button(node, ctx, !unlocked, target -> {
            if (!unlocked) {
                plugin.service().playSound(target, plugin.settings().soundDenied());
                denied(target, result, path);
                return;
            }
            openConfirm(target, path);
        });
    }

    private ActionButton button(ConfigurationSection node, Ctx ctx, boolean locked,
                                Consumer<Player> action) {
        String label = locked
                ? node.getString("locked-label", node.getString("label", ""))
                : node.getString("label", "");
        String tooltip = locked
                ? node.getString("locked-tooltip", node.getString("tooltip", ""))
                : node.getString("tooltip", "");

        ActionButton.Builder builder = ActionButton.builder(Text.parse(label, ctx.resolver()));
        if (!tooltip.isBlank()) {
            builder.tooltip(Text.parse(tooltip, ctx.resolver()));
        }
        builder.action(DialogAction.customClick((response, audience) -> {
            if (audience instanceof Player clicker) {
                action.accept(clicker);
            }
        }, ONCE));
        return builder.build();
    }

    // --------------------------------------------------------- confirm view

    @Override
    public void openConfirm(Player player, RebirthPath path) {
        CheckResult result = plugin.service().check(player, path);
        if (!result.allowed()) {
            denied(player, result, path);
            return;
        }

        ConfigurationSection root = cfg.getConfigurationSection("confirm");
        if (root == null) {
            plugin.getLogger().warning("dialogs.yml is missing the 'confirm:' section.");
            return;
        }

        RebirthTier tier = result.tier();
        Ctx ctx = plugin.service().context(player, tier, path, result.cost());
        ctx.put("path_cost", plugin.settings().numbers().display(result.cost()));

        List<DialogBody> body = bodies(plugin.renderer().parseAll(root.getStringList("body"), ctx));

        ConfigurationSection yes = root.getConfigurationSection("yes-button");
        ConfigurationSection no = root.getConfigurationSection("no-button");

        ActionButton confirm = button(yes, ctx, false, target -> confirm(target, path));
        ActionButton cancel = button(no, ctx, false, target -> {
            plugin.lang().send(target, "rebirth.cancelled");
            openMain(target);
        });

        boolean escape = root.getBoolean("can-close-with-escape", true);

        player.showDialog(Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Text.parse(root.getString("title", "Confirm"), ctx.resolver()))
                        .canCloseWithEscape(escape)
                        .body(body)
                        .build())
                .type(DialogType.confirmation(confirm, cancel))));
    }

    private void confirm(Player player, RebirthPath path) {
        player.closeDialog();
        CheckResult.Status status = plugin.service().perform(player, path);
        if (status == CheckResult.Status.OK) {
            return;
        }
        plugin.service().playSound(player, plugin.settings().soundDenied());
        denied(player, plugin.service().check(player, path), path);
    }

    private void openMaxed(Player player) {
        ConfigurationSection root = cfg.getConfigurationSection("maxed");
        if (root == null) {
            plugin.lang().send(player, "rebirth.maxed");
            return;
        }
        Ctx ctx = new Ctx()
                .put("player", player.getName())
                .put("max_tier", plugin.tiers().highest());

        List<DialogBody> body = bodies(plugin.renderer().parseAll(root.getStringList("body"), ctx));
        ConfigurationSection ok = root.getConfigurationSection("ok-button");
        ActionButton okButton = ActionButton.builder(Text.parse(
                ok == null ? "Close" : ok.getString("label", "Close"), ctx.resolver())).build();

        player.showDialog(Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Text.parse(root.getString("title", "Rebirth"), ctx.resolver()))
                        .body(body)
                        .build())
                .type(DialogType.notice(okButton))));
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

    // ---------------------------------------------------------------- utils

    private static List<DialogBody> bodies(List<Component> lines) {
        List<DialogBody> out = new ArrayList<>(lines.size());
        for (Component line : lines) {
            out.add(DialogBody.plainMessage(line));
        }
        return out;
    }

    private static List<ActionButton> sized(List<ActionButton> buttons, int width) {
        List<ActionButton> out = new ArrayList<>(buttons.size());
        for (ActionButton button : buttons) {
            out.add(ActionButton.create(button.label(), button.tooltip(), width, button.action()));
        }
        return out;
    }

    private static int clampWidth(int width) {
        return Math.max(1, Math.min(1024, width));
    }
}
