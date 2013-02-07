/*
   Copyright 2009-2013 Attila Szegedi

   Licensed under either the Apache License, Version 2.0 (the "Apache
   License") or the 3-clause BSD License (the "BSD License"), with licensee
   being free to choose either of the two at their discretion.

   You may not use this file except in compliance with either the Apache
   License or the BSD License.

   A copy of the BSD License is available in the root directory of the
   source distribution of the project under the file name
   "LICENSE-BSD.txt".

   A copy of the Apache License is available in the root directory of the
   source distribution of the project under the file name
   "LICENSE-Apache-2.0.txt". Alternatively, you may obtain a copy of the
   Apache License at <http://www.apache.org/licenses/LICENSE-2.0>

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See your chosen License for the specific language governing permissions
   and limitations under that License.
*/

package org.dynalang.dynalink.beans;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

class StaticClassIntrospector extends FacetIntrospector {
    private final Method[] methods;
    StaticClassIntrospector(Class<?> clazz) {
        super(clazz, false);
        methods = membersLookup.getMethods();
    }

    @Override
    Collection<PropertyDescriptor> getProperties() {
        // Discover getXxx()/isXxx() methods as property getters. Don't deal separately with property setters, as
        // AbstractJavaLinker will construct them directly from the setXxx() methods returned from the getMethods()
        // call.
        final Map<String, PropertyDescriptor> descs = new HashMap<String, PropertyDescriptor>();
        for(Method method: methods) {
            if(method.isBridge() || method.isSynthetic()) {
                continue;
            }
            if(method.getReturnType() == Void.TYPE) {
                continue;
            }
            final String name = method.getName();
            if(name.startsWith("get") && name.length() > 3 && method.getParameterTypes().length == 0) {
                addPropertyDescriptor(descs, method, Introspector.decapitalize(name.substring(3)));
            } else if(name.startsWith("is") && name.length() > 2 && method.getParameterTypes().length == 0) {
                addPropertyDescriptor(descs, method, Introspector.decapitalize(name.substring(2)));
            }
        }
        return descs.values();
    }

    private static void addPropertyDescriptor(Map<String, PropertyDescriptor> descs, Method method, String name) {
        if(!descs.containsKey(name)) {
            try {
                descs.put(name, new PropertyDescriptor(name, method, null));
            } catch(IntrospectionException e) {
                throw new RuntimeException(e);
            }
        }

    }

    @Override
    Map<String, MethodHandle> getInnerClassGetters() {
        final Map<String, MethodHandle> map = new HashMap<>();
        for(Class<?> innerClass: membersLookup.getInnerClasses()) {
            map.put(innerClass.getSimpleName(), editMethodHandle(MethodHandles.constant(StaticClass.class,
                    StaticClass.forClass(innerClass))));
        }
        return map;
    }

    @Override
    boolean includeField(Field field) {
        return Modifier.isStatic(field.getModifiers());
    }

    @Override
    Collection<Method> getMethods() {
        final Collection<Method> cmethods = new ArrayList<Method>(methods.length);
        for(Method method: methods) {
            if(!(method.isBridge() || method.isSynthetic())) {
                cmethods.add(method);
            }
        }
        return cmethods;
    }

    @Override
    MethodHandle editMethodHandle(MethodHandle mh) {
        MethodHandle newHandle = MethodHandles.dropArguments(mh, 0, Object.class);
        // NOTE: this is a workaround for the fact that dropArguments doesn't preserve vararg collector state.
        if(mh.isVarargsCollector() && !newHandle.isVarargsCollector()) {
            final MethodType type = mh.type();
            newHandle = newHandle.asVarargsCollector(type.parameterType(type.parameterCount() - 1));
        }
        return newHandle;
    }
}
