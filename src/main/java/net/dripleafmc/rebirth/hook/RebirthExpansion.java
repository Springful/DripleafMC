package net.dripleafmc.rebirth.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.dripleafmc.rebirth.RebirthPlugin;
import net.dripleafmc.rebirth.core.CheckResult;
import net.dripleafmc.rebirth.core.RequirementState;
import net.dripleafmc.rebirth.tier.RebirthPath;
import net.dripleafmc.rebirth.tier.RebirthTier;
import net.dripleafmc.rebirth.util.Text;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * The plugin's own placeholders, exposed to TAB, chat, holograms, scoreboards
 * and anything else on the server.
 *
 * <pre>
 *   %rebirth_tier%                 3
 *   %rebirth_tier_roman%           ɪɪɪ
 *   %rebirth_total%                lifetime rebirth count
 *   %rebirth_last_path%            standard | soul
 *   %rebirth_maxed%                true | false
 *   %rebirth_multiplier%           current sell/jobs bonus, e.g. 12
 *   %rebirth_next%                 4
 *   %rebirth_next_roman%           ɪᴠ
 *   %rebirth_next_cost%            formatted standard cost
 *   %rebirth_next_cost_soul%       formatted soul cost
 *   %rebirth_next_multiplier%      multiplier the next tier grants
 *   %rebirth_next_keys%            any reward value works: keys, cash, souls...
 *   %rebirth_can_standard%         true | false
 *   %rebirth_can_soul%             true | false
 *   %rebirth_progress%             overall completion of the next tier, 0-100
 *   %rebirth_cooldown%             remaining cooldown, formatted
 *   %rebirth_requirement_&lt;id&gt;_progress|target|percent|met%
 * </pre>
 */
public final class RebirthExpansion extends PlaceholderExpansion {

    private final RebirthPlugin plugin;

    public RebirthExpansion(RebirthPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "rebirth";
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
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        String key = params.toLowerCase(Locale.ROOT);

        switch (key) {
            case "tier":
                return String.valueOf(plugin.service().currentTier(player));
            case "tier_roman": {
                RebirthTier current = plugin.tiers().get(plugin.service().currentTier(player));
                return current == null ? plugin.settings().noneRoman() : current.roman();
            }
            case "total":
                return String.valueOf(plugin.store().get(player.getUniqueId()).total());
            case "last_path": {
                String path = plugin.store().get(player.getUniqueId()).path();
                return path == null ? "" : path;
            }
            case "maxed":
                return String.valueOf(plugin.service().maxed(player));
            case "multiplier":
                return trim(plugin.service().multiplier(player));
            case "cooldown":
                return plugin.service().formatDuration(plugin.service().cooldownRemaining(player));
            default:
                break;
        }

        if (plugin.service().maxed(player)) {
            return maxedFallback(key);
        }

        RebirthTier next = plugin.tiers().get(plugin.service().nextTier(player));
        if (next == null) {
            return maxedFallback(key);
        }

        switch (key) {
            case "next":
                return String.valueOf(next.level());
            case "next_roman":
                return next.roman();
            case "next_cost":
                return plugin.settings().numbers().display(next.cost(RebirthPath.STANDARD));
            case "next_cost_soul":
                return plugin.settings().numbers().display(next.cost(RebirthPath.SOUL));
            case "can_standard":
                return String.valueOf(plugin.service().check(player, RebirthPath.STANDARD).allowed());
            case "can_soul":
                return String.valueOf(plugin.service().check(player, RebirthPath.SOUL).allowed());
            case "progress":
                return String.valueOf(overallProgress(player));
            default:
                break;
        }

        if (key.startsWith("next_")) {
            return trim(next.value(key.substring(5)));
        }

        if (key.startsWith("requirement_")) {
            return requirement(player, next, key.substring("requirement_".length()));
        }

        return null;
    }

    private String requirement(Player player, RebirthTier tier, String rest) {
        int split = rest.lastIndexOf('_');
        if (split <= 0) {
            return "";
        }
        String id = rest.substring(0, split);
        String field = rest.substring(split + 1);

        for (var requirement : tier.requirements()) {
            if (!requirement.id().equalsIgnoreCase(id)) {
                continue;
            }
            RequirementState state = RequirementState.of(requirement, player);
            return switch (field) {
                case "progress" -> plugin.settings().numbers().progress(state.progress());
                case "target" -> plugin.settings().numbers().progress(state.target());
                case "percent" -> String.valueOf(Math.round(state.target() <= 0d ? 100d
                        : Math.min(100d, state.progress() / state.target() * 100d)));
                case "met" -> String.valueOf(state.met());
                case "name" -> Text.plain(requirement.display());
                default -> "";
            };
        }
        return "";
    }

    private long overallProgress(Player player) {
        CheckResult result = plugin.service().check(player, RebirthPath.STANDARD);
        List<RequirementState> states = result.requirements();
        if (states.isEmpty()) {
            return result.costMet() ? 100L : 0L;
        }
        double total = 0d;
        for (RequirementState state : states) {
            total += state.target() <= 0d ? 100d
                    : Math.min(100d, state.progress() / state.target() * 100d);
        }
        return Math.round(total / states.size());
    }

    private String maxedFallback(String key) {
        return key.startsWith("next") || key.startsWith("can_") || key.startsWith("requirement_")
                ? plugin.settings().maxedText()
                : null;
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
