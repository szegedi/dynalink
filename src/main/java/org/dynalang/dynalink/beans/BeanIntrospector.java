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

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.MethodDescriptor;
import java.beans.PropertyDescriptor;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

class BeanIntrospector extends FacetIntrospector {
    private final BeanInfo beanInfo;

    BeanIntrospector(Class<?> clazz) {
        super(clazz, true);
        try {
            beanInfo = Introspector.getBeanInfo(clazz);
        } catch(IntrospectionException e) {
            throw new UndeclaredThrowableException(e);
        }
    }

    @Override
    Collection<PropertyDescriptor> getProperties() {
        final PropertyDescriptor[] descs = beanInfo.getPropertyDescriptors();
        final List<PropertyDescriptor> ldescs = new ArrayList<>(descs.length);
        for(PropertyDescriptor desc: descs) {
            final Method readMethod = desc.getReadMethod();
            final Method writeMethod = desc.getWriteMethod();
            final Method accReadMethod = membersLookup.getAccessibleMethod(readMethod);
            final Method accWriteMethod = membersLookup.getAccessibleMethod(writeMethod);
            if(readMethod != null || writeMethod != null) {
                if(accReadMethod == readMethod && accWriteMethod == writeMethod) {
                    ldescs.add(desc);
                } else {
                    try {
                        ldescs.add(new PropertyDescriptor(desc.getName(), accReadMethod, accWriteMethod));
                    } catch(IntrospectionException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        return ldescs;
    }

    @Override
    Map<String, MethodHandle> getInnerClassGetters() {
        return Collections.emptyMap(); // TODO: non-static inner classes
    }

    @Override
    boolean includeField(Field field) {
        return !Modifier.isStatic(field.getModifiers());
    }

    @Override
    Collection<Method> getMethods() {
        final MethodDescriptor[] methods = beanInfo.getMethodDescriptors();
        final Collection<Method> cmethods = new ArrayList<Method>(methods.length);
        for(MethodDescriptor methodDesc: methods) {
            final Method method = methodDesc.getMethod();
            if(!(Modifier.isStatic(method.getModifiers()))) {
                final Method accMethod = membersLookup.getAccessibleMethod(method);
                if(accMethod != null) {
                    cmethods.add(accMethod);
                }
            }
        }
        return cmethods;
    }

    @Override
    public void close() {
        Introspector.flushFromCaches(clazz);
    }
}
