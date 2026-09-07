package net.dripleaf.core.common.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.invoke.MethodHandle;

/**
 * mcMMO power level, natively where possible.
 *
 * <p>The Skript read this through a PlaceholderAPI string parse on every lore
 * line. The API call is a method handle resolved once; PAPI stays as the
 * fallback for installs whose API shape differs.
 */
public final class McMmoBridge implements Bridge {

    private static final String POWER_PLACEHOLDER = "%mcmmo_power_level%";

    private final PapiBridge papi;
    private MethodHandle powerLevel;
    private boolean present;

    public McMmoBridge(PapiBridge papi) {
        this.papi = papi;
    }

    public void connect() {
        powerLevel = null;
        present = Bukkit.getPluginManager().isPluginEnabled("mcMMO");
        if (!present) {
            return;
        }
        Class<?> api = Reflect.findClass("com.gmail.nossr50.api.ExperienceAPI");
        powerLevel = Reflect.staticMethod(api, "getPowerLevel", int.class, Player.class);
    }

    @Override
    public String name() {
        return "mcMMO";
    }

    @Override
    public boolean available() {
        return present;
    }

    @Override
    public String detail() {
        if (!present) {
            return "not installed";
        }
        return powerLevel != null ? "native API" : "placeholder fallback";
    }

    /** @return the player's power level, or 0 when mcMMO is absent */
    public int powerLevel(Player player) {
        if (powerLevel != null) {
            try {
                return (int) powerLevel.invoke(player);
            } catch (Throwable ex) {
                // Fall through to the placeholder rather than failing the check.
            }
        }
        if (!present) {
            return 0;
        }
        return (int) papi.resolveNumber(player, POWER_PLACEHOLDER, 0d);
    }
}
