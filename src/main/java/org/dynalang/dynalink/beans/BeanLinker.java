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

package org.dynalang.dynalink.beans;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.MethodDescriptor;
import java.beans.PropertyDescriptor;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dynalang.dynalink.Results;
import org.dynalang.dynalink.linker.CallSiteDescriptor;
import org.dynalang.dynalink.linker.GuardedInvocation;
import org.dynalang.dynalink.linker.GuardingDynamicLinker;
import org.dynalang.dynalink.linker.LinkRequest;
import org.dynalang.dynalink.linker.LinkerServices;
import org.dynalang.dynalink.support.Guards;
import org.dynalang.dynalink.support.Lookup;

/**
 * A class that provides linking capabilities for a single POJO class. Normally not used directly, but managed by
 * {@link BeansLinker}. TODO: add a class guard method, and propagate it downstream
 *
 * @author Attila Szegedi
 * @version $Id: $
 */
class BeanLinker implements GuardingDynamicLinker {
    private final Class<?> clazz;
    private final Map<String, AnnotatedMethodHandle> propertyGetters = new HashMap<String, AnnotatedMethodHandle>();
    private final Map<String, DynamicMethod> propertySetters = new HashMap<String, DynamicMethod>();
    private final Map<String, DynamicMethod> methods = new HashMap<String, DynamicMethod>();
    private final MethodHandle classGuard;

    BeanLinker(Class<?> clazz) throws IntrospectionException {
        this.clazz = clazz;
        classGuard = Guards.getClassGuard(clazz);

        final BeanInfo beanInfo = Introspector.getBeanInfo(clazz);
        final AccessibleMethodsLookup accessibleLookup = new AccessibleMethodsLookup(clazz);
        final PropertyDescriptor[] propDescs = beanInfo.getPropertyDescriptors();
        for(int i = 0; i < propDescs.length; i++) {
            final PropertyDescriptor descriptor = propDescs[i];
            final Method accReadMethod = accessibleLookup.getAccessibleMethod(descriptor.getReadMethod());
            final String name = descriptor.getName();
            if(accReadMethod != null) {
                // getMostGenericGetter() will look for the most generic superclass that declares this getter. Since
                // getters have zero args (aside from the receiver), they can't be overloaded, so we're free to link
                // with an instanceof guard for the most generic one, creating more stable call sites.
                propertyGetters.put(name, getMostGenericGetter(accReadMethod));
            }
            final Method accWriteMethod = accessibleLookup.getAccessibleMethod(descriptor.getWriteMethod());
            if(accWriteMethod != null) {
                propertySetters.put(name, new SimpleDynamicMethod(Lookup.PUBLIC.unreflect(accWriteMethod)));
            }
        }

        // Add field getters
        for(Field field: clazz.getFields()) {
            final int modifiers = field.getModifiers();
            if(Modifier.isStatic(modifiers)) {
                continue;
            }
            final String name = field.getName();
            if(!propertyGetters.containsKey(name)) {
                // Only add field getter if we don't have an explicit property
                // getter with the same name
                propertyGetters.put(name, new AnnotatedMethodHandle(Lookup.PUBLIC.unreflectGetter(field), false));
            }
        }

        // Add instance methods
        final MethodDescriptor[] methodDescs = beanInfo.getMethodDescriptors();
        for(int i = 0; i < methodDescs.length; i++) {
            final MethodDescriptor descriptor = methodDescs[i];
            final Method method = descriptor.getMethod();
            if(Modifier.isStatic(method.getModifiers()) || method.isBridge() || method.isSynthetic()) {
                continue;
            }
            final Method accMethod = accessibleLookup.getAccessibleMethod(method);
            if(accMethod == null) {
                continue;
            }
            final String name = method.getName();
            final MethodHandle methodHandle = Lookup.PUBLIC.unreflect(accMethod);
            addMember(name, methodHandle, methods);
            // Check if this method can be an alternative property setter
            if(isPropertySetter(accMethod)) {
                addMember(Introspector.decapitalize(name.substring(3)), methodHandle, propertySetters);
            }
        }

        // Add field setters as property setters, but only for fields that have no property setter defined.
        for(Field field: clazz.getFields()) {
            final int modifiers = field.getModifiers();
            if(Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) {
                continue;
            }
            final String name = field.getName();
            if(!propertySetters.containsKey(name)) {
                addMember(name, Lookup.PUBLIC.unreflectSetter(field), propertySetters);
            }
        }

        // Make sure we don't prevent GCing the class
        Introspector.flushFromCaches(clazz);
    }

