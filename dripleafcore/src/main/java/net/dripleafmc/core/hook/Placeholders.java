package net.dripleafmc.core.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.dripleafmc.core.DripleafCore;
import net.dripleafmc.core.profile.Profile;
import net.dripleafmc.core.util.Num;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * PlaceholderAPI output so TAB, scoreboards and holograms can render this data
 * without DripleafCore having to draw any of those surfaces itself.
 */
public final class Placeholders extends PlaceholderExpansion {

    private final DripleafCore core;

    public Placeholders(DripleafCore core) {
        this.core = core;
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
        return core.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offline, @NotNull String params) {
        if (offline == null) return "";
        Profile profile = core.profiles().get(offline.getUniqueId());
        if (profile == null) return "";

        return switch (params.toLowerCase(java.util.Locale.ROOT)) {
            case "shards" -> Long.toString(profile.shards);
            case "shards_short" -> Num.compact(profile.shards);
            case "money" -> Num.money(core.money().balance(offline));
            case "money_short" -> Num.compact(core.money().balance(offline));
            case "kills" -> Integer.toString(profile.kills);
            case "deaths" -> Integer.toString(profile.deaths);
            case "kdr" -> String.format(java.util.Locale.US, "%.2f",
                    profile.deaths == 0 ? profile.kills : (double) profile.kills / profile.deaths);
            case "killstreak" -> Integer.toString(profile.killStreak);
            case "best_killstreak" -> Integer.toString(profile.bestStreak);
            case "mobs_killed" -> Integer.toString(profile.mobKills);
            case "blocks_broken" -> Num.compact(profile.blocksBroken);
            case "blocks_placed" -> Num.compact(profile.blocksPlaced);
            case "money_made" -> Num.compact(profile.moneyMade);
            case "money_spent" -> Num.compact(profile.moneySpent);
            case "playtime" -> Num.duration(profile.totalPlaytime());
            case "booster" -> profile.boosterActive() ? "true" : "false";
            case "booster_time" -> profile.boosterActive()
                    ? Num.duration((profile.boosterUntil - System.currentTimeMillis()) / 1000L) : "0s";
            case "homes" -> Integer.toString(core.homes().homes(offline.getUniqueId()).size());
            case "home_slots" -> offline instanceof Player player
                    ? Integer.toString(core.homes().slots(player)) : "";
            case "sell_multiplier" -> offline instanceof Player player
                    ? String.format(java.util.Locale.US, "%.2f", core.worth().multiplier(player, profile)) : "";
            case "combat" -> offline instanceof Player player && core.combat().tagged(player) ? "true" : "false";
            case "bounty" -> {
                var bounty = core.bounties().on(offline.getUniqueId());
                yield bounty == null ? "0" : Num.money(bounty.amount());
            }
            default -> null;
        };
    }
}
