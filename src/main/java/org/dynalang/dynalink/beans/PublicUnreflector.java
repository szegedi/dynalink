package org.dynalang.dynalink.beans;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Provides lookup of unreflected method handles through delegation to an instance of {@link PublicUnreflectorImpl}. If
 * Dynalink is run as trusted code, the delegate class is loaded into an isolated zero-permissions protection domain,
 * serving as a firebreak against an accidental privilege escalation downstream.
 */
class PublicUnreflector {
    private static final String UNREFLECTOR_IMPL_CLASS_NAME = "org.dynalang.dynalink.beans.PublicUnreflectorImpl";
    private static final Unreflector impl = createImpl();

    /**
     * Performs a {@link java.lang.invoke.MethodHandles.Lookup#unreflect(Method)}, converting any encountered
     * {@link IllegalAccessException} into an {@link IllegalAccessError}.
     *
     * @param m the method to unreflect
     * @return the unreflected method handle.
     */
    static MethodHandle unreflect(Method m) {
        return impl.unreflect(m);
    }

    /**
     * Performs a {@link java.lang.invoke.MethodHandles.Lookup#unreflectGetter(Field)}, converting any encountered
     * {@link IllegalAccessException} into an {@link IllegalAccessError}.
     *
     * @param f the field for which a getter is unreflected
     * @return the unreflected field getter handle.
     */
    static MethodHandle unreflectGetter(Field f) {
        return impl.unreflectGetter(f);
    }

    /**
     * Performs a {@link java.lang.invoke.MethodHandles.Lookup#unreflectSetter(Field)}, converting any encountered
     * {@link IllegalAccessException} into an {@link IllegalAccessError}.
     *
     * @param f the field for which a setter is unreflected
     * @return the unreflected field setter handle.
     */
    static MethodHandle unreflectSetter(Field f) {
        return impl.unreflectSetter(f);
    }

    static MethodHandle unreflectConstructor(Constructor<?> c) {
        return impl.unreflectConstructor(c);
    }

    private static Unreflector createImpl() {
        try {
            return (Unreflector)ZeroPermissionsClassLoader.loadClass(UNREFLECTOR_IMPL_CLASS_NAME).newInstance();
        } catch(InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e.getMessage(), e);
        } catch(SecurityException e) {
            // We don't have sufficient privileges to load the PublicUnreflectorImpl class into a separate protection
            // domain, so just create a new instance directly - in this scenario, Dynalink is not trusted code anyway.
            return createOrdinaryPublicUnreflector();
        }
    }

    private static Unreflector createOrdinaryPublicUnreflector() {
        return new PublicUnreflectorImpl();
    }

}
