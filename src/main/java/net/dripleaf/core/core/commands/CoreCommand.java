package net.dripleaf.core.core.commands;

import net.dripleaf.core.DripleafCore;
import net.dripleaf.core.common.Services;
import net.dripleaf.core.common.command.CommandSpec;
import net.dripleaf.core.common.command.DripleafCommand;
import net.dripleaf.core.common.hook.Bridge;
import net.dripleaf.core.common.text.Ctx;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * {@code /dripleafcore} — status, reload, and the admin panel.
 *
 * <p>Reload <b>validates before applying</b>: the new configuration is parsed
 * into a fresh object graph and only swapped in if it holds together. A
 * malformed file leaves the live configuration alone and reports what is wrong,
 * because a reload that half-lands is worse than one that refuses.
 *
 * <p>Commands and listeners are never re-registered — that path leaks. Reload
 * swaps data only, and says how long it took.
 */
public final class CoreCommand extends DripleafCommand {

    private final DripleafCore plugin;

    public CoreCommand(Services services, CommandSpec spec, DripleafCore plugin) {
        super(services, spec);
        this.plugin = plugin;
    }

    @Override
    protected boolean playerOnly() {
        return false;
    }

    @Override
    protected boolean run(CommandSender sender, Player player, String[] args) {
        if (args.length == 0) {
            status(sender);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(sender, args.length > 1 ? args[1] : "all");
            case "admin" -> admin(sender, player);
            default -> {
                services.messages().send(sender, "errors.usage",
                        Ctx.of("usage", "/dripleafcore [reload|admin]"));
                yield false;
            }
        };
    }

    private void status(CommandSender sender) {
        services.messages().send(sender, "core.status-header", new Ctx()
                .put("version", plugin.getPluginMeta().getVersion()));
        services.messages().send(sender, "core.status-modules", new Ctx()
                .put("core", String.valueOf(plugin.core().enabled()))
                .put("rebirth", String.valueOf(plugin.rebirth().enabled())));
        services.messages().send(sender, "core.status-uptime", Ctx.of("uptime",
                formatDuration(System.currentTimeMillis() - plugin.startedAt())));
        for (Bridge bridge : services.hooks().all()) {
            services.messages().send(sender, "core.status-hook", new Ctx()
                    .put("hook", bridge.name())
                    .put("detail", bridge.detail()));
        }
    }

    private boolean reload(CommandSender sender, String what) {
        if (!sender.hasPermission("dripleaf.admin.reload")) {
            services.messages().send(sender, "errors.no-permission",
                    Ctx.of("permission", "dripleaf.admin.reload"));
            return false;
        }

        long started = System.nanoTime();
        int issuesBefore = services.configs().log().size();
        String scope = what.toLowerCase(Locale.ROOT);

        DripleafCore.ReloadReport report;
        try {
            report = plugin.reload(scope);
        } catch (RuntimeException ex) {
            services.plugin().getLogger().log(java.util.logging.Level.SEVERE,
                    "Reload failed; the previous configuration is still live", ex);
            services.messages().send(sender, "core.reload-failed",
                    Ctx.of("error", String.valueOf(ex.getMessage())));
            return false;
        }

        long millis = (System.nanoTime() - started) / 1_000_000L;
        int newIssues = services.configs().log().size() - issuesBefore;

        services.messages().send(sender, "core.reload-done", new Ctx()
                .put("scope", scope)
                .put("summary", report.summary())
                .put("millis", String.valueOf(millis)));
        if (newIssues > 0) {
            services.messages().send(sender, "core.reload-warnings",
                    Ctx.of("count", String.valueOf(newIssues)));
        }
        services.audit().admin(sender.getName() + " reloaded " + scope
                + " in " + millis + "ms");
        return true;
    }

    private boolean admin(CommandSender sender, Player player) {
        if (!sender.hasPermission("dripleaf.admin")) {
            services.messages().send(sender, "errors.no-permission",
                    Ctx.of("permission", "dripleaf.admin"));
            return false;
        }
        if (player == null) {
            services.messages().send(sender, "errors.players-only");
            return false;
        }
        plugin.core().admin().openRoot(player);
        return true;
    }

    @Override
    protected Collection<String> complete(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            return filter(List.of("reload", "admin"), args);
        }
        if (args[0].equalsIgnoreCase("reload")) {
            return filter(List.of("all", "core", "rebirth", "shops", "messages"), args);
        }
        return List.of();
    }
}
