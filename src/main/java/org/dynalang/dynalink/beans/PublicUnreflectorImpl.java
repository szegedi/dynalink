package org.dynalang.dynalink.beans;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Performs lookup of unreflected method handles by delegating to {@link MethodHandles#publicLookup()}. When Dynalink
 * runs as trusted code, this class is normally loaded into an isolated zero-permissions protection domain by
 * {@link PublicUnreflector} to stop any accidental privilege escalation. This class is only public for implementation
 * purposes, and is not part of any supported API.
 */
public class PublicUnreflectorImpl implements Unreflector {
    @Override
    public MethodHandle unreflect(Method m) {
        try {
            return MethodHandles.publicLookup().unreflect(m);
        } catch(IllegalAccessException e) {
            final IllegalAccessError ee = new IllegalAccessError("Failed to unreflect method " + m);
            ee.initCause(e);
            throw ee;
        }
    }

    @Override
    public MethodHandle unreflectGetter(Field f) {
        try {
            return MethodHandles.publicLookup().unreflectGetter(f);
        } catch(IllegalAccessException e) {
            final IllegalAccessError ee = new IllegalAccessError("Failed to unreflect getter for field " + f);
            ee.initCause(e);
            throw ee;
        }
    }

    @Override
    public MethodHandle unreflectSetter(Field f) {
        try {
            return MethodHandles.publicLookup().unreflectSetter(f);
        } catch(IllegalAccessException e) {
            final IllegalAccessError ee = new IllegalAccessError("Failed to unreflect setter for field " + f);
            ee.initCause(e);
            throw ee;
        }
    }

    @Override
    public MethodHandle unreflectConstructor(Constructor<?> c) {
        try {
            return MethodHandles.publicLookup().unreflectConstructor(c);
        } catch(IllegalAccessException e) {
            final IllegalAccessError ee = new IllegalAccessError("Failed to unreflect constructor " + c);
            ee.initCause(e);
            throw ee;
        }
    }
}
