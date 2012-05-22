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

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.MethodDescriptor;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

class BeanIntrospector extends FacetIntrospector {
    private final BeanInfo beanInfo;

    BeanIntrospector(Class<?> clazz) {
        super(clazz);
        try {
            beanInfo = Introspector.getBeanInfo(clazz);
        } catch(IntrospectionException e) {
            throw new UndeclaredThrowableException(e);
        }
    }

    @Override
    Collection<PropertyDescriptor> getProperties() {
        return Arrays.asList(beanInfo.getPropertyDescriptors());
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
                cmethods.add(method);
            }
        }
        return cmethods;
    }

    @Override
    public void close() {
        Introspector.flushFromCaches(clazz);
    }
}
