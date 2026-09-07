package net.dripleaf.core.core.commands;

import net.dripleaf.core.common.Services;
import net.dripleaf.core.common.command.CommandSpec;
import net.dripleaf.core.common.command.DripleafCommand;
import net.dripleaf.core.common.text.Ctx;
import net.dripleaf.core.core.CoreModule;
import net.dripleaf.core.core.flight.FlightService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * {@code /flytime} — check your balance, or manage someone else's.
 *
 * <p>Bare, it reports how much flight the sender has left. The
 * {@code give|take|set} forms need {@code dripleaf.flytime.admin} and are what
 * the shard shop and rebirth rewards dispatch from the console:
 *
 * <pre>
 * commands:
 *   - "flytime give &lt;player&gt; 1h"
 * </pre>
 *
 * <p>Durations accept {@code 30s}, {@code 10m}, {@code 2h}, {@code 1d} or a
 * plain number of seconds.
 */
public final class FlightCommand extends DripleafCommand {

    private final CoreModule core;

    public FlightCommand(Services services, CommandSpec spec, CoreModule core) {
        super(services, spec);
        this.core = core;
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
            services.messages().send(sender, "flight.balance", new Ctx()
                    .put("player", player.getName())
                    .put("time", core.flight().describe(player)));
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("check") && args.length > 1) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                services.messages().send(sender, "errors.player-not-found",
                        Ctx.of("player", args[1]));
                return false;
            }
            services.messages().send(sender, "flight.balance", new Ctx()
                    .put("player", target.getName())
                    .put("time", core.flight().describe(target)));
            return true;
        }

        if (!List.of("give", "add", "take", "remove", "set").contains(action)) {
            services.messages().send(sender, "errors.usage", Ctx.of("usage",
                    "/flytime [check|give|take|set] <player> <duration>"));
            return false;
        }
        if (!sender.hasPermission("dripleaf.flytime.admin")) {
            services.messages().send(sender, "errors.no-permission",
                    Ctx.of("permission", "dripleaf.flytime.admin"));
            return false;
        }
        if (args.length < 3) {
            services.messages().send(sender, "errors.usage", Ctx.of("usage",
                    "/flytime " + action + " <player> <duration>"));
            return false;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            services.messages().send(sender, "errors.player-not-found",
                    Ctx.of("player", args[1]));
            return false;
        }
        long seconds = FlightService.parseDuration(args[2]);
        if (seconds < 0) {
            services.messages().send(sender, "flight.bad-duration",
                    Ctx.of("input", args[2]));
            return false;
        }

        long before = core.flight().remaining(target);
        switch (action) {
            case "give", "add" -> core.flight().grant(target, seconds);
            case "take", "remove" -> core.flight().grant(target, -seconds);
            default -> core.flight().set(target, seconds);
        }
        long after = core.flight().remaining(target);

        services.audit().admin(String.format("%s flytime %s %s %ds (%d -> %d)",
                sender.getName(), action, target.getName(), seconds, before, after));
        services.messages().send(sender, "flight.admin-applied", new Ctx()
                .put("action", action)
                .put("player", target.getName())
                .put("before", formatDuration(before * 1000L))
                .put("after", formatDuration(after * 1000L)));
        return true;
    }

    @Override
    protected Collection<String> complete(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            return filter(sender.hasPermission("dripleaf.flytime.admin")
                    ? List.of("check", "give", "take", "set")
                    : List.of("check"), args);
        }
        if (args.length == 2) {
            return onlineNames(args);
        }
        if (args.length == 3) {
            return filter(List.of("30s", "5m", "30m", "1h", "6h", "1d"), args);
        }
        return List.of();
    }
}
