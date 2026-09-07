package net.dripleaf.core.common.hook;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.invoke.MethodHandle;

/**
 * GriefPrevention claims.
 *
 * <p>Needed for one thing: the Rebirth XX "infinite fly in claims" unlock. A
 * player holding {@code dripleaf.fly.claims} keeps flight while inside a claim
 * they have build trust in; everywhere else the normal fly rules apply.
 *
 * <p>With GriefPrevention absent {@link #trustedHere} is always false, so the
 * unlock simply never fires and {@code /fly} behaves normally.
 */
public final class ClaimBridge implements Bridge {

    private Object dataStore;
    private MethodHandle getClaimAt;
    private MethodHandle allowBuild;
    private boolean present;

    public void connect() {
        dataStore = null;
        getClaimAt = null;
        allowBuild = null;
        present = Bukkit.getPluginManager().isPluginEnabled("GriefPrevention");
        if (!present) {
            return;
        }
        try {
            Class<?> gpClass = Reflect.findClass("me.ryanhamshire.GriefPrevention.GriefPrevention");
            Class<?> claimClass = Reflect.findClass("me.ryanhamshire.GriefPrevention.Claim");
            Class<?> storeClass = Reflect.findClass("me.ryanhamshire.GriefPrevention.DataStore");
            if (gpClass == null || claimClass == null || storeClass == null) {
                present = false;
                return;
            }
            Object instance = gpClass.getField("instance").get(null);
            dataStore = gpClass.getField("dataStore").get(instance);
            getClaimAt = Reflect.virtualMethod(storeClass, "getClaimAt", claimClass,
                    Location.class, boolean.class, claimClass);
            allowBuild = Reflect.virtualMethod(claimClass, "allowBuild", String.class,
                    Player.class, org.bukkit.Material.class);
        } catch (Throwable ex) {
            present = false;
        }
    }

    @Override
    public String name() {
        return "GriefPrevention";
    }

    @Override
    public boolean available() {
        return present && getClaimAt != null && allowBuild != null;
    }

    /** True when {@code player} may build where they are standing. */
    public boolean trustedHere(Player player) {
        if (!available()) {
            return false;
        }
        try {
            Object claim = getClaimAt.invoke(dataStore, player.getLocation(), true, null);
            if (claim == null) {
                return false;
            }
            // GriefPrevention returns null for "allowed" and a denial reason otherwise.
            return allowBuild.invoke(claim, player, org.bukkit.Material.STONE) == null;
        } catch (Throwable ex) {
            return false;
        }
    }
}