    /**
     * Returns a dynamic method of the specified name.
     *
     * @param name name of the method
     * @return the dynamic method (either {@link SimpleDynamicMethod} or {@link OverloadedDynamicMethod}, or null if the
     * method with the specified name does not exist.
     */
    public DynamicMethod getMethod(String name) {
        return methods.get(name);
    }

    private void addMember(String name, MethodHandle mh, Map<String, DynamicMethod> methods) {
        DynamicMethod existingMethod = methods.get(name);
        DynamicMethod newMethod = addMember(mh, existingMethod);
        if(newMethod != existingMethod) {
            methods.put(name, newMethod);
        }
    }

    private DynamicMethod addMember(MethodHandle mh, DynamicMethod existing) {
        if(existing == null) {
            return new SimpleDynamicMethod(mh);
        } else if(existing.contains(mh)) {
            return existing;
        } else if(existing instanceof SimpleDynamicMethod) {
            OverloadedDynamicMethod odm = new OverloadedDynamicMethod(clazz);
            odm.addMethod(((SimpleDynamicMethod)existing));
            odm.addMethod(mh);
            return odm;
        } else if(existing instanceof OverloadedDynamicMethod) {
            ((OverloadedDynamicMethod)existing).addMethod(mh);
            return existing;
        }
        throw new AssertionError();
    }

    @Override
    public GuardedInvocation getGuardedInvocation(LinkRequest request, final LinkerServices linkerServices) {
        final LinkRequest ncrequest = request.withoutRuntimeContext();
        // BeansLinker already checked that the name is at least 2 elements
        // long and the first element is "dyn".
        final CallSiteDescriptor callSiteDescriptor = ncrequest.getCallSiteDescriptor();
        final String op = callSiteDescriptor.getNameToken(1);
        // Either dyn:getProp:name(this) or dyn:getProp(this, name)
        if("getProp".equals(op)) {
            return getPropertyGetter(callSiteDescriptor);
        }
        final Object[] arguments = ncrequest.getArguments();
        // Either dyn:setProp:name(this, value) or dyn:setProp(this, name,
        // value)
        if("setProp".equals(op)) {
            return getPropertySetter(callSiteDescriptor, linkerServices, arguments);
        }
        // Either dyn:callPropWithThis:name(this[,args]) or
        // dyn:callPropWithThis(this,name[,args]).
        if("callPropWithThis".equals(op)) {
            return getCallPropWithThis(callSiteDescriptor, linkerServices, arguments);
        }
        // dyn:getElem(this, id)
        // id is typically either an int (for arrays and lists) or an object
        // (for maps). linkerServices can provide conversion from call site
        // argument type though.
        if("getElem".equals(op)) {
            return getElementGetter(callSiteDescriptor, linkerServices, arguments);
        }
        if("setElem".equals(op)) {
            return getElementSetter(callSiteDescriptor, linkerServices, arguments);
        }
        // dyn:getLength(this) (works on Java arrays, collections, and maps)
        if("getLength".equals(op)) {
            return getLengthGetter(callSiteDescriptor, arguments);
        }
        return null;
    }

    private static MethodHandle GET_LIST_ELEMENT = Lookup.PUBLIC.findVirtual(List.class, "get",
            MethodType.methodType(Object.class, int.class));

    private static MethodHandle GET_MAP_ELEMENT = Lookup.PUBLIC.findVirtual(Map.class, "get",
            MethodType.methodType(Object.class, Object.class));

