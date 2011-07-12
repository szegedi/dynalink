/*
   Copyright 2009-2011 Attila Szegedi

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
import java.lang.BootstrapMethodError;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * A wrapper around MethodHandles.Lookup that masks checked exceptions in those
 * cases when you're looking up methods within your own codebase (therefore it
 * is an error if they are not present).
 *
 * @author Attila Szegedi
 * @version $Id: $
 */
public class Lookup {
    private final MethodHandles.Lookup lookup;

    /**
     * Creates a new instance, bound to an instance of
     * {@link java.lang.invoke.MethodHandles.Lookup}.
     *
     * @param lookup the {@link java.lang.invoke.MethodHandles.Lookup} it delegates to.
     */
    public Lookup(MethodHandles.Lookup lookup) {
        this.lookup = lookup;
    }

    public static final Lookup PUBLIC =
            new Lookup(MethodHandles.publicLookup());

    /**
     * Performs a {@link java.lang.invoke.MethodHandles.Lookup#unreflect(Method)}, converting any
     * encountered {@link IllegalAccessException} into a
     * {@link BootstrapMethodError}.
     *
     * @param m the method to unreflect
     * @return the unreflected method handle.
     */
    public MethodHandle unreflect(Method m) {
        try {
            return lookup.unreflect(m);
        } catch(IllegalAccessException e) {
            throw new BootstrapMethodError("Failed to unreflect " + m, e);
        }
    }

    /**
     * Performs a findSpecial on the underlying lookup, except for the backport
     * where it rather uses unreflect.
     *
     * @param declaringClass class declaring the method
     * @param name the name of the method
     * @param type the type of the method
     * @return a method handle for the method
     * @throws BootstrapMethodError if the method does not exist or is
     * inaccessible.
     */
    public MethodHandle findSpecial(Class<?> declaringClass, String name,
            MethodType type) {
        try {
            if(Backport.inUse) {
                final Method m =
                        declaringClass.getDeclaredMethod(name, type
                                .parameterArray());
                if(!Modifier.isPublic(declaringClass.getModifiers())
                        || !Modifier.isPublic(m.getModifiers())) {
                    m.setAccessible(true);
                }
                return unreflect(m);
            } else {
                return lookup.findSpecial(declaringClass, name, type,
                        declaringClass);
            }
        } catch(IllegalAccessException e) {
            throw new BootstrapMethodError("Failed to find special method "
                    + methodDescription(declaringClass, name, type), e);
        } catch(NoSuchMethodException e) {
            throw new BootstrapMethodError("Failed to find special method "
                    + methodDescription(declaringClass, name, type), e);
        }
    }

    private static String methodDescription(Class<?> declaringClass,
            String name, MethodType type) {
        return declaringClass.getName() + "#" + name + type;
    }

    public MethodHandle findStatic(Class<?> declaringClass, String methodName,
            MethodType methodType) {
        try {
            return lookup.findStatic(declaringClass, methodName, methodType);
        } catch(IllegalAccessException e) {
            throw new BootstrapMethodError(
                    "Failed to find static method "
                            + methodDescription(declaringClass, methodName,
                                    methodType), e);
        } catch(NoSuchMethodException e) {
            throw new BootstrapMethodError(
                    "Failed to find static method "
                            + methodDescription(declaringClass, methodName,
                                    methodType), e);
        }
    }

    public MethodHandle findVirtual(Class<?> declaringClass, String methodName,
            MethodType methodType) {
        try {
            return lookup.findVirtual(declaringClass, methodName, methodType);
        } catch(IllegalAccessException e) {
            throw new BootstrapMethodError(
                    "Failed to find virtual method "
                            + methodDescription(declaringClass, methodName,
                                    methodType), e);
        } catch(NoSuchMethodException e) {
            throw new BootstrapMethodError(
                    "Failed to find virtual method "
                            + methodDescription(declaringClass, methodName,
                                    methodType), e);
        }
    }

}
