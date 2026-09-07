package net.dripleaf.core.common.hook;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * The small amount of reflection the optional bridges need.
 *
 * <p>Handles are resolved once at connect time and cached. If a dependency
 * changes its API the bridge degrades to unavailable at start-up with a log
 * line, instead of throwing from inside a listener later.
 */
final class Reflect {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.publicLookup();

    private Reflect() {
    }

    static Class<?> findClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException | LinkageError ex) {
            return null;
        }
    }

    static MethodHandle staticMethod(Class<?> owner, String name, Class<?> returns,
                                     Class<?>... params) {
        if (owner == null) {
            return null;
        }
        try {
            return LOOKUP.findStatic(owner, name, MethodType.methodType(returns, params));
        } catch (ReflectiveOperationException | LinkageError ex) {
            return null;
        }
    }

    static MethodHandle virtualMethod(Class<?> owner, String name, Class<?> returns,
                                      Class<?>... params) {
        if (owner == null) {
            return null;
        }
        try {
            return LOOKUP.findVirtual(owner, name, MethodType.methodType(returns, params));
        } catch (ReflectiveOperationException | LinkageError ex) {
            return null;
        }
    }
}