    private static MethodHandle LIST_GUARD = Guards.getInstanceGuard(List.class);
    private static MethodHandle MAP_GUARD = Guards.getInstanceGuard(Map.class);

    private GuardedInvocation getElementGetter(final CallSiteDescriptor callSiteDescriptor,
            final LinkerServices linkerServices, final Object... arguments) {
        assertParameterCount(callSiteDescriptor, 2);
        final MethodType callSiteType = callSiteDescriptor.getMethodType();
        final Class<?> declaredType = callSiteType.parameterType(0);
        // If declared type of receiver at the call site is already an array, a
        // list or map, bind without guard. Thing is, it'd be quite stupid of a
        // call site creator to go though invokedynamic when it knows in
        // advance they're dealing with an array, or a list or map, but hey...
        if(declaredType.isArray()) {
            return new GuardedInvocation(MethodHandles.arrayElementGetter(declaredType).asType(callSiteType), null);
        }
        if(List.class.isAssignableFrom(declaredType)) {
            return new GuardedInvocation(linkerServices.asType(GET_LIST_ELEMENT, callSiteType), null);
        }
        if(Map.class.isAssignableFrom(declaredType)) {
            return new GuardedInvocation(linkerServices.asType(GET_MAP_ELEMENT, callSiteType), null);
        }
        if(clazz.isArray()) {
            return new GuardedInvocation(linkerServices.asType(MethodHandles.arrayElementGetter(clazz), callSiteType),
                    getClassGuard(callSiteType));
        }
        if(List.class.isAssignableFrom(clazz)) {
            return new GuardedInvocation(linkerServices.asType(GET_LIST_ELEMENT, callSiteType), Guards.asType(
                    LIST_GUARD, callSiteType));
        }
        if(Map.class.isAssignableFrom(clazz)) {
            return new GuardedInvocation(linkerServices.asType(GET_MAP_ELEMENT, callSiteType), Guards.asType(MAP_GUARD,
                    callSiteType));
        }
        // Can't retrieve elements for objects that are neither arrays, nor
        // list, nor maps.
        return null;
    }

    private MethodHandle getClassGuard(MethodType type) {
        return Guards.asType(classGuard, type);
    }

    private static MethodHandle SET_LIST_ELEMENT = Lookup.PUBLIC.findVirtual(List.class, "set",
            MethodType.methodType(Object.class, int.class, Object.class));

    private static MethodHandle PUT_MAP_ELEMENT = Lookup.PUBLIC.findVirtual(Map.class, "put",
            MethodType.methodType(Object.class, Object.class, Object.class));

    private GuardedInvocation getElementSetter(CallSiteDescriptor callSiteDescriptor, LinkerServices linkerServices,
            Object... arguments) {
        assertParameterCount(callSiteDescriptor, 3);
        final MethodType callSiteType = callSiteDescriptor.getMethodType();
        final Class<?> declaredType = callSiteType.parameterType(0);
        // If declared type of receiver at the call site is already an array, a list or map, bind without guard. Thing
        // is, it'd be quite stupid of a call site creator to go though invokedynamic when it knows in advance they're
        // dealing with an array, or a list or map, but hey...
        if(declaredType.isArray()) {
            return new GuardedInvocation(linkerServices.asType(MethodHandles.arrayElementSetter(declaredType),
                    callSiteType), null);
        }
        if(List.class.isAssignableFrom(declaredType)) {
            return new GuardedInvocation(linkerServices.asType(SET_LIST_ELEMENT, callSiteType), null);
        }
        if(Map.class.isAssignableFrom(declaredType)) {
            return new GuardedInvocation(linkerServices.asType(PUT_MAP_ELEMENT, callSiteType), null);
        }
        // Otherwise, create a binding based on the actual type of the argument with an appropriate guard.
        if(clazz.isArray()) {
            return new GuardedInvocation(linkerServices.asType(MethodHandles.arrayElementSetter(clazz), callSiteType),
                    getClassGuard(callSiteType));
        }
        if(List.class.isAssignableFrom(clazz)) {
            return new GuardedInvocation(linkerServices.asType(SET_LIST_ELEMENT, callSiteType), Guards.asType(
                    LIST_GUARD, callSiteType));
        }
        if(Map.class.isAssignableFrom(clazz)) {
            return new GuardedInvocation(linkerServices.asType(PUT_MAP_ELEMENT, callSiteType), Guards.asType(MAP_GUARD,
                    callSiteType));
        }
        // Can't retrieve elements for objects that are neither arrays, nor
        // list, nor maps.
        return null;
    }

    private static MethodHandle GET_ARRAY_LENGTH = Lookup.PUBLIC.findStatic(Array.class, "getLength",
            MethodType.methodType(int.class, Object.class));

    private static MethodHandle GET_COLLECTION_LENGTH = Lookup.PUBLIC.findVirtual(Collection.class, "size",
            MethodType.methodType(int.class));

    private static MethodHandle GET_MAP_LENGTH = Lookup.PUBLIC.findVirtual(Map.class, "size",
            MethodType.methodType(int.class));

    private static MethodHandle COLLECTION_GUARD = Guards.getInstanceGuard(Collection.class);

    private GuardedInvocation getLengthGetter(CallSiteDescriptor callSiteDescriptor, Object... arguments) {
        assertParameterCount(callSiteDescriptor, 1);
        final MethodType callSiteType = callSiteDescriptor.getMethodType();
        final Class<?> declaredType = callSiteType.parameterType(0);
        // If declared type of receiver at the call site is already an array, collection, or map, bind without guard.
        // Thing is, it'd be quite stupid of a call site creator to go though invokedynamic when it knows in advance
        // they're dealing with an array, collection, or map, but hey...
        if(declaredType.isArray()) {
            // TODO: maybe we'll have a MethodHandles.arrayLengthGetter()?
            return new GuardedInvocation(GET_ARRAY_LENGTH.asType(callSiteType), null);
        }
        if(Collection.class.isAssignableFrom(declaredType)) {
            return new GuardedInvocation(GET_COLLECTION_LENGTH.asType(callSiteType), null);
        }
        if(Map.class.isAssignableFrom(declaredType)) {
            return new GuardedInvocation(GET_MAP_LENGTH.asType(callSiteType), null);
        }
        // Otherwise, create a binding based on the actual type of the argument
        // with an appropriate guard.
        if(clazz.isArray()) {
            return new GuardedInvocation(GET_ARRAY_LENGTH.asType(callSiteType), Guards.isArray(0, callSiteType));
        }
        if(Collection.class.isAssignableFrom(clazz)) {
            return new GuardedInvocation(GET_COLLECTION_LENGTH.asType(callSiteType), Guards.asType(COLLECTION_GUARD,
                    callSiteType));
        }
        if(Map.class.isAssignableFrom(clazz)) {
            return new GuardedInvocation(GET_MAP_LENGTH.asType(callSiteType), Guards.asType(MAP_GUARD, callSiteType));
        }
        // Can't retrieve length for objects that are neither arrays, nor
        // collections, nor maps.
        return null;
    }

    private GuardedInvocation getCallPropWithThis(CallSiteDescriptor callSiteDescriptor, LinkerServices linkerServices,
            Object... args) {
        switch(callSiteDescriptor.getNameTokenCount()) {
            case 3: {
                return createGuardedDynamicMethodInvocation(callSiteDescriptor, linkerServices,
                        callSiteDescriptor.getNameToken(2), methods);
            }
            default: {
                return null;
            }
        }
    }

    private GuardedInvocation createGuardedDynamicMethodInvocation(CallSiteDescriptor callSiteDescriptor,
            LinkerServices linkerServices, String methodName, Map<String, DynamicMethod> methods) {
        final MethodHandle invocation = getDynamicMethodInvocation(callSiteDescriptor, linkerServices, methodName,
                methods);
        return invocation == null ? null : new GuardedInvocation(invocation, getClassGuard(
                callSiteDescriptor.getMethodType()));
    }

