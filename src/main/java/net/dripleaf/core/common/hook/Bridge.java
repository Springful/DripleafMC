package net.dripleaf.core.common.hook;

/**
 * A third-party integration.
 *
 * <p>Nothing outside {@code common/hook/} imports a third-party class. The
 * reason is not tidiness: a {@code NoClassDefFoundError} thrown from a listener
 * on a server already at its thread ceiling is very hard to diagnose. Every
 * bridge answers {@link #available()} honestly and no-ops otherwise, so the
 * plugin enables cleanly with all soft dependencies absent.
 */
public interface Bridge {

    String name();

    boolean available();

    /** One line for the admin Diagnostics screen. */
    default String detail() {
        return available() ? "connected" : "not installed";
    }
}
