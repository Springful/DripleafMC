package net.dripleaf.core;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.dripleaf.core.api.CurrencyService;
import net.dripleaf.core.api.CurrencyType;
import net.dripleaf.core.common.Services;
import net.dripleaf.core.common.command.DripleafCommand;
import net.dripleaf.core.core.CoreModule;
import net.dripleaf.core.core.leaderboard.LeaderboardService;
import net.dripleaf.core.rebirth.CheckResult;
import net.dripleaf.core.rebirth.RebirthModule;
import net.dripleaf.core.rebirth.RebirthPath;
import net.dripleaf.core.rebirth.RebirthTier;
import net.dripleaf.core.rebirth.requirement.RequirementState;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * The {@code %dripleaf_…%} expansion.
 *
 * <p>Every placeholder returns a sensible value for an offline or unknown
 * player rather than erroring — a scoreboard that throws is a scoreboard that
 * spams the console once per tick per player.
 */
public final class DripleafPlaceholders extends PlaceholderExpansion {

    private final DripleafCore plugin;
    private final Services services;
    private final CoreModule core;
    private final RebirthModule rebirth;

    public DripleafPlaceholders(DripleafCore plugin, Services services, CoreModule core,
                                RebirthModule rebirth) {
        this.plugin = plugin;
        this.services = services;
        this.core = core;
        this.rebirth = rebirth;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "dripleaf";
    }

    @Override
    public @NotNull String getAuthor() {
        return "DripleafMC";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offline, @NotNull String params) {
        String key = params.toLowerCase(Locale.ROOT);

        // Leaderboards work without an online player.
        if (key.startsWith("baltop_")) {
            return leaderboardName(core.leaderboards().balanceAt(index(key)));
        }
        if (key.startsWith("rebirthtop_")) {
            return leaderboardName(core.leaderboards().rebirthAt(index(key)));
        }

        if (offline == null) {
            return "";
        }

        switch (key) {
            case "balance" -> {
                return services.amounts().format(CurrencyType.MONEY, balance(offline,
                        CurrencyType.MONEY));
            }
            case "balance_raw" -> {
                return balance(offline, CurrencyType.MONEY).toPlainString();
            }
            case "shards" -> {
                return services.amounts().formatExact(balance(offline, CurrencyType.SHARDS));
            }
            case "souls" -> {
                return services.amounts().formatExact(balance(offline, CurrencyType.SOULS));
            }
            default -> {
                // Fall through to the online-only placeholders.
            }
        }

        Player player = offline.getPlayer();
        if (player == null) {
            return "";
        }

        return switch (key) {
            case "rebirth" -> String.valueOf(rebirth.service().tier(player));
            case "rebirth_numeral" -> numeral(rebirth.service().tier(player));
            case "rebirth_next" -> String.valueOf(rebirth.service().nextTier(player));
            case "rebirth_next_numeral" -> numeral(rebirth.service().nextTier(player));
            case "rebirth_max" -> String.valueOf(rebirth.service().maxTier());
            case "rebirth_maxed" -> String.valueOf(rebirth.service().maxed(player));
            case "rebirth_multiplier" -> "+" + Math.round(
                    (rebirth.service().sellMultiplier(player) - 1d) * 100d) + "%";
            case "rebirth_multiplier_raw" -> String.format(Locale.US, "%.2f",
                    rebirth.service().sellMultiplier(player));
            case "rebirth_progress" -> progress(player);
            case "rebirth_cost" -> cost(player, RebirthPath.STANDARD);
            case "rebirth_cost_soul" -> cost(player, RebirthPath.SOUL);
            case "rebirth_requirement" -> requirementName(player);
            case "rebirth_requirement_progress" -> requirementProgress(player);
            case "rebirth_can_standard" -> String.valueOf(
                    rebirth.service().check(player, RebirthPath.STANDARD).allowed());
            case "rebirth_can_soul" -> String.valueOf(
                    rebirth.service().check(player, RebirthPath.SOUL).allowed());
            case "rebirth_cooldown" -> DripleafCommand.formatDuration(
                    rebirth.service().cooldownRemaining(player));
            case "homes_used" -> String.valueOf(core.homes().used(player));
            case "homes_max" -> String.valueOf(core.homes().limit(player));
            case "playtime" -> DripleafCommand.formatDuration(
                    services.players().get(player).playtimeMillis());
            case "afk" -> String.valueOf(core.social().afk(player));
            case "ui_mode" -> services.ui().effectiveMode(player).name();
            case "bedrock" -> String.valueOf(services.ui().bedrock().isBedrock(player));
            default -> null;
        };
    }

    // --------------------------------------------------------------- helpers

    private BigDecimal balance(OfflinePlayer offline, CurrencyType type) {
        CurrencyService service = services.currencies() == null
                ? null : services.currencies().get(type);
        return service == null ? BigDecimal.ZERO : service.balance(offline);
    }

    private String numeral(int tier) {
        RebirthTier found = rebirth.tiers().get(tier);
        return found == null ? services.messages().raw("rebirth.numeral-none") : found.numeral();
    }

    private String progress(Player player) {
        CheckResult result = rebirth.service().check(player, RebirthPath.STANDARD);
        if (result.requirements().isEmpty()) {
            return "100";
        }
        double total = 0d;
        for (RequirementState state : result.requirements()) {
            total += state.fraction();
        }
        return String.valueOf(Math.round(total / result.requirements().size() * 100d));
    }

    private String cost(Player player, RebirthPath path) {
        RebirthTier next = rebirth.tiers().get(rebirth.service().nextTier(player));
        return next == null ? "-" : services.amounts()
                .formatExact(CurrencyType.MONEY, next.cost(path));
    }

    private String requirementName(Player player) {
        RebirthTier next = rebirth.tiers().get(rebirth.service().nextTier(player));
        if (next == null || next.requirements().isEmpty()) {
            return "-";
        }
        return next.requirements().get(next.requirements().size() - 1).display();
    }

    private String requirementProgress(Player player) {
        CheckResult result = rebirth.service().check(player, RebirthPath.STANDARD);
        if (result.requirements().isEmpty()) {
            return "-";
        }
        RequirementState state = result.requirements().get(result.requirements().size() - 1);
        return services.amounts().formatWhole(state.progress()) + " / "
                + services.amounts().formatWhole(state.target());
    }

    private static String leaderboardName(LeaderboardService.Entry entry) {
        return entry == null ? "-" : entry.name();
    }

    private static int index(String key) {
        int underscore = key.lastIndexOf('_');
        try {
            return Integer.parseInt(key.substring(underscore + 1));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