    private static MethodHandle getDynamicMethodInvocation(CallSiteDescriptor callSiteDescriptor,
            LinkerServices linkerServices, String methodName, Map<String, DynamicMethod> methods) {
        final DynamicMethod dynaMethod = methods.get(methodName);
        return dynaMethod == null ? null : dynaMethod.getInvocation(callSiteDescriptor, linkerServices);
    }

    private GuardedInvocation getPropertySetter(CallSiteDescriptor callSiteDescriptor, LinkerServices linkerServices,
            Object... arguments) {
        final MethodType type = callSiteDescriptor.getMethodType();
        switch(callSiteDescriptor.getNameTokenCount()) {
            case 2: {
                // Must have theee arguments: target object, property name, and property value.
                assertParameterCount(callSiteDescriptor, 3);
                // Create a new call site descriptor that drops the ID argument. This is used for embedded overloaded
                // method lookup.
                final CallSiteDescriptor newDescriptor = callSiteDescriptor.dropParameterTypes(1, 2);
                return new GuardedInvocation(MethodHandles.insertArguments(SET_PROPERTY_WITH_VARIABLE_ID, 0,
                        newDescriptor, linkerServices).asType(type), getClassGuard(type));
            }
            case 3: {
                // Must have two arguments: target object and property value
                assertParameterCount(callSiteDescriptor, 2);
                return createGuardedDynamicMethodInvocation(callSiteDescriptor, linkerServices,
                        callSiteDescriptor.getNameToken(2), propertySetters);
            }
            default: {
                // More than two name components; don't know what to do with it.
                return null;
            }
        }
    }

    private GuardedInvocation getPropertyGetter(CallSiteDescriptor callSiteDescriptor) {
        final MethodType type = callSiteDescriptor.getMethodType();
        switch(callSiteDescriptor.getNameTokenCount()) {
            case 2: {
                // Must have exactly two arguments: receiver and name
                assertParameterCount(callSiteDescriptor, 2);
                return new GuardedInvocation(GET_PROPERTY_WITH_VARIABLE_ID.asType(type), getClassGuard(type));
            }
            case 3: {
                // Must have exactly one argument: receiver
                assertParameterCount(callSiteDescriptor, 1);
                // Fixed name
                final AnnotatedMethodHandle annGetter = propertyGetters.get(callSiteDescriptor.getNameToken(2));
                if(annGetter == null) {
                    // Property has no getter
                    return null;
                }
                final MethodHandle getter = annGetter.handle;
                final MethodHandle guard;
                final Class<?> guardType = getter.type().parameterType(0);
                // NOTE: since property getters (not field getters!) are no-arg, we don't have to worry about them being
                // overloaded in a subclass. Therefore, we can discover the most abstract superclass that has the
                // method, and use that as the guard with Guards.isInstance() for a more stably linked call site. If
                // we're linking against a field getter, don't make the assumption.
                return new GuardedInvocation(getter.asType(type), annGetter.overloadSafe ? Guards.isInstance(guardType,
                        type) : getClassGuard(type));
            }
            default: {
                // Can't do anything with more than 3 name components
                return null;
            }
        }
    }

    private static void assertParameterCount(CallSiteDescriptor descriptor, int paramCount) {
        if(descriptor.getMethodType().parameterCount() != paramCount) {
            throw new BootstrapMethodError(descriptor.getName() + " must have exactly " + paramCount + " parameters.");
        }
    }

    private static final Lookup privateLookup = new Lookup(MethodHandles.lookup());

    private MethodHandle GET_PROPERTY_WITH_VARIABLE_ID = privateLookup.findSpecial(BeanLinker.class,
            "_getPropertyWithVariableId", MethodType.methodType(Object.class, Object.class, Object.class)).bindTo(this);

