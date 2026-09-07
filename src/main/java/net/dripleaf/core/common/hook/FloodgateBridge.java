package net.dripleaf.core.common.hook;

import org.bukkit.Bukkit;

import java.lang.invoke.MethodHandle;
import java.util.UUID;

/**
 * "Is this player on Bedrock?", answered by Floodgate or Geyser when either is
 * installed.
 *
 * <p>Both are reached reflectively so the plugin still compiles and runs on a
 * server that has neither. When neither answers, {@code BedrockService} falls
 * back to the configured placeholder and then to the username prefix.
 */
public final class FloodgateBridge implements Bridge {

    private Object floodgateApi;
    private MethodHandle floodgateIsBedrock;

    private Object geyserApi;
    private MethodHandle geyserIsBedrock;

    private String provider = "";

    public void connect() {
        floodgateApi = null;
        floodgateIsBedrock = null;
        geyserApi = null;
        geyserIsBedrock = null;
        provider = "";

        connectFloodgate();
        if (floodgateIsBedrock == null) {
            connectGeyser();
        }
    }

    private void connectFloodgate() {
        if (Bukkit.getPluginManager().getPlugin("floodgate") == null
                && Bukkit.getPluginManager().getPlugin("Floodgate") == null) {
            return;
        }
        Class<?> apiClass = Reflect.findClass("org.geysermc.floodgate.api.FloodgateApi");
        MethodHandle instance = Reflect.staticMethod(apiClass, "getInstance", apiClass);
        MethodHandle check = Reflect.virtualMethod(apiClass, "isFloodgatePlayer",
                boolean.class, UUID.class);
        if (instance == null || check == null) {
            return;
        }
        try {
            floodgateApi = instance.invoke();
            floodgateIsBedrock = check;
            provider = "Floodgate";
        } catch (Throwable ex) {
            floodgateApi = null;
            floodgateIsBedrock = null;
        }
    }

    private void connectGeyser() {
        if (Bukkit.getPluginManager().getPlugin("Geyser-Spigot") == null
                && Bukkit.getPluginManager().getPlugin("Geyser") == null) {
            return;
        }
        Class<?> apiClass = Reflect.findClass("org.geysermc.geyser.api.GeyserApi");
        MethodHandle instance = Reflect.staticMethod(apiClass, "api", apiClass);
        MethodHandle check = Reflect.virtualMethod(apiClass, "isBedrockPlayer",
                boolean.class, UUID.class);
        if (instance == null || check == null) {
            return;
        }
        try {
            geyserApi = instance.invoke();
            geyserIsBedrock = check;
            provider = "Geyser";
        } catch (Throwable ex) {
            geyserApi = null;
            geyserIsBedrock = null;
        }
    }

    @Override
    public String name() {
        return "Floodgate/Geyser";
    }

    @Override
    public boolean available() {
        return floodgateIsBedrock != null || geyserIsBedrock != null;
    }

    @Override
    public String detail() {
        return available() ? provider + " detected" : "not installed";
    }

    /**
     * @return {@code TRUE}/{@code FALSE} when a provider answered, {@code null}
     *         when none could — the caller then tries the other signals
     */
    public Boolean isBedrock(UUID uuid) {
        try {
            if (floodgateIsBedrock != null) {
                return (boolean) floodgateIsBedrock.invoke(floodgateApi, uuid);
            }
            if (geyserIsBedrock != null) {
                return (boolean) geyserIsBedrock.invoke(geyserApi, uuid);
            }
        } catch (Throwable ex) {
            // A provider that throws is a provider we stop trusting for this call.
            return null;
        }
        return null;
    }
}
