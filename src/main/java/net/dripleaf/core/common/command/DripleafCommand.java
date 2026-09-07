package net.dripleaf.core.common.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.dripleaf.core.common.Services;
import net.dripleaf.core.common.text.Ctx;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * The shared base every core command extends.
 *
 * <p>Permission checks, console-versus-player guards, cooldowns, warmups and
 * bypass nodes are handled here, once, so a command class contains only what it
 * actually does. Getting the order right matters: permission, then player
 * guard, then cooldown, then warmup, and the cooldown only starts if the body
 * reports success.
 *
 * <p>Bypass nodes are {@code dripleaf.bypass.cooldown[.<id>]} and
 * {@code dripleaf.bypass.warmup[.<id>]}.
 */
public abstract class DripleafCommand implements BasicCommand {

    protected final Services services;
    private final CommandSpec spec;

    protected DripleafCommand(Services services, CommandSpec spec) {
        this.services = services;
        this.spec = spec;
    }

    public CommandSpec spec() {
        return spec;
    }

    public String id() {
        return spec.id();
    }

    /** Override for commands the console may run. */
    protected boolean playerOnly() {
        return true;
    }

    /**
     * The command body.
     *
     * @param player the sender as a player, or {@code null} for the console when
     *               {@link #playerOnly()} is false
     * @return true when the command succeeded — only then does the cooldown start
     */
    protected abstract boolean run(CommandSender sender, Player player, String[] args);

    /** Tab completion. Empty by default. */
    protected Collection<String> complete(CommandSender sender, String[] args) {
        return List.of();
    }

    // ------------------------------------------------------------- plumbing

    @Override
    public final void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();

        if (!spec.permission().isBlank() && !sender.hasPermission(spec.permission())) {
            services.messages().send(sender, "errors.no-permission",
                    Ctx.of("permission", spec.permission()));
            return;
        }

        Player player = sender instanceof Player p ? p : null;
        if (playerOnly() && player == null) {
            services.messages().send(sender, "errors.players-only");
            return;
        }

        if (player == null) {
            run(sender, null, args);
            return;
        }

        long remaining = bypassesCooldown(player) ? 0L
                : services.cooldowns().remaining(player, spec.id());
        if (remaining > 0L) {
            services.messages().send(sender, "errors.cooldown",
                    Ctx.of("time", formatDuration(remaining)));
            return;
        }

        double warmup = bypassesWarmup(player) ? 0d : spec.warmup();
        if (warmup <= 0d) {
            finish(sender, player, args);
            return;
        }

        if (services.warmups().isWarmingUp(player)) {
            services.messages().send(sender, "errors.already-warming-up");
            return;
        }
        services.warmups().begin(player, spec.id(), warmup,
                () -> finish(sender, player, args));
    }

    private void finish(CommandSender sender, Player player, String[] args) {
        boolean success;
        try {
            success = run(sender, player, args);
        } catch (Exception ex) {
            services.plugin().getLogger().log(java.util.logging.Level.SEVERE,
                    "/" + spec.id() + " failed", ex);
            services.messages().send(sender, "errors.internal");
            return;
        }
        if (success && player != null && !bypassesCooldown(player)) {
            services.cooldowns().start(player, spec.id(), spec.cooldown());
        }
    }

    @Override
    public final Collection<String> suggest(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (!spec.permission().isBlank() && !sender.hasPermission(spec.permission())) {
            return List.of();
        }
        return complete(sender, args);
    }

    @Override
    public final String permission() {
        return spec.permission().isBlank() ? null : spec.permission();
    }

    private boolean bypassesCooldown(Player player) {
        return player.hasPermission("dripleaf.bypass.cooldown")
                || player.hasPermission("dripleaf.bypass.cooldown." + spec.id());
    }

    private boolean bypassesWarmup(Player player) {
        return player.hasPermission("dripleaf.bypass.warmup")
                || player.hasPermission("dripleaf.bypass.warmup." + spec.id());
    }

    // --------------------------------------------------------------- helpers

    /** Online player names matching the last argument, for tab completion. */
    protected static List<String> onlineNames(String[] args) {
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(online.getName());
            }
        }
        return out;
    }

    protected static List<String> filter(Collection<String> options, String[] args) {
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>(options.size());
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(option);
            }
        }
        return out;
    }

    /** {@code 1h 5m 3s}, dropping empty units. */
    public static String formatDuration(long millis) {
        long seconds = Math.max(0L, millis) / 1000L;
        long days = seconds / 86_400L;
        long hours = seconds % 86_400L / 3_600L;
        long minutes = seconds % 3_600L / 60L;
        long secs = seconds % 60L;

        StringBuilder sb = new StringBuilder(24);
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0) {
            sb.append(minutes).append("m ");
        }
        if (secs > 0 || sb.isEmpty()) {
            sb.append(secs).append('s');
        }
        return sb.toString().trim();
    }
}
