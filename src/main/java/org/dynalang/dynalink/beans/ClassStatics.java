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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.dynalang.dynalink.support.Lookup;

class ClassStatics {
    private final Class<?> clazz;

    ClassStatics(Class<?> clazz) {
        this.clazz = clazz;
    }

    Class<?> getRepresentedClass() {
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

    static MethodHandle getIsClass(Class<?> clazz) {
        return IS_CLASS.bindTo(clazz);
    }

    private static boolean isClass(Class<?> clazz, Object obj) {
        return obj instanceof ClassStatics && ((ClassStatics)obj).clazz == clazz;
    }
}