    /**
     * This method is public for implementation reasons. Do not invoke it directly. Retrieves a property value from an
     * object.
     *
     * @param obj the object
     * @param id the property ID
     * @return the value of the property, or {@link Results#doesNotExist} if the property does not exist, or
     * {@link Results#notReadable} if the property is not readable.
     * @throws Throwable rethrown underlying method handle invocation throwable.
     */
    public Object _getPropertyWithVariableId(Object obj, Object id) throws Throwable {
        AnnotatedMethodHandle getter = propertyGetters.get(String.valueOf(id));
        if(getter == null) {
            return Results.notReadable;
        }
        return getter.handle.invokeWithArguments(obj);
    }

    private MethodHandle SET_PROPERTY_WITH_VARIABLE_ID = privateLookup.findSpecial(
            BeanLinker.class,
            "_setPropertyWithVariableId",
            MethodType.methodType(Results.class, CallSiteDescriptor.class, LinkerServices.class, Object.class,
                    Object.class, Object.class)).bindTo(this);

    /**
     * This method is public for implementation reasons. Do not invoke it directly. Sets a property on an object.
     * @param callSiteDescriptor the descriptor of the setter call site
     * @param linkerServices the linker services used for value conversion
     * @param obj the object
     * @param id the property ID
     * @param value the new value for the property
     * @return {@link Results#ok} if the operation succeeded, or {@link Results#notWritable} if the property is not
     * writable.
     * @throws Throwable rethrown underlying method handle invocation throwable.
     */
    public Results _setPropertyWithVariableId(CallSiteDescriptor callSiteDescriptor, LinkerServices linkerServices,
            Object obj, Object id, Object value) throws Throwable {
        // TODO: this is quite likely terribly inefficient. Optimize.
        final MethodHandle invocation = getDynamicMethodInvocation(callSiteDescriptor, linkerServices, String.valueOf(
                id), propertySetters);
        if(invocation != null) {
            invocation.invokeWithArguments(obj, value);
            return Results.ok;
        }
        return Results.notWritable;
    }

    private static AnnotatedMethodHandle getMostGenericGetter(Method getter) {
        final Method mostGenericGetter =
                getMostGenericGetter(getter.getName(), getter.getReturnType(), getter.getDeclaringClass());
        return new AnnotatedMethodHandle(Lookup.PUBLIC.unreflect(mostGenericGetter), true);
    }

    private static Method getMostGenericGetter(String name, Class<?> returnType, Class<?> declaringClass) {
        if(declaringClass == null) {
            return null;
        }
        // Prefer interfaces
        for(Class<?> itf: declaringClass.getInterfaces()) {
            final Method itfGetter = getMostGenericGetter(name, returnType, itf);
            if(itfGetter != null) {
                return itfGetter;
            }
        }
        final Method superGetter = getMostGenericGetter(name, returnType, declaringClass.getSuperclass());
        if(superGetter != null) {
            return superGetter;
        }
        if(Modifier.isPublic(declaringClass.getModifiers())) {
            try {
                return declaringClass.getMethod(name);
            } catch(NoSuchMethodException e) {
                // Intentionally ignored, meant to fall through
            }
        }
        return null;
    }

    private static final class AnnotatedMethodHandle {
        private final MethodHandle handle;
        private final boolean overloadSafe;

        AnnotatedMethodHandle(MethodHandle handle, boolean overloadSafe) {
            this.handle = handle;
            this.overloadSafe = overloadSafe;
        }
    }

    /**
     * Determines if the method is a property setter. Only invoked on public instance methods, so we don't check
     * repeatedly for those. It differs somewhat from the JavaBeans introspector's definition, as we'll happily accept
     * methods that have non-void return types, to accommodate for the widespread pattern of property setters that allow
     * chaining.
     * @param m the method tested for being a property setter
     * @return true if it is a property setter, false otherwise
     */
    private static boolean isPropertySetter(Method m) {
        final String name = m.getName();
        return name.startsWith("set") && name.length() > 3 && m.getParameterTypes().length == 1;
    }
}