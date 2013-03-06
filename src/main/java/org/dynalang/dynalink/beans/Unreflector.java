package org.dynalang.dynalink.beans;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Interface for creating unreflected method handles. This class is public for implementation purposes and is not part
 * of any supported API.
 */
public interface Unreflector {
    /**
     * Performs similarly to {@link java.lang.invoke.MethodHandles.Lookup#unreflect(Method)} for some lookup object,
     * also converting any encountered {@link IllegalAccessException} into an {@link IllegalAccessError}.
     *
     * @param m the method to unreflect
     * @return the unreflected method handle.
     */
    public MethodHandle unreflect(Method m);

    /**
     * Performs similarly to {@link java.lang.invoke.MethodHandles.Lookup#unreflectGetter(Field)} for some lookup
     * object, also converting any encountered {@link IllegalAccessException} into an {@link IllegalAccessError}.
     *
     * @param f the field for which a getter is unreflected
     * @return the unreflected field getter handle.
     */
    public MethodHandle unreflectGetter(Field f);

    /**
     * Performs similarly to {@link java.lang.invoke.MethodHandles.Lookup#unreflectSetter(Field)} for some lookup
     * object, also converting any encountered {@link IllegalAccessException} into an {@link IllegalAccessError}.
     *
     * @param f the field for which a setter is unreflected
     * @return the unreflected field setter handle.
     */
    public MethodHandle unreflectSetter(Field f);

    /**
     * Performs similarly to {@link java.lang.invoke.MethodHandles.Lookup#unreflectConstructor(Constructor)} for some
     * lookup object, also converting any encountered {@link IllegalAccessException} into an {@link IllegalAccessError}.
     *
     * @param c the constructor to unreflect
     * @return the unreflected constructor handle.
     */
    public MethodHandle unreflectConstructor(Constructor<?> c);

}
