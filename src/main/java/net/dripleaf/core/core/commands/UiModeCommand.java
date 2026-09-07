package net.dripleaf.core.core.commands;

import net.dripleaf.core.common.Services;
import net.dripleaf.core.common.command.CommandSpec;
import net.dripleaf.core.common.command.DripleafCommand;
import net.dripleaf.core.common.text.Ctx;
import net.dripleaf.core.common.text.Glyphs;
import net.dripleaf.core.common.text.Palette;
import net.dripleaf.core.common.ui.ButtonStyle;
import net.dripleaf.core.common.ui.Screen;
import net.dripleaf.core.common.ui.ScreenButton;
import net.dripleaf.core.common.ui.UiMode;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * {@code /uimode} — the menu-surface toggle.
 *
 * <p>This is the switch that makes "which of these actually works for my
 * players?" an answerable question rather than a guess. A player sets
 * {@code dialog} or {@code chest} and <em>every</em> menu in the plugin —
 * shops, warps, homes, kits, rebirth, the admin panel — redraws on that
 * surface, immediately, with no restart and no config edit. {@code auto} hands
 * them back to the server default, which is dialogs for Java and chest GUIs for
 * Bedrock.
 *
 * <p>Bedrock players are the reason it exists, and they are exactly who is
 * least able to read a wiki page about it: hence the picker screen when the
 * command is run bare, drawn on whichever surface they are currently on.
 *
 * <p>{@code /uimode <player> <mode>} sets it for someone else, for staff
 * diagnosing a report of "the menu doesn't open".
 */
public final class UiModeCommand extends DripleafCommand {

    public UiModeCommand(Services services, CommandSpec spec) {
        super(services, spec);
    }

    @Override
    protected boolean run(CommandSender sender, Player player, String[] args) {
        if (!services.ui().overrideAllowed()) {
            services.messages().send(sender, "ui.override-disabled");
            return false;
        }

        if (args.length == 0) {
            openPicker(player);
            return true;
        }

        // Two forms: "/uimode <mode>" for yourself, "/uimode <player> <mode>" for staff.
        if (args.length >= 2) {
            if (!sender.hasPermission("dripleaf.uimode.other")) {
                services.messages().send(sender, "errors.no-permission",
                        Ctx.of("permission", "dripleaf.uimode.other"));
                return false;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                services.messages().send(sender, "errors.player-not-found",
                        Ctx.of("player", args[0]));
                return false;
            }
            UiMode mode = parse(sender, args[1]);
            if (mode == null) {
                return false;
            }
            apply(target, mode);
            services.messages().send(sender, "ui.set-other", new Ctx()
                    .put("player", target.getName())
                    .put("mode", label(mode)));
            return true;
        }

        UiMode mode = parse(sender, args[0]);
        if (mode == null) {
            return false;
        }
        apply(player, mode);
        return true;
    }

    private UiMode parse(CommandSender sender, String raw) {
        UiMode mode = UiMode.from(raw, null);
        if (mode == null) {
            services.messages().send(sender, "ui.unknown-mode", Ctx.of("mode", raw));
        }
        return mode;
    }

    private void apply(Player target, UiMode mode) {
        services.ui().preference(target, mode);
        services.messages().send(target, "ui.set", new Ctx()
                .put("mode", label(mode))
                .put("effective", label(services.ui().effectiveMode(target))));
    }

    /**
     * The picker, drawn through the router — so a player on chest GUIs sees it
     * as a chest GUI, and switching to dialogs redraws it as a dialog. Seeing
     * the change land on the screen you are looking at is the whole point.
     */
    private void openPicker(Player player) {
        UiMode current = services.ui().preference(player);
        UiMode effective = services.ui().effectiveMode(player);
        boolean bedrock = services.ui().bedrock().isBedrock(player);

        Screen screen = Screen.of("uimode",
                        Palette.brand(services.messages().raw("ui.picker-title")))
                .line(services.messages().raw("ui.picker-intro"))
                .blank()
                .line(detail("ui.picker-current", label(current)))
                .line(detail("ui.picker-effective", label(effective)))
                .line(detail("ui.picker-platform", bedrock
                        ? services.messages().raw("ui.platform-bedrock")
                        : services.messages().raw("ui.platform-java")))
                .line(Palette.colour(Palette.DEEP_MUTED,
                        Glyphs.ITEM + " " + services.ui().bedrock().explain(player)))
                .blank()
                .button(option(player, UiMode.DIALOG, current, Material.WRITTEN_BOOK,
                        ButtonStyle.PRIMARY))
                .button(option(player, UiMode.CHEST, current, Material.CHEST,
                        ButtonStyle.SHARD))
                .button(option(player, UiMode.AUTO, current, Material.COMPARATOR,
                        ButtonStyle.NEUTRAL))
                .build();

        services.ui().open(player, screen);
    }

    private ScreenButton option(Player player, UiMode mode, UiMode current, Material icon,
                                ButtonStyle style) {
        boolean selected = current == mode;
        String key = "ui.option-" + mode.name().toLowerCase(Locale.ROOT);
        return ScreenButton.of("uimode-" + mode.name().toLowerCase(Locale.ROOT),
                        (selected ? Glyphs.MET + " " : "") + style.paint(label(mode)))
                .material(icon)
                .style(style)
                .glint(selected)
                .line(Palette.colour(Palette.MUTED,
                        Glyphs.ITEM + ' ' + services.messages().raw(key)))
                .action(clicker -> {
                    apply(clicker, mode);
                    // Redraw on the newly chosen surface so the change is visible at once.
                    services.ui().close(clicker);
                    services.schedulers().delaySync(() -> openPicker(clicker), 2L);
                })
                .build();
    }

    private String detail(String key, String value) {
        return Ctx.of("value", value).applyRaw(services.messages().raw(key));
    }

    private String label(UiMode mode) {
        return services.messages().raw("ui.mode-" + mode.name().toLowerCase(Locale.ROOT));
    }

    @Override
    protected Collection<String> complete(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            return filter(List.of("dialog", "chest", "auto"), args);
        }
        if (args.length == 2 && sender.hasPermission("dripleaf.uimode.other")) {
            return filter(List.of("dialog", "chest", "auto"), args);
        }
        return List.of();
    }
}
