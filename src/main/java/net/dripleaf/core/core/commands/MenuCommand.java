package net.dripleaf.core.core.commands;

import net.dripleaf.core.common.Services;
import net.dripleaf.core.common.command.CommandSpec;
import net.dripleaf.core.common.command.DripleafCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;

/**
 * {@code /menu} — the server menu players actually live in.
 *
 * <p>There is deliberately no layout in this class. The screen, its title, its
 * buttons, their icons, their order and what each one does all come from
 * {@code screens.main-menu} in {@code menus.yml}, resolved through
 * {@link net.dripleaf.core.common.ui.MenuActions}. Adding an entry is a config
 * block; so is removing one, or building a whole second menu and pointing a
 * button at it with {@code action: "screen:my-menu"}.
 *
 * <p>{@code /menu <id>} opens any other screen defined in the file, which is
 * what makes nested menus work without a command per menu.
 */
public final class MenuCommand extends DripleafCommand {

    public MenuCommand(Services services, CommandSpec spec) {
        super(services, spec);
    }

    @Override
    protected boolean run(CommandSender sender, Player player, String[] args) {
        String screen = args.length > 0 ? args[0] : "main-menu";

        // Opening arbitrary screens by name is a staff tool, not a player one:
        // some screens are sub-pages that assume context.
        if (args.length > 0 && !sender.hasPermission("dripleaf.menu.other")) {
            services.messages().send(sender, "errors.no-permission",
                    net.dripleaf.core.common.text.Ctx.of("permission", "dripleaf.menu.other"));
            return false;
        }
        if (!services.menus().has(screen)) {
            services.messages().send(sender, "ui.unknown-screen",
                    net.dripleaf.core.common.text.Ctx.of("screen", screen));
            return false;
        }
        services.actions().open(player, screen);
        return true;
    }

    @Override
    protected Collection<String> complete(CommandSender sender, String[] args) {
        if (args.length <= 1 && sender.hasPermission("dripleaf.menu.other")) {
            return filter(List.copyOf(new java.util.TreeSet<>(
                    services.menus().screenIds())), args);
        }
        return List.of();
    }
}
