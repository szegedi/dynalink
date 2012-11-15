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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for discovering accessible methods. Normally, a public method declared on a class is accessible (that
 * is, it can be invoked from anywhere). However, this is not the case if the class itself is not public. In that case,
 * it is required to lookup a method with the same signature in a public superclass or implemented interface of the
 * class, and use it instead of the method discovered on the class. This can of course all be avoided by simply using
 * {@link Method#setAccessible(boolean)}, but this solution (which I call "dynamic upcasting") works even in more
 * constrained security environments.
 *
 * @author Attila Szegedi
 */
class AccessibleMethodsLookup {
    private final Map<MethodSignature, Method> map;
    private boolean instance;

    /**
     * Creates a mapping for all accessible methods on a class.
     *
     * @param clazz the inspected class
     * @param instance true to inspect instance methods, false to inspect static methods
     */
    AccessibleMethodsLookup(final Class<?> clazz, boolean instance) {
        this.map = new HashMap<MethodSignature, Method>();
        this.instance = instance;
        lookupAccessibleMethods(clazz);
    }

    /**
     * Returns an accessible method equivalent of a method.
     *
     * @param m the method whose accessible equivalent is requested.
     * @return the accessible equivalent for the method (can be the same as the passed in method), or null if there is
     * no accessible method equivalent.
     */
    Method getAccessibleMethod(final Method m) {
        return m == null ? null : map.get(new MethodSignature(m));
    }

    Method[] getMethods() {
        return map.values().toArray(new Method[map.size()]);
    }

    /**
     * A helper class that represents a method signature - name and argument types.
     *
     * @author Attila Szegedi
     */
    static final class MethodSignature {
        private final String name;
        private final Class<?>[] args;

        /**
         * Creates a new method signature from arbitrary data.
         *
         * @param name the name of the method this signature represents.
         * @param args the argument types of the method.
         */
        MethodSignature(String name, Class<?>[] args) {
            this.name = name;
            this.args = args;
        }

        /**
         * Creates a signature for the given method.
         *
         * @param method the method for which a signature is created.
         */
        MethodSignature(final Method method) {
            this(method.getName(), method.getParameterTypes());
        }

        /**
         * Compares this object to another object
         *
         * @param o the other object
         * @return true if the other object is also a method signature with the same name, same number of arguments, and
         * same types of arguments.
         */
        @Override
        public boolean equals(final Object o) {
            if(o instanceof MethodSignature) {
                final MethodSignature ms = (MethodSignature)o;
                return ms.name.equals(name) && Arrays.equals(args, ms.args);
            }
            return false;
        }

        /**
         * Returns a hash code, consistent with the overridden {@link #equals(Object)}.
         */
        @Override
        public int hashCode() {
            return name.hashCode() ^ Arrays.hashCode(args);
        }
    }

    private void lookupAccessibleMethods(final Class<?> clazz) {
        if(!CheckRestrictedPackage.isRestrictedClass(clazz)) {
            final Method[] methods = clazz.getMethods();
            for(int i = 0; i < methods.length; i++) {
                final Method method = methods[i];
                if(instance != Modifier.isStatic(method.getModifiers())) {
                    map.put(new MethodSignature(method), method);
                }
            }
        } else {
            // If we reach here, the class is either not public, or it is in a restricted package. We'll try superclasses
            // and implemented interfaces then looking for public ones.
            final Class<?>[] interfaces = clazz.getInterfaces();
            for(int i = 0; i < interfaces.length; i++) {
                lookupAccessibleMethods(interfaces[i]);
            }
            final Class<?> superclass = clazz.getSuperclass();
            if(superclass != null) {
                lookupAccessibleMethods(superclass);
            }
        }
    }
}
