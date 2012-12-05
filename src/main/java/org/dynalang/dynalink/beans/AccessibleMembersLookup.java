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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Utility class for discovering accessible methods and inner classes. Normally, a public member declared on a class is
 * accessible (that is, it can be invoked from anywhere). However, this is not the case if the class itself is not
 * public, or belongs to a restricted-access package. In that case, it is required to lookup a member in a publicly
 * accessible superclass or implemented interface of the class, and use it instead of the member discovered on the
 * class.
 *
 * @author Attila Szegedi
 */
class AccessibleMembersLookup {
    private final Map<MethodSignature, Method> methods;
    private final List<Class<?>> innerClasses;
    private boolean instance;

    /**
     * Creates a mapping for all accessible methods and inner classes on a class.
     *
     * @param clazz the inspected class
     * @param instance true to inspect instance methods, false to inspect static methods.
     */
    AccessibleMembersLookup(final Class<?> clazz, boolean instance) {
        this.methods = new HashMap<>();
        this.innerClasses = new LinkedList<>();
        this.instance = instance;
        lookupAccessibleMembers(clazz);
    }

    /**
     * Returns an accessible method equivalent of a method.
     *
     * @param m the method whose accessible equivalent is requested.
     * @return the accessible equivalent for the method (can be the same as the passed in method), or null if there is
     * no accessible method equivalent.
     */
    Method getAccessibleMethod(final Method m) {
        return m == null ? null : methods.get(new MethodSignature(m));
    }

    Method[] getMethods() {
        return methods.values().toArray(new Method[methods.size()]);
    }

    Class<?>[] getInnerClasses() {
        return innerClasses.toArray(new Class<?>[innerClasses.size()]);
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

    private void lookupAccessibleMembers(final Class<?> clazz) {
        if(!CheckRestrictedPackage.isRestrictedClass(clazz)) {
            for(Method method: clazz.getMethods()) {
                if(instance != Modifier.isStatic(method.getModifiers())) {
                    methods.put(new MethodSignature(method), method);
                }
            }
            for(Class<?> innerClass: clazz.getClasses()) {
                // Add both static and non-static classes, regardless of instance flag. StaticClassLinker will just
                // expose non-static classes with explicit constructor outer class argument.
                // NOTE: getting inner class objects through getClasses() does not resolve them, so if those classes
                // were not yet loaded, they'll only get loaded in a non-resolved state; no static initializers for
                // them will trigger just by doing this.
                innerClasses.add(innerClass);
            }
        } else {
            // If we reach here, the class is either not public, or it is in a restricted package. We'll try superclasses
            // and implemented interfaces then looking for public ones.
            final Class<?>[] interfaces = clazz.getInterfaces();
            for(int i = 0; i < interfaces.length; i++) {
                lookupAccessibleMembers(interfaces[i]);
            }
            final Class<?> superclass = clazz.getSuperclass();
            if(superclass != null) {
                lookupAccessibleMembers(superclass);
            }
        }
    }
}
