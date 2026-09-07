package net.dripleaf.core.common.hook;

import org.bukkit.Bukkit;

/**
 * ExcellentCrates. Keys are granted by console command from {@code tiers.yml},
 * so this bridge only reports presence — enough to warn at start-up that a tier
 * promising crate keys has nothing to deliver them.
 */
public final class CratesBridge implements Bridge {

    private boolean present;

    public void connect() {
        this.present = Bukkit.getPluginManager().isPluginEnabled("ExcellentCrates");
    }

    @Override
    public String name() {
        return "ExcellentCrates";
    }

    @Override
    public boolean available() {
        return present;
    }
}
