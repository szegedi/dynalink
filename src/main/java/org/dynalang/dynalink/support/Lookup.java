/*
   Copyright 2009-2012 Attila Szegedi

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package org.dynalang.dynalink.support;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * A wrapper around MethodHandles.Lookup that masks checked exceptions in those cases when you're looking up methods
 * within your own codebase (therefore it is an error if they are not present).
 *
 * @author Attila Szegedi
 * @version $Id: $
 */
public class Lookup {
    private final MethodHandles.Lookup lookup;

    /**
     * Creates a new instance, bound to an instance of {@link java.lang.invoke.MethodHandles.Lookup}.
     *
     * @param lookup the {@link java.lang.invoke.MethodHandles.Lookup} it delegates to.
     */
    public Lookup(MethodHandles.Lookup lookup) {
        this.lookup = lookup;
    }

    public static final Lookup PUBLIC = new Lookup(MethodHandles.publicLookup());

    /**
     * Performs a {@link java.lang.invoke.MethodHandles.Lookup#unreflect(Method)}, converting any encountered
     * {@link IllegalAccessException} into a {@link BootstrapMethodError}.
     *
     * @param m the method to unreflect
     * @return the unreflected method handle.
     */
    public MethodHandle unreflect(Method m) {
        try {
            return lookup.unreflect(m);
        } catch(IllegalAccessException e) {
            final IllegalAccessError ee = new IllegalAccessError("Failed to unreflect method " + m);
            ee.initCause(e);
            throw ee;
        }
    }

    public MethodHandle unreflectGetter(Field f) {
        try {
            return lookup.unreflectGetter(f);
        } catch(IllegalAccessException e) {
            final IllegalAccessError ee = new IllegalAccessError("Failed to unreflect getter for field " + f);
            ee.initCause(e);
            throw ee;
        }
    }

    public MethodHandle unreflectSetter(Field f) {
        try {
            return lookup.unreflectSetter(f);
        } catch(IllegalAccessException e) {
            final IllegalAccessError ee = new IllegalAccessError("Failed to unreflect setter for field " + f);
            ee.initCause(e);
            throw ee;
        }
    }

    public MethodHandle unreflectConstructor(Constructor c) {
        try {
            return lookup.unreflectConstructor(c);
        } catch(IllegalAccessException e) {
            final IllegalAccessError ee = new IllegalAccessError("Failed to unreflect constructor " + c);
            ee.initCause(e);
            throw ee;
        }
    }

    /**
     * Performs a findSpecial on the underlying lookup, except for the backport where it rather uses unreflect.
     *
     * @param declaringClass class declaring the method
     * @param name the name of the method
     * @param type the type of the method
     * @return a method handle for the method
     * @throws BootstrapMethodError if the method does not exist or is inaccessible.
     */
    public MethodHandle findSpecial(Class<?> declaringClass, String name, MethodType type) {
        try {
            if(Backport.inUse) {
                final Method m = declaringClass.getDeclaredMethod(name, type.parameterArray());
                if(!Modifier.isPublic(declaringClass.getModifiers()) || !Modifier.isPublic(m.getModifiers())) {
                    m.setAccessible(true);
                }
                return unreflect(m);
            } else {
                return lookup.findSpecial(declaringClass, name, type, declaringClass);
            }
        } catch(IllegalAccessException e) {
            final IllegalAccessError ee = new IllegalAccessError("Failed to access special method " + methodDescription(
                    declaringClass, name, type));
            ee.initCause(e);
            throw ee;
        } catch(NoSuchMethodException e) {
            final NoSuchMethodError ee = new NoSuchMethodError("Failed to find special method " + methodDescription(
                    declaringClass, name, type));
            ee.initCause(e);
            throw ee;
        }
    }

    private static String methodDescription(Class<?> declaringClass, String name, MethodType type) {
        return declaringClass.getName() + "#" + name + type;
    }

    public MethodHandle findStatic(Class<?> declaringClass, String name, MethodType type) {
        try {
            return lookup.findStatic(declaringClass, name, type);
        } catch(IllegalAccessException e) {
            final IllegalAccessError ee = new IllegalAccessError("Failed to access static method " + methodDescription(
                    declaringClass, name, type));
            ee.initCause(e);
            throw ee;
        } catch(NoSuchMethodException e) {
            final NoSuchMethodError ee = new NoSuchMethodError("Failed to find static method " + methodDescription(
                    declaringClass, name, type));
            ee.initCause(e);
            throw ee;
        }
    }

    public MethodHandle findVirtual(Class<?> declaringClass, String name, MethodType type) {
        try {
            return lookup.findVirtual(declaringClass, name, type);
        } catch(IllegalAccessException e) {
            final IllegalAccessError ee = new IllegalAccessError("Failed to access virtual method " + methodDescription(
                    declaringClass, name, type));
            ee.initCause(e);
            throw ee;
        } catch(NoSuchMethodException e) {
            final NoSuchMethodError ee = new NoSuchMethodError("Failed to find virtual method " + methodDescription(
                    declaringClass, name, type));
            ee.initCause(e);
            throw ee;
        }
    }

    /**
     * Given a lookup, finds using {@link #findSpecial(Class, String, MethodType)} a method on that lookup's class.
     * Useful in classes' code for convenient linking to their own privates.
     * @param lookup the lookup for the class
     * @param name the name of the method
     * @param rtype the return type of the method
     * @param ptypes the parameter types of the method
     * @return the method handle for the method
     */
    public static MethodHandle findOwnSpecial(MethodHandles.Lookup lookup, String name, Class<?> rtype, Class<?>... ptypes) {
        return new Lookup(lookup).findSpecial(lookup.lookupClass(), name, MethodType.methodType(rtype, ptypes));
    }

    /**
     * Given a lookup, finds using {@link #findStatic(Class, String, MethodType)} a method on that lookup's class.
     * Useful in classes' code for convenient linking to their own privates. It's easier to use than {@code findStatic}
     * in that you can just list the parameter types, and don't have to specify lookup class.
     * @param lookup the lookup for the class
     * @param name the name of the method
     * @param rtype the return type of the method
     * @param ptypes the parameter types of the method
     * @return the method handle for the method
     */
    public static MethodHandle findOwnStatic(MethodHandles.Lookup lookup, String name, Class<?> rtype, Class<?>... ptypes) {
        return new Lookup(lookup).findOwnStatic(name, rtype, ptypes);
    }

    /**
     * Finds using {@link #findStatic(Class, String, MethodType)} a method on that lookup's class. Useful in classes'
     * code for convenient linking to their own privates. It's easier to use than {@code findStatic} in that you can
     * just list the parameter types, and don't have to specify lookup class.
     * @param lookup the lookup for the class
     * @param name the name of the method
     * @param rtype the return type of the method
     * @param ptypes the parameter types of the method
     * @return the method handle for the method
     */
    public MethodHandle findOwnStatic(String name, Class<?> rtype, Class<?>... ptypes) {
        return findStatic(lookup.lookupClass(), name, MethodType.methodType(rtype, ptypes));
    }
}
