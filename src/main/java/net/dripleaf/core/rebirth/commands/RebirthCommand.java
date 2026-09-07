package net.dripleaf.core.rebirth.commands;

import net.dripleaf.core.common.Services;
import net.dripleaf.core.common.command.CommandSpec;
import net.dripleaf.core.common.command.DripleafCommand;
import net.dripleaf.core.common.text.Ctx;
import net.dripleaf.core.rebirth.RebirthModule;
import net.dripleaf.core.rebirth.RebirthPath;
import net.dripleaf.core.rebirth.RebirthTier;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * {@code /rebirth} and its subcommands.
 *
 * <p>Bare, it opens the path menu. {@code info} opens the tier browser,
 * {@code stats} the summary panel, and {@code admin …} the staff operations —
 * every one of which is also reachable from the admin panel, and every one of
 * which is logged.
 */
public final class RebirthCommand extends DripleafCommand {

    private final RebirthModule module;

    public RebirthCommand(Services services, CommandSpec spec, RebirthModule module) {
        super(services, spec);
        this.module = module;
    }

    @Override
    protected boolean playerOnly() {
        return false;
    }

    @Override
    protected boolean run(CommandSender sender, Player player, String[] args) {
        if (args.length == 0) {
            if (player == null) {
                services.messages().send(sender, "errors.players-only");
                return false;
            }
            module.screens().openMain(player);
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "info", "tiers" -> {
                if (player == null) {
                    services.messages().send(sender, "errors.players-only");
                    yield false;
                }
                module.screens().openBrowser(player);
                yield true;
            }
            case "stats" -> stats(sender, player, args);
            case "admin" -> admin(sender, args);
            default -> {
                services.messages().send(sender, "errors.usage",
                        Ctx.of("usage", "/rebirth [info|stats|admin]"));
                yield false;
            }
        };
    }

    private boolean stats(CommandSender sender, Player player, String[] args) {
        Player subject = player;
        if (args.length > 1) {
            subject = Bukkit.getPlayerExact(args[1]);
            if (subject == null) {
                services.messages().send(sender, "errors.player-not-found",
                        Ctx.of("player", args[1]));
                return false;
            }
        }
        if (player == null) {
            services.messages().send(sender, "errors.players-only");
            return false;
        }
        module.screens().openStats(player, subject);
        return true;
    }

    private boolean admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dripleaf.rebirth.admin")) {
            services.messages().send(sender, "errors.no-permission",
                    Ctx.of("permission", "dripleaf.rebirth.admin"));
            return false;
        }
        if (args.length < 2) {
            services.messages().send(sender, "errors.usage", Ctx.of("usage",
                    "/rebirth admin <set|grant|reset|reload> [player] [tier]"));
            return false;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("reload")) {
            module.reload();
            services.messages().send(sender, "admin.reloaded", Ctx.of("what", "rebirth"));
            services.audit().admin(sender.getName() + " reloaded rebirth data");
            return true;
        }

        if (args.length < 3) {
            services.messages().send(sender, "errors.usage", Ctx.of("usage",
                    "/rebirth admin " + action + " <player> [tier]"));
            return false;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            services.messages().send(sender, "errors.player-not-found",
                    Ctx.of("player", args[2]));
            return false;
        }

        switch (action) {
            case "reset" -> {
                int before = module.service().tier(target);
                module.service().reset(target);
                services.audit().admin(sender.getName() + " reset rebirth for "
                        + target.getName() + " (was tier " + before + ")");
                services.messages().send(sender, "rebirth.admin-reset",
                        Ctx.of("player", target.getName()));
                return true;
            }
            case "set", "grant" -> {
                if (args.length < 4) {
                    services.messages().send(sender, "errors.usage", Ctx.of("usage",
                            "/rebirth admin " + action + " <player> <tier>"));
                    return false;
                }
                int tier;
                try {
                    tier = Integer.parseInt(args[3]);
                } catch (NumberFormatException ex) {
                    services.messages().send(sender, "errors.not-a-number",
                            Ctx.of("input", args[3]));
                    return false;
                }
                int before = module.service().tier(target);

                if (action.equals("set")) {
                    module.service().setTier(target, tier);
                } else {
                    RebirthTier definition = module.service().tiers().get(tier);
                    if (definition == null) {
                        services.messages().send(sender, "rebirth.admin-unknown-tier",
                                Ctx.of("tier", String.valueOf(tier)));
                        return false;
                    }
                    module.service().setTier(target, tier);
                    module.service().grantRewards(target, definition, RebirthPath.STANDARD);
                }

                services.audit().admin(String.format("%s %s tier for %s: %d -> %d",
                        sender.getName(), action, target.getName(), before, tier));
                services.messages().send(sender, "rebirth.admin-set", new Ctx()
                        .put("player", target.getName())
                        .put("before", String.valueOf(before))
                        .put("after", String.valueOf(tier)));
                return true;
            }
            default -> {
                services.messages().send(sender, "errors.usage", Ctx.of("usage",
                        "/rebirth admin <set|grant|reset|reload>"));
                return false;
            }
        }
    }

    @Override
    protected Collection<String> complete(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            List<String> options = new ArrayList<>(List.of("info", "stats"));
            if (sender.hasPermission("dripleaf.rebirth.admin")) {
                options.add("admin");
            }
            return filter(options, args);
        }
        if (args[0].equalsIgnoreCase("admin")) {
            if (args.length == 2) {
                return filter(List.of("set", "grant", "reset", "reload"), args);
            }
            if (args.length == 3) {
                return onlineNames(args);
            }
        }
        if (args[0].equalsIgnoreCase("stats") && args.length == 2) {
            return onlineNames(args);
        }
        return List.of();
    }
}
