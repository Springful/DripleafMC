package net.dripleaf.core.common.hook;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.invoke.MethodHandle;

/**
 * WorldGuard, used for one question: <em>may this player build here?</em>
 *
 * <p>That single test is enough for random teleport to stay out of protected
 * land, and it deliberately goes through {@code WorldGuardPlugin}'s
 * {@code ProtectionQuery}, which takes plain Bukkit {@link Location} and
 * {@link Player} types. The region API proper would mean reflecting through
 * WorldGuard's own world-adapter classes, which change between versions — this
 * does not.
 *
 * <p>With WorldGuard absent every check passes, so the feature simply imposes
 * no extra restriction.
 */
public final class WorldGuardBridge implements Bridge {

    private Object query;
    private MethodHandle testBuild;
    private boolean present;

    public void connect() {
        query = null;
        testBuild = null;
        present = Bukkit.getPluginManager().isPluginEnabled("WorldGuard");
        if (!present) {
            return;
        }
        try {
            Class<?> pluginClass =
                    Reflect.findClass("com.sk89q.worldguard.bukkit.WorldGuardPlugin");
            Class<?> queryClass =
                    Reflect.findClass("com.sk89q.worldguard.bukkit.protection.query"
                            + ".ProtectionQuery");
            if (pluginClass == null || queryClass == null) {
                present = false;
                return;
            }
            MethodHandle instance = Reflect.staticMethod(pluginClass, "inst", pluginClass);
            MethodHandle createQuery =
                    Reflect.virtualMethod(pluginClass, "createProtectionQuery", queryClass);
            if (instance == null || createQuery == null) {
                present = false;
                return;
            }
            this.query = createQuery.invoke(instance.invoke());
            this.testBuild = Reflect.virtualMethod(queryClass, "testBuild", boolean.class,
                    Location.class, Player.class);
            if (testBuild == null) {
                present = false;
            }
        } catch (Throwable ex) {
            present = false;
        }
    }

    @Override
    public String name() {
        return "WorldGuard";
    }

    @Override
    public boolean available() {
        return present && testBuild != null;
    }

    /**
     * @return true when {@code player} may build at {@code location}, and true
     *         whenever WorldGuard is absent or fails to answer — this gates a
     *         convenience feature, so an unavailable check must not lock
     *         players out of it
     */
    public boolean canBuild(Player player, Location location) {
        if (!available()) {
            return true;
        }
        try {
            return (boolean) testBuild.invoke(query, location, player);
        } catch (Throwable ex) {
            return true;
        }
    }
}
