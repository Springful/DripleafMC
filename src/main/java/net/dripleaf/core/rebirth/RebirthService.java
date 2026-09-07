package net.dripleaf.core.rebirth;

import net.dripleaf.core.api.CurrencyService;
import net.dripleaf.core.api.CurrencyType;
import net.dripleaf.core.api.RebirthApi;
import net.dripleaf.core.common.Services;
import net.dripleaf.core.common.sound.SoundService;
import net.dripleaf.core.common.storage.PlayerData;
import net.dripleaf.core.common.text.Ctx;
import net.dripleaf.core.common.text.Text;
import net.dripleaf.core.rebirth.requirement.Requirement;
import net.dripleaf.core.rebirth.requirement.RequirementState;
import net.dripleaf.core.rebirth.reward.Unlock;
import net.dripleaf.core.rebirth.reward.UnlockRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The only thing in this plugin that can move a player up a tier.
 *
 * <p>Both front ends — the dialog and the chest GUI — drive this and nothing
 * else, which is what keeps them from diverging.
 *
 * <p><b>Execution order matters and is not negotiable:</b>
 * <ol>
 *   <li>re-validate <em>all</em> requirements, not just money — the Skript only
 *       re-checked the balance at confirm time, which is a gap;</li>
 *   <li>apply sacrifices (money to zero, mcMMO reset);</li>
 *   <li>grant the tier: persist it, then run {@code commands}, then
 *       {@code soul-commands} if this is the soul path;</li>
 *   <li>inject starting cash;</li>
 *   <li>grant keys and souls;</li>
 *   <li>run the unlock commands for the newly reached tier;</li>
 *   <li>broadcast, sound, title.</li>
 * </ol>
 * If any step throws, the full state — player, tier, path, what completed — is
 * written to {@code logs/rebirth.log} before the exception propagates. A
 * half-applied rebirth is the worst possible outcome and staff need enough to
 * reconstruct it by hand.
 */
public final class RebirthService implements RebirthApi, Listener {

    /** A cached evaluation, so rendering one screen does not re-query eight times. */
    private record Cached(CheckResult result, long expiry) {
    }

    private final Services services;
    private final TierRegistry tiers;
    private final UnlockRegistry unlocks;
    private final RebirthSettings settings;
    private final Map<UUID, Map<RebirthPath, Cached>> cache = new ConcurrentHashMap<>();

    public RebirthService(Services services, TierRegistry tiers, UnlockRegistry unlocks,
                          RebirthSettings settings) {
        this.services = services;
        this.tiers = tiers;
        this.unlocks = unlocks;
        this.settings = settings;
    }

    // ------------------------------------------------------------------- api

    @Override
    public int tier(Player player) {
        if (settings.tierSource() == RebirthSettings.TierSource.PERMISSION) {
            return permissionTier(player);
        }
        return services.players().get(player).rebirthTier();
    }

    @Override
    public double sellMultiplier(Player player) {
        RebirthTier current = tiers.get(tier(player));
        return current == null ? 1d : 1d + current.multiplier() / 100d;
    }

    @Override
    public int maxTier() {
        return tiers.max();
    }

    /**
     * The Skript's behaviour, kept as an option: walk the groups downwards and
     * take the first that matches. Costs up to {@code max()} permission lookups,
     * which is exactly why {@code data} is the recommended source.
     */
    private int permissionTier(Player player) {
        for (int number = tiers.max(); number >= 1; number--) {
            String node = new Ctx().put("tier", number)
                    .applyRaw(settings.permissionFormat());
            if (player.hasPermission(node)) {
                return number;
            }
        }
        return 0;
    }

    public int nextTier(Player player) {
        return tier(player) + 1;
    }

    public boolean maxed(Player player) {
        return tiers.get(nextTier(player)) == null;
    }

    // ----------------------------------------------------------------- check

