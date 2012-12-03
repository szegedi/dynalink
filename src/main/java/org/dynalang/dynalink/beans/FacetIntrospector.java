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
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.dynalang.dynalink.support.Lookup;

/**
 * Base for classes that expose class property, field, and method information to an {@link AbstractJavaLinker}. There
 * are subclasses for instance (bean) and static facet of a class.
 * @author Attila Szegedi
 */
abstract class FacetIntrospector implements AutoCloseable {
    protected final Class<?> clazz;
    protected final boolean isRestricted;
    protected final AccessibleMembersLookup membersLookup;

    FacetIntrospector(Class<?> clazz, boolean instance) {
        this.clazz = clazz;
        isRestricted = CheckRestrictedPackage.isRestrictedClass(clazz);
        this.membersLookup = new AccessibleMembersLookup(clazz, instance);
    }

    /**
     * Returns bean properties for the facet.
     * @return bean properties for the facet.
     */
    abstract Collection<PropertyDescriptor> getProperties();

    /**
     * Returns getters for inner classes.
     * @return getters for inner classes.
     */
    abstract Map<String, MethodHandle> getInnerClassGetters();

    /**
     * Returns the fields for the class facet.
     * @return the fields for the class facet.
     */
    Collection<Field> getFields() {
        if(isRestricted) {
            // NOTE: we can't do anything here. Unlike with methods in AccessibleMethodsLookup, we can't just return
            // the fields from a public superclass, because this class might define same-named fields which will shadow
            // the superclass fields, and we have no way to know if they do, since we're denied invocation of
            // getFields(). Therefore, the only correct course of action is to not expose any public fields a class
            // defined in a restricted package.
            return Collections.emptySet();
        }

        final Field[] fields = clazz.getFields();
        final Collection<Field> cfields = new ArrayList<Field>(fields.length);
        for(Field field: fields) {
            if(isAccessible(field) && includeField(field)) {
                cfields.add(field);
            }
        }
        return cfields;
    }

    boolean isAccessible(Member m) {
        final Class<?> declaring = m.getDeclaringClass();
        // (declaring == clazz) is just an optimization - we're calling this only from code that operates on a
        // non-restriced class, so if the declaring class is identical to the class being inspected, then forego
        // a potentially expensive restricted-package check.
        return declaring == clazz || !CheckRestrictedPackage.isRestrictedClass(declaring);
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
     * the facet. By default, returns the passed method handle unchanged. The class' static facet will introduce a
     * dropArguments.
     * @param mh the method handle to edit.
     * @return the edited method handle.
     */
    @SuppressWarnings("static-method") // Meant to be overridden in a subclass
    MethodHandle editMethodHandle(MethodHandle mh) {
        return mh;
    }

    @Override
    public void close() {
    }
}