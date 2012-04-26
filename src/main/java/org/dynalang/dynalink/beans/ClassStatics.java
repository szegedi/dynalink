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

package org.dynalang.dynalink.beans;

import java.io.Serializable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.dynalang.dynalink.support.Lookup;

/**
 * Object that represents the static facet of a class (its static methods, properties, and fields). Objects of this
 * class are recognized by the {@link BeansLinker} as being special, and operations on them will be linked against the
 * represented class' static facet. The "class" synthetic property is additionally recognized and returns the Java Class
 * object, as per {@link #getRepresentedClass()} method. Conversely, {@link Class} objects exposed through
 * {@link BeansLinker} expose the "statics" synthetic property which returns an instance of this class. The linker also
 * interprets the "dyn:new" operation on these objects just as if they were executed on the Class they represent.
 */
public class ClassStatics implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Class<?> clazz;

    /**
     * Creates a new instance of ClassStatics for the specified class.
     * @param clazz the class for which to create the static facet.
     * @throws NullPointerException if clazz is null
     */
    public ClassStatics(Class<?> clazz) {
        clazz.getClass(); // NPE check
        this.clazz = clazz;
    }

    /**
     * Returns the represented Java class.
     * @return the represented Java class.
     */
    public Class<?> getRepresentedClass() {
        return clazz;
    }

    @Override
    public boolean equals(Object obj) {
        return isClass(clazz, obj);
    }

    @Override
    public int hashCode() {
        return clazz.hashCode();
    }

    @Override
    public String toString() {
        return "ClassStatics[" + clazz.getName() + "]";
    }

    static final MethodHandle IS_CLASS = new Lookup(MethodHandles.lookup()).findStatic(ClassStatics.class,
            "isClass", MethodType.methodType(Boolean.TYPE, Class.class, Object.class));

    static final MethodHandle GET_CLASS = new Lookup(MethodHandles.lookup()).findSpecial(ClassStatics.class,
            "getRepresentedClass", MethodType.methodType(Class.class));

    static MethodHandle getIsClass(Class<?> clazz) {
        return IS_CLASS.bindTo(clazz);
    }

    private static boolean isClass(Class<?> clazz, Object obj) {
        return obj instanceof ClassStatics && ((ClassStatics)obj).clazz == clazz;
    }
}
