package net.dripleaf.core.common.hook;

import org.bukkit.Bukkit;

/**
 * LuckPerms, used for exactly two things: reading a player's legacy
 * {@code rebirth-N} group during the one-time migration, and confirming the
 * plugin is present so the reward commands that grant those groups are worth
 * dispatching.
 *
 * <p>Group grants themselves stay as editable console commands in
 * {@code tiers.yml} — external plugins keying off {@code group.rebirth-N} keep
 * working, but the group stops being the source of truth for a player's tier.
 */
public final class LuckPermsBridge implements Bridge {

    private boolean present;

    public void connect() {
        this.present = Bukkit.getPluginManager().isPluginEnabled("LuckPerms");
    }

    @Override
    public String name() {
        return "LuckPerms";
    }

    @Override
    public boolean available() {
        return present;
    }
}
