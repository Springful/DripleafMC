package net.dripleafmc.rebirth.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Reads mcMMO power levels without a compile-time dependency on mcMMO.
 * <p>
 * The method handle is resolved once at enable. If mcMMO is absent we fall back
 * to PlaceholderAPI, and if that is absent too the requirement simply reads 0
 * (so a misconfigured server fails closed rather than handing out free tiers).
 */
public final class McMmoBridge {

    private static final String PLACEHOLDER = "%mcmmo_power_level%";

    private final PapiBridge papi;
    private final MethodHandle getPowerLevel;
    private final boolean present;

    public McMmoBridge(PapiBridge papi) {
        this.papi = papi;
        MethodHandle handle = null;
        boolean found = false;
        if (Bukkit.getPluginManager().isPluginEnabled("mcMMO")) {
            try {
                Class<?> api = Class.forName("com.gmail.nossr50.api.ExperienceAPI");
                handle = MethodHandles.publicLookup().findStatic(api, "getPowerLevel",
                        MethodType.methodType(int.class, Player.class));
                found = true;
            } catch (ReflectiveOperationException ignored) {
                // Older or newer mcMMO signature; PAPI fallback covers it.
            }
        }
        this.getPowerLevel = handle;
        this.present = found;
    }

    public boolean present() {
        return present;
    }

    public double powerLevel(Player player) {
        if (getPowerLevel != null) {
            try {
                return (int) getPowerLevel.invokeExact(player);
            } catch (Throwable ignored) {
                // fall through
            }
        }
        return papi.number(player, PLACEHOLDER);
    }
}
