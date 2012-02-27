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

import java.beans.PropertyDescriptor;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;

import org.dynalang.dynalink.support.Lookup;

/**
 * Base for classes that expose class property, field, and method information to an {@link AbstractJavaLinker}. There
 * are subclasses for instance (bean) and statics facet of a class.
 * @author Attila Szegedi
 * @version $Id: $
 */
abstract class FacetIntrospector implements AutoCloseable {
    protected final Class<?> clazz;

    FacetIntrospector(Class<?> clazz) {
        this.clazz = clazz;
    }

    /**
     * Returns bean properties for the facet.
     * @return bean properties for the facet.
     */
    abstract Collection<PropertyDescriptor> getProperties();

    /**
     * Returns the fields for the class facet.
     * @return the fields for the class facet.
     */
    Collection<Field> getFields() {
        final Field[] fields = clazz.getFields();
        Collection<Field> cfields = new ArrayList<Field>(fields.length);
        for(Field field: fields) {
            if(includeField(field)) {
                cfields.add(field);
            }
        }
        return cfields;
    }

    /**
     * A filter predicate to determine whether to include a field in the facet or not.
     * @param field the field to test
     * @return true to include the field in the facet, false otherwise.
     */
    abstract boolean includeField(Field field);

    /**
     * Returns all the methods in the facet.
     * @return all the methods in the facet.
     */
    abstract Collection<Method> getMethods();

    MethodHandle unreflectGetter(Field field) {
        return editMethodHandle(Lookup.PUBLIC.unreflectGetter(field));
    }

    MethodHandle unreflectSetter(Field field) {
        return editMethodHandle(Lookup.PUBLIC.unreflectSetter(field));
    }

    MethodHandle unreflect(Method method) {
        return editMethodHandle(Lookup.PUBLIC.unreflect(method));
    }

    /**
     * Returns an edited method handle. A facet might need to edit an unreflected method handle before it is usable with
     * the facet. By default, returns the passed method handle unchanged. The class statics' facet will introduce a
     * dropArguments.
     * @param mh the method handle to edit.
     * @return the edited method handle.
     */
    MethodHandle editMethodHandle(MethodHandle mh) {
        return mh;
    }

    @Override
    public void close() {
    }
}