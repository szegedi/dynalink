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

    /**
     * A canonical Lookup object that wraps {@link MethodHandles#publicLookup()}.
     */
    public static final Lookup PUBLIC = new Lookup(MethodHandles.publicLookup());

    /**
     * Performs a {@link java.lang.invoke.MethodHandles.Lookup#unreflect(Method)}, converting any encountered
     * {@link IllegalAccessException} into an {@link IllegalAccessError}.
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


    /**
     * Performs a {@link java.lang.invoke.MethodHandles.Lookup#unreflectGetter(Field)}, converting any encountered
     * {@link IllegalAccessException} into an {@link IllegalAccessError}.
     *
     * @param f the field for which a getter is unreflected
     * @return the unreflected field getter handle.
     */
    public MethodHandle unreflectGetter(Field f) {
        try {
            return lookup.unreflectGetter(f);
        } catch(IllegalAccessException e) {
            final IllegalAccessError ee = new IllegalAccessError("Failed to unreflect getter for field " + f);
            ee.initCause(e);
            throw ee;
        }
    }

    /**
     * Performs a {@link java.lang.invoke.MethodHandles.Lookup#findGetter(Class, String, Class)}, converting any
     * encountered {@link IllegalAccessException} into an {@link IllegalAccessError} and {@link NoSuchFieldException}
     * into a {@link NoSuchFieldError}.
     *
     * @param refc the class declaring the field
     * @param name the name of the field
     * @param type the type of the field
     * @return the unreflected field getter handle.
     * @throws IllegalAccessError if the field is inaccessible.
     * @throws NoSuchFieldError if the field does not exist.
     */
    public MethodHandle findGetter(Class<?>refc, String name, Class<?> type) {
        try {
            return lookup.findGetter(refc, name, type);
        } catch(IllegalAccessException e) {
            final IllegalAccessError ee = new IllegalAccessError("Failed to access getter for field " + refc.getName() +
                    "." + name + " of type " + type.getName());
            ee.initCause(e);
            throw ee;
        } catch(NoSuchFieldException e) {
            final NoSuchFieldError ee = new NoSuchFieldError("Failed to find getter for field " + refc.getName() +
                    "." + name + " of type " + type.getName());
            ee.initCause(e);
            throw ee;
        }
    }

    /**
     * Performs a {@link java.lang.invoke.MethodHandles.Lookup#unreflectSetter(Field)}, converting any encountered
     * {@link IllegalAccessException} into an {@link IllegalAccessError}.
     *
     * @param f the field for which a setter is unreflected
     * @return the unreflected field setter handle.
     */
    public MethodHandle unreflectSetter(Field f) {
        try {
            return lookup.unreflectSetter(f);
        } catch(IllegalAccessException e) {
            final IllegalAccessError ee = new IllegalAccessError("Failed to unreflect setter for field " + f);
            ee.initCause(e);
            throw ee;
        }
    }

    /**
     * Performs a {@link java.lang.invoke.MethodHandles.Lookup#unreflectConstructor(Constructor)}, converting any
     * encountered {@link IllegalAccessException} into an {@link IllegalAccessError}.
     *
     * @param c the constructor to unreflect
     * @return the unreflected constructor handle.
     */
    public MethodHandle unreflectConstructor(Constructor<?> c) {
        try {
            return lookup.unreflectConstructor(c);
        } catch(IllegalAccessException e) {
            final IllegalAccessError ee = new IllegalAccessError("Failed to unreflect constructor " + c);
            ee.initCause(e);
            throw ee;
        }
    }

    /**
     * Performs a findSpecial on the underlying lookup, except for the backport where it rather uses unreflect. Converts
     * any encountered {@link IllegalAccessException} into an {@link IllegalAccessError} and a
     * {@link NoSuchMethodException} into a {@link NoSuchMethodError}.
     *
     * @param declaringClass class declaring the method
     * @param name the name of the method
     * @param type the type of the method
     * @return a method handle for the method
     * @throws IllegalAccessError if the method is inaccessible.
     * @throws NoSuchMethodError if the method does not exist.
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

    /**
     * Performs a findStatic on the underlying lookup. Converts any encountered {@link IllegalAccessException} into an
     * {@link IllegalAccessError} and a {@link NoSuchMethodException} into a {@link NoSuchMethodError}.
     *
     * @param declaringClass class declaring the method
     * @param name the name of the method
     * @param type the type of the method
     * @return a method handle for the method
     * @throws IllegalAccessError if the method is inaccessible.
     * @throws NoSuchMethodError if the method does not exist.
     */
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

    /**
     * Performs a findVirtual on the underlying lookup. Converts any encountered {@link IllegalAccessException} into an
     * {@link IllegalAccessError} and a {@link NoSuchMethodException} into a {@link NoSuchMethodError}.
     *
     * @param declaringClass class declaring the method
     * @param name the name of the method
     * @param type the type of the method
     * @return a method handle for the method
     * @throws IllegalAccessError if the method is inaccessible.
     * @throws NoSuchMethodError if the method does not exist.
     */
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
        return new Lookup(lookup).findOwnSpecial(name, rtype, ptypes);
    }


    /**
     * Finds using {@link #findSpecial(Class, String, MethodType)} a method on that lookup's class. Useful in classes'
     * code for convenient linking to their own privates. It's easier to use than {@code findSpecial} in that you can
     * just list the parameter types, and don't have to specify lookup class.
     * @param name the name of the method
     * @param rtype the return type of the method
     * @param ptypes the parameter types of the method
     * @return the method handle for the method
     */
    public MethodHandle findOwnSpecial(String name, Class<?> rtype, Class<?>... ptypes) {
        return findSpecial(lookup.lookupClass(), name, MethodType.methodType(rtype, ptypes));
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
     * @param name the name of the method
     * @param rtype the return type of the method
     * @param ptypes the parameter types of the method
     * @return the method handle for the method
     */
    public MethodHandle findOwnStatic(String name, Class<?> rtype, Class<?>... ptypes) {
        return findStatic(lookup.lookupClass(), name, MethodType.methodType(rtype, ptypes));
    }
}