    /** Cached for {@code requirement-cache-millis}; forced fresh by {@link #checkFresh}. */
    public CheckResult check(Player player, RebirthPath path) {
        long ttl = settings.requirementCacheMillis();
        if (ttl <= 0L) {
            return checkFresh(player, path);
        }
        Map<RebirthPath, Cached> perPlayer =
                cache.computeIfAbsent(player.getUniqueId(), key -> new ConcurrentHashMap<>(2));
        Cached cached = perPlayer.get(path);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiry() > now) {
            return cached.result();
        }
        CheckResult result = checkFresh(player, path);
        perPlayer.put(path, new Cached(result, now + ttl));
        return result;
    }

    /** Always re-evaluates. Used at confirm time, where a stale answer is a bug. */
    public CheckResult checkFresh(Player player, RebirthPath path) {
        RebirthTier tier = tiers.get(nextTier(player));
        if (tier == null) {
            return new CheckResult(CheckResult.Status.MAXED, null, path,
                    BigDecimal.ZERO, BigDecimal.ZERO, List.of(), 0L);
        }

        BigDecimal cost = tier.cost(path);
        CurrencyService money = services.currencies().money();
        if (money == null || !money.available()) {
            return new CheckResult(CheckResult.Status.ECONOMY_UNAVAILABLE, tier, path,
                    cost, BigDecimal.ZERO, states(player, tier), 0L);
        }
        BigDecimal balance = money.balance(player);

        List<RequirementState> states = states(player, tier);
        long cooldown = cooldownRemaining(player);

        CheckResult.Status status;
        if (cooldown > 0L) {
            status = CheckResult.Status.COOLDOWN;
        } else if (states.stream().anyMatch(state -> !state.met())) {
            status = CheckResult.Status.REQUIREMENTS_UNMET;
        } else if (balance.compareTo(cost) < 0) {
            status = CheckResult.Status.INSUFFICIENT_FUNDS;
        } else {
            status = CheckResult.Status.OK;
        }
        return new CheckResult(status, tier, path, cost, balance, states, cooldown);
    }

    private List<RequirementState> states(Player player, RebirthTier tier) {
        List<RequirementState> out = new ArrayList<>(tier.requirements().size());
        for (Requirement requirement : tier.requirements()) {
            out.add(requirement.evaluate(player));
        }
        return out;
    }

    public long cooldownRemaining(Player player) {
        if (settings.cooldownSeconds() <= 0L
                || player.hasPermission("dripleaf.bypass.cooldown")) {
            return 0L;
        }
        long last = services.players().get(player).rebirthLast();
        if (last == 0L) {
            return 0L;
        }
        long ready = last + settings.cooldownSeconds() * 1000L;
        return Math.max(0L, ready - System.currentTimeMillis());
    }

    // --------------------------------------------------------------- perform

    /**
     * Runs a rebirth. Main thread only.
     *
     * @return {@link CheckResult.Status#OK} on success; anything else is the
     *         reason it was refused, and nothing was changed
     */
    public CheckResult.Status perform(Player player, RebirthPath path) {
        CheckResult result = checkFresh(player, path);
        if (!result.allowed()) {
            return result.status();
        }
        RebirthTier tier = result.tier();
        String stage = "start";

        try {
            CurrencyService money = services.currencies().money();

            // 2. Sacrifices. Money first, because the cost comes out of it.
            stage = "charge";
            if (!money.withdraw(player, result.cost())) {
                return CheckResult.Status.INSUFFICIENT_FUNDS;
            }
            stage = "sacrifice";
            if (settings.sacrificeMoney()) {
                money.set(player, BigDecimal.ZERO);
            }
            if (settings.sacrificeMcMmo() && !settings.mcMmoCommand().isBlank()) {
                console(settings.mcMmoCommand(), player, tier, path);
            }

            // 3. Persist before paying out. A crash here loses rewards, not the tier.
            stage = "persist";
            PlayerData data = services.players().get(player);
            long now = System.currentTimeMillis();
            data.rebirthTier(tier.number());
            data.rebirthTotal(data.rebirthTotal() + 1);
            data.rebirthLast(now);
            if (data.rebirthFirst() == 0L) {
                data.rebirthFirst(now);
            }
            data.lastPath(path.id());
            data.addRebirthSpent(result.cost());
            data.recordTierDate(tier.number(), now);

            stage = "commands";
            for (String command : tier.commands()) {
                console(command, player, tier, path);
            }
            if (path == RebirthPath.SOUL) {
                for (String command : tier.soulCommands()) {
                    console(command, player, tier, path);
                }
            }

            // 4-5. Starting cash, keys, souls.
            stage = "payout";
            if (tier.cash().signum() > 0) {
                money.deposit(player, tier.cash());
            }
            int souls = tier.souls(path);
            if (souls > 0) {
                CurrencyService soulService = services.currencies().get(CurrencyType.SOULS);
                if (soulService != null && soulService.available()) {
                    soulService.deposit(player, BigDecimal.valueOf(souls));
                }
                data.addSoulsEarned(BigDecimal.valueOf(souls));
            }

            // 6. Unlock commands for the tier just reached.
            stage = "unlocks";
            for (Unlock unlock : unlocks.forTier(tier.number())) {
                for (String command : unlock.commands()) {
                    console(command, player, tier, path);
                }
            }

            // 7. Broadcast, sound, title.
            stage = "announce";
            announce(player, tier, path);

            invalidate(player.getUniqueId());
            services.audit().rebirth(String.format(
                    "OK   %s (%s) tier=%d path=%s cost=%s keys=%d souls=%d",
                    player.getName(), player.getUniqueId(), tier.number(), path.id(),
                    result.cost().toPlainString(), tier.keys(), souls));
            return CheckResult.Status.OK;

        } catch (RuntimeException ex) {
            services.audit().rebirth(String.format(
                    "FAIL %s (%s) tier=%d path=%s failed at stage=%s: %s",
                    player.getName(), player.getUniqueId(), tier.number(), path.id(),
                    stage, ex));
            services.plugin().getLogger().log(java.util.logging.Level.SEVERE,
                    "Rebirth failed for " + player.getName() + " at stage " + stage, ex);
            throw ex;
        }
    }

    private void announce(Player player, RebirthTier tier, RebirthPath path) {
        Ctx ctx = context(player, tier, path);
        services.sounds().play(player, path == RebirthPath.SOUL
                ? SoundService.REBIRTH_SOUL : SoundService.REBIRTH_STANDARD);
        services.messages().sendBlock(player, path == RebirthPath.SOUL
                ? "rebirth.panel-soul" : "rebirth.panel-standard", ctx);

        if (!settings.broadcastFor(path)) {
            return;
        }
        for (String line : services.messages().rawList(path == RebirthPath.SOUL
                ? "rebirth.broadcast-soul" : "rebirth.broadcast-standard")) {
            Bukkit.broadcast(Text.parse(line, ctx.resolver()));
        }
    }

    private void console(String template, Player player, RebirthTier tier, RebirthPath path) {
        String command = context(player, tier, path).applyRaw(template);
        if (!command.isBlank()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }

    /** The substitutions available in commands, messages and menu templates. */
    public Ctx context(Player player, RebirthTier tier, RebirthPath path) {
        Ctx ctx = new Ctx()
                .put("player", player.getName())
                .put("path", path == null ? "" : path.id())
                .put("path_name", services.messages().raw(
                        "rebirth.path-name-" + (path == null ? "standard" : path.id())));
        if (tier == null) {
            return ctx;
        }
        return ctx
                .put("tier", tier.number())
                .put("numeral", tier.numeral())
                .put("tier_roman", tier.numeral())
                .put("keys", tier.keys())
                .put("cash", services.amounts().formatExact(tier.cash()))
                .put("souls", tier.souls(path == null ? RebirthPath.STANDARD : path))
                .put("multiplier", trim(tier.multiplier()))
                .put("cost", services.amounts().formatExact(
                        tier.cost(path == null ? RebirthPath.STANDARD : path)));
    }

    private static String trim(double value) {
        return value == Math.rint(value)
                ? String.valueOf((long) value)
                : String.valueOf(value);
    }

    // ----------------------------------------------------------- admin paths

    /** Sets a tier without running rewards. Logged by the caller. */
    public void setTier(Player player, int tier) {
        PlayerData data = services.players().get(player);
        data.rebirthTier(Math.max(0, tier));
        invalidate(player.getUniqueId());
    }

    /** Re-runs one tier's reward and unlock commands without charging for it. */
    public void grantRewards(Player player, RebirthTier tier, RebirthPath path) {
        for (String command : tier.commands()) {
            console(command, player, tier, path);
        }
        if (path == RebirthPath.SOUL) {
            for (String command : tier.soulCommands()) {
                console(command, player, tier, path);
            }
        }
        for (Unlock unlock : unlocks.forTier(tier.number())) {
            for (String command : unlock.commands()) {
                console(command, player, tier, path);
            }
        }
    }

    public void reset(Player player) {
        PlayerData data = services.players().get(player);
        data.rebirthTier(0);
        data.rebirthTotal(0);
        data.rebirthLast(0L);
        data.rebirthFirst(0L);
        data.rebirthSpent(BigDecimal.ZERO);
        data.soulsEarned(BigDecimal.ZERO);
        data.tierDates().clear();
        data.markDirty();
        invalidate(player.getUniqueId());
    }

    /**
     * Highest tier a player may see in the browser:
     * {@code (floor(T / block) + 1) × block}.
     */
    public int visibleCeiling(Player player) {
        int block = settings.infoVisibilityBlock();
        int current = tier(player);
        return (current / block + 1) * block;
    }

    public void invalidate(UUID uuid) {
        cache.remove(uuid);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        invalidate(event.getPlayer().getUniqueId());
    }

    public TierRegistry tiers() {
        return tiers;
    }

    public UnlockRegistry unlocks() {
        return unlocks;
    }

    public RebirthSettings settings() {
        return settings;
    }
}
