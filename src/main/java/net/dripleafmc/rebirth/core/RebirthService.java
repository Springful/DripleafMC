package net.dripleafmc.rebirth.core;

import net.dripleafmc.rebirth.RebirthPlugin;
import net.dripleafmc.rebirth.data.Profile;
import net.dripleafmc.rebirth.requirement.Requirement;
import net.dripleafmc.rebirth.tier.PathSpec;
import net.dripleafmc.rebirth.tier.RebirthPath;
import net.dripleafmc.rebirth.tier.RebirthTier;
import net.dripleafmc.rebirth.util.Ctx;
import net.dripleafmc.rebirth.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The engine. Both front ends call exactly these methods; neither of them
 * contains any progression logic of its own.
 */
public final class RebirthService {

    private final RebirthPlugin plugin;

    public RebirthService(RebirthPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------- state

    /** Tiers already completed. 0 = never rebirthed. */
    public int currentTier(Player player) {
        return plugin.store().tier(player.getUniqueId());
    }

    /** The tier they would ascend INTO next. */
    public int nextTier(Player player) {
        return currentTier(player) + 1;
    }

    public boolean maxed(Player player) {
        int cap = Math.min(plugin.settings().maxTier(), plugin.tiers().highest());
        return currentTier(player) >= cap;
    }

    /** Total sell/jobs multiplier granted by the tier they currently hold. */
    public double multiplier(Player player) {
        RebirthTier tier = plugin.tiers().get(currentTier(player));
        return tier == null ? 0d : tier.value("multiplier");
    }

    // ---------------------------------------------------------------- check

    public CheckResult check(Player player, RebirthPath path) {
        if (maxed(player)) {
            return new CheckResult(CheckResult.Status.MAXED, path, null, List.of(), 0d, 0d, false, 0L);
        }

        RebirthTier tier = plugin.tiers().get(nextTier(player));
        if (tier == null) {
            return new CheckResult(CheckResult.Status.NO_TIER_DEFINED, path, null, List.of(), 0d, 0d, false, 0L);
        }

        long cooldown = cooldownRemaining(player);
        double cost = tier.cost(path);
        double balance = plugin.economy().getBalance(player);
        boolean costMet = balance >= cost;

        List<Requirement> requirements = tier.requirements();
        List<RequirementState> states = new ArrayList<>(requirements.size());
        boolean allMet = true;
        for (Requirement requirement : requirements) {
            RequirementState state = RequirementState.of(requirement, player);
            states.add(state);
            allMet &= state.met();
        }

        CheckResult.Status status;
        if (cooldown > 0L && !player.hasPermission("dripleafrebirth.bypass.cooldown")) {
            status = CheckResult.Status.COOLDOWN;
        } else if (!allMet) {
            status = CheckResult.Status.REQUIREMENTS_UNMET;
        } else if (!costMet) {
            status = CheckResult.Status.INSUFFICIENT_FUNDS;
        } else {
            status = CheckResult.Status.OK;
        }

        return new CheckResult(status, path, tier, List.copyOf(states), cost, balance, costMet, cooldown);
    }

    public long cooldownRemaining(Player player) {
        long cooldownMillis = plugin.settings().cooldownSeconds() * 1000L;
        if (cooldownMillis <= 0L) {
            return 0L;
        }
        long last = plugin.store().get(player.getUniqueId()).lastRebirth();
        if (last <= 0L) {
            return 0L;
        }
        return Math.max(0L, last + cooldownMillis - System.currentTimeMillis());
    }

    // -------------------------------------------------------------- execute

    /**
     * Performs the ascension. Returns the final {@link CheckResult.Status}:
     * {@code OK} on success, otherwise the reason it was refused.
     * <p>
     * Order of operations is deliberate: validate, take the money, sacrifice,
     * persist the new tier, then pay out. The tier is written before the reward
     * commands run so that any {@code <tier>} placeholder inside them, and any
     * plugin those commands touch, sees the new value.
     */
    public CheckResult.Status perform(Player player, RebirthPath path) {
        CheckResult result = check(player, path);
        if (!result.allowed()) {
            return result.status();
        }

        RebirthTier tier = result.tier();
        PathSpec spec = tier.path(path);
        double cost = result.cost();

        // Take the cost first; if Vault refuses, nothing else has happened yet.
        if (!plugin.economy().withdrawPlayer(player, cost).transactionSuccess()) {
            return CheckResult.Status.INSUFFICIENT_FUNDS;
        }

        Ctx ctx = context(player, tier, path, cost);

        if (plugin.settings().sacrificeFirst()) {
            sacrifice(player, ctx);
        }

        int previous = currentTier(player);
        Profile profile = plugin.store().get(player.getUniqueId());
        plugin.store().set(player.getUniqueId(),
                profile.advance(tier.level(), path.id(), System.currentTimeMillis()));
        ctx.put("prev_tier", previous);

        if (!plugin.settings().sacrificeFirst()) {
            sacrifice(player, ctx);
        }

        plugin.rewardExecutor().run(player, plugin.rewards().resolve(spec.rewardSets()), ctx);

        if (plugin.settings().broadcast()) {
            for (String line : plugin.rewards().broadcast(path)) {
                Bukkit.broadcast(Text.parse(line, ctx.resolver()));
            }
        }

        playSound(player, path == RebirthPath.SOUL
                ? plugin.settings().soundSoul()
                : plugin.settings().soundStandard());

        plugin.lang().send(player, "rebirth.success", ctx);
        plugin.papi().invalidate(player.getUniqueId());
        return CheckResult.Status.OK;
    }

    private void sacrifice(Player player, Ctx ctx) {
        if (plugin.rewards().wipeBalance()) {
            double remaining = plugin.economy().getBalance(player);
            if (remaining > 0d) {
                plugin.economy().withdrawPlayer(player, remaining);
            }
        }
        plugin.rewardExecutor().run(player, plugin.rewards().sacrifice(), ctx);
    }

    // --------------------------------------------------------------- render

    /** The placeholder bag shared by messages, lore, dialog bodies and commands. */
    public Ctx context(Player player, RebirthTier tier, RebirthPath path, double cost) {
        Ctx ctx = new Ctx()
                .put("player", player.getName())
                .put("uuid", player.getUniqueId().toString())
                .put("tier", tier.level())
                .put("tier_roman", tier.roman())
                .put("path", path == null ? "" : path.id())
                .put("path_name", path == null ? "" : plugin.lang()
                        .raw("components.path-name-" + path.id()))
                .put("cost", plugin.settings().numbers().display(cost))
                .put("cost_raw", String.valueOf((long) cost))
                .put("balance", plugin.settings().numbers().display(plugin.economy().getBalance(player)));

        for (Map.Entry<String, Double> entry : tier.values().entrySet()) {
            double value = entry.getValue();
            ctx.put(entry.getKey(), value == Math.rint(value)
                    ? String.valueOf((long) value)
                    : String.valueOf(value));
        }
        return ctx;
    }

    public void playSound(Player player, String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            player.playSound(player.getLocation(),
                    Sound.valueOf(key.toUpperCase(java.util.Locale.ROOT).replace('.', '_')), 1f, 1f);
        } catch (IllegalArgumentException ex) {
            // Namespaced string form (e.g. "ui.button.click") - supported directly.
            player.playSound(net.kyori.adventure.sound.Sound.sound(
                    net.kyori.adventure.key.Key.key(key),
                    net.kyori.adventure.sound.Sound.Source.MASTER, 1f, 1f));
        }
    }

    public String formatDuration(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long days = totalSeconds / 86400L;
        long hours = (totalSeconds % 86400L) / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        StringBuilder sb = new StringBuilder(16);
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.isEmpty()) sb.append(seconds).append('s');
        return sb.toString().trim();
    }
}
