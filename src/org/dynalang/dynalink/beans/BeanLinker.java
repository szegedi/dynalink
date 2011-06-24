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
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dynalang.dynalink.CallSiteDescriptor;
import org.dynalang.dynalink.GuardedInvocation;
import org.dynalang.dynalink.GuardingDynamicLinker;
import org.dynalang.dynalink.LinkRequest;
import org.dynalang.dynalink.LinkerServices;
import org.dynalang.dynalink.Results;
import org.dynalang.dynalink.beans.support.AccessibleMethodsLookup;
import org.dynalang.dynalink.support.Guards;
import org.dynalang.dynalink.support.Lookup;

/**
 * A class that provides linking capabilities for a single POJO class. Normally
 * not used directly, but managed by {@link BeansLinker}. TODO: add a class
 * guard method, and propagate it downstream
 *
 * @author Attila Szegedi
 * @version $Id: $
 */
public class BeanLinker implements GuardingDynamicLinker {
    private final Class<?> clazz;

    private final Map<String, PropertyGetterDescriptor> properties =
            new HashMap<String, PropertyGetterDescriptor>();
    private final Map<String, DynamicMethod> methods =
            new HashMap<String, DynamicMethod>();

    BeanLinker(Class<?> clazz) throws IntrospectionException {
        this.clazz = clazz;
        final BeanInfo beanInfo = Introspector.getBeanInfo(clazz);
        final AccessibleMethodsLookup accessibleLookup =
                new AccessibleMethodsLookup(clazz);
        final PropertyDescriptor[] propDescs =
                beanInfo.getPropertyDescriptors();
        for(int i = 0; i < propDescs.length; i++) {
            final PropertyDescriptor descriptor = propDescs[i];
            final Method readMethod = descriptor.getReadMethod();
            if(readMethod == null) {
                continue;
            }
            final Method accReadMethod =
                    accessibleLookup.getAccessibleMethod(readMethod);
            if(accReadMethod == null) {
                continue;
            }
            properties.put(descriptor.getName(), new PropertyGetterDescriptor(
                    readMethod));
        }

        // Add instance methods
        final MethodDescriptor[] methodDescs = beanInfo.getMethodDescriptors();
        for(int i = 0; i < methodDescs.length; i++) {
            final MethodDescriptor descriptor = methodDescs[i];
            final Method method = descriptor.getMethod();
            if(Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            final Method accMethod =
                    accessibleLookup.getAccessibleMethod(method);
            if(accMethod == null) {
                continue;
            }
            addMember(accMethod);
        }
    }

    /**
     * Returns a dynamic method of the specified name.
     *
     * @param name name of the method
     * @return the dynamic method (either {@link SimpleDynamicMethod} or
     * {@link OverloadedDynamicMethod}, or null if the method with the specified
     * name does not exist.
     */
    public DynamicMethod getMethod(String name) {
        return methods.get(name);
    }

    private void addMember(Method method) {
        final String name = method.getName();
        DynamicMethod existingMethod = methods.get(name);
        DynamicMethod newMethod = addMember(method, existingMethod);
        if(newMethod != existingMethod) {
            methods.put(name, newMethod);
        }
    }

    private DynamicMethod addMember(Method method, DynamicMethod existing) {
        final MethodHandle mh = Lookup.PUBLIC.unreflect(method);
        if(existing == null) {
            return new SimpleDynamicMethod(mh);
        } else
            if(existing instanceof SimpleDynamicMethod) {
                OverloadedDynamicMethod odm =
                        new OverloadedDynamicMethod(clazz);
                odm.addMethod(((SimpleDynamicMethod)existing));
                odm.addMethod(mh);
                return odm;
            } else
                if(existing instanceof OverloadedDynamicMethod) {
                    ((OverloadedDynamicMethod)existing).addMethod(mh);
                    return existing;
                }
        throw new AssertionError();
    }

    public GuardedInvocation getGuardedInvocation(LinkRequest request,
            final LinkerServices linkerServices) {
        request = request.withoutRuntimeContext();
        // BeansLinker already checked that the name is at least 2 elements
        // long and the first element is "dyn".
        final CallSiteDescriptor callSiteDescriptor =
                request.getCallSiteDescriptor();
        final String op = callSiteDescriptor.getTokenizedName().get(1);
        // Either dyn:getProp:name(this) or dyn:getProp(this, name)
        if("getProp".equals(op)) {
            return getPropertyGetter(callSiteDescriptor);
        }
        final Object[] arguments = request.getArguments();
        // Either dyn:setProp:name(this, value) or dyn:setProp(this, name,
        // value)
        if("setProp".equals(op)) {
            return getPropertySetter(callSiteDescriptor, linkerServices,
                    arguments);
        }
        // Either dyn:callPropWithThis:name(this[,args]) or
        // dyn:callPropWithThis(name,this,[,args]).
        if("callPropWithThis".equals(op)) {
            return getCallPropWithThis(callSiteDescriptor, linkerServices,
                    arguments);
        }
        // dyn:getElem(this, id)
        // id is typically either an int (for arrays and lists) or an object
        // (for maps). linkerServices can provide conversion from call site
        // argument type though.
        if("getElem".equals(op)) {
            return getElementGetter(callSiteDescriptor, linkerServices,
                    arguments);
        }
        if("setElem".equals(op)) {
            return getElementSetter(callSiteDescriptor, linkerServices,
                    arguments);
        }
        // dyn:getLength(this) (works on Java arrays, collections, and maps)
        if("getLength".equals(op)) {
            return getLengthGetter(callSiteDescriptor, arguments);
        }
        return null;
    }

    private static MethodHandle GET_LIST_ELEMENT =
            Lookup.PUBLIC.findVirtual(List.class, "get", MethodType.methodType(
                    Object.class, int.class));

    private static MethodHandle GET_MAP_ELEMENT =
            Lookup.PUBLIC.findVirtual(Map.class, "get", MethodType.methodType(
                    Object.class, Object.class));

    private GuardedInvocation getElementGetter(
            final CallSiteDescriptor callSiteDescriptor,
            final LinkerServices linkerServices, final Object... arguments) {
        callSiteDescriptor.assertParameterCount(2);
        final MethodType callSiteType = callSiteDescriptor.getMethodType();
        final Class<?> declaredType = callSiteType.parameterType(0);
        // If declared type of receiver at the call site is already an array, a
        // list or map, bind without guard. Thing is, it'd be quite stupid of a
        // call site creator to go though invokedynamic when it knows in
        // advance they're dealing with an array, or a list or map, but hey...
        if(declaredType.isArray()) {
            return new GuardedInvocation(MethodHandles.arrayElementGetter(
                    declaredType).asType(callSiteType), null);
        }
        if(List.class.isAssignableFrom(declaredType)) {
            return new GuardedInvocation(linkerServices.convertArguments(
                    GET_LIST_ELEMENT, callSiteType), null);
        }
        if(Map.class.isAssignableFrom(declaredType)) {
            return new GuardedInvocation(linkerServices.convertArguments(
                    GET_MAP_ELEMENT, callSiteType), null);
        }
        // Otherwise, create a binding based on the actual type of the argument
        // with an appropriate guard.
        final Object receiver = arguments[0];
        final Class<?> clazz = receiver.getClass();
        if(clazz.isArray()) {
            return new GuardedInvocation(linkerServices.convertArguments(
                    MethodHandles.arrayElementGetter(clazz), callSiteType),
                    Guards.isOfClass(clazz, callSiteType));
        }
        if(List.class.isInstance(receiver)) {
            return new GuardedInvocation(linkerServices.convertArguments(
                    GET_LIST_ELEMENT, callSiteType), Guards.isInstance(
                    List.class, callSiteType));
        }
        if(Map.class.isInstance(receiver)) {
            return new GuardedInvocation(linkerServices.convertArguments(
                    GET_MAP_ELEMENT, callSiteType), Guards.isInstance(
                    Map.class, callSiteType));
        }
        // Can't retrieve elements for objects that are neither arrays, nor
        // list, nor maps.
        return null;
    }

    private static MethodHandle SET_LIST_ELEMENT =
            Lookup.PUBLIC.findVirtual(List.class, "set", MethodType.methodType(
                    Object.class, int.class, Object.class));

    private static MethodHandle PUT_MAP_ELEMENT =
            Lookup.PUBLIC.findVirtual(Map.class, "put", MethodType.methodType(
                    Object.class, Object.class, Object.class));

    private GuardedInvocation getElementSetter(
            final CallSiteDescriptor callSiteDescriptor,
            final LinkerServices linkerServices, final Object... arguments) {
        callSiteDescriptor.assertParameterCount(3);
        final MethodType callSiteType = callSiteDescriptor.getMethodType();
        final Class<?> declaredType = callSiteType.parameterType(0);
        // If declared type of receiver at the call site is already an array, a
        // list or map, bind without guard. Thing is, it'd be quite stupid of a
        // call site creator to go though invokedynamic when it knows in
        // advance they're dealing with an array, or a list or map, but hey...
        if(declaredType.isArray()) {
            return new GuardedInvocation(linkerServices.convertArguments(
                    MethodHandles.arrayElementSetter(declaredType),
                    callSiteType), null);
        }
        if(List.class.isAssignableFrom(declaredType)) {
            return new GuardedInvocation(linkerServices.convertArguments(
                    SET_LIST_ELEMENT, callSiteType), null);
        }
        if(Map.class.isAssignableFrom(declaredType)) {
            return new GuardedInvocation(linkerServices.convertArguments(
                    PUT_MAP_ELEMENT, callSiteType), null);
        }
        // Otherwise, create a binding based on the actual type of the argument
        // with an appropriate guard.
        final Object receiver = arguments[0];
        final Class<?> clazz = receiver.getClass();
        if(clazz.isArray()) {
            return new GuardedInvocation(linkerServices.convertArguments(
                    MethodHandles.arrayElementSetter(clazz), callSiteType),
                    Guards.isOfClass(clazz, callSiteType));
        }
        if(List.class.isInstance(receiver)) {
            return new GuardedInvocation(linkerServices.convertArguments(
                    SET_LIST_ELEMENT, callSiteType), Guards.isInstance(
                    List.class, callSiteType));
        }
        if(Map.class.isInstance(receiver)) {
            return new GuardedInvocation(linkerServices.convertArguments(
                    PUT_MAP_ELEMENT, callSiteType), Guards.isInstance(
                    Map.class, callSiteType));
        }
        // Can't retrieve elements for objects that are neither arrays, nor
        // list, nor maps.
        return null;
    }

    private static MethodHandle GET_ARRAY_LENGTH =
            Lookup.PUBLIC.findStatic(Array.class, "getLength", MethodType
                    .methodType(int.class, Object.class));

    private static MethodHandle GET_COLLECTION_LENGTH =
            Lookup.PUBLIC.findVirtual(Collection.class, "size", MethodType
                    .methodType(int.class));

    private static MethodHandle GET_MAP_LENGTH =
            Lookup.PUBLIC.findVirtual(Map.class, "size", MethodType
                    .methodType(int.class));

    private GuardedInvocation getLengthGetter(
            final CallSiteDescriptor callSiteDescriptor,
            final Object... arguments) {
        callSiteDescriptor.assertParameterCount(1);
        final MethodType callSiteType = callSiteDescriptor.getMethodType();
        final Class<?> declaredType = callSiteType.parameterType(0);
        // If declared type of receiver at the call site is already an array,
        // collection, or map, bind without guard. Thing is, it'd be quite
        // stupid of a call site creator to go though invokedynamic when it
        // knows in advance they're dealing with an array, collection, or map,
        // but hey...
        if(declaredType.isArray()) {
            // TODO: maybe we'll have a MethodHandles.arrayLengthGetter()?
            return new GuardedInvocation(GET_ARRAY_LENGTH.asType(callSiteType),
                    null);
        }
        if(Collection.class.isAssignableFrom(declaredType)) {
            return new GuardedInvocation(GET_COLLECTION_LENGTH
                    .asType(callSiteType), null);
        }
        if(Map.class.isAssignableFrom(declaredType)) {
            return new GuardedInvocation(GET_MAP_LENGTH.asType(callSiteType),
                    null);
        }
        // Otherwise, create a binding based on the actual type of the argument
        // with an appropriate guard.
        final Class<?> clazz = arguments[0].getClass();
        if(clazz.isArray()) {
            return new GuardedInvocation(GET_ARRAY_LENGTH.asType(callSiteType),
                    Guards.isArray(0, callSiteType));
        }
        if(Collection.class.isAssignableFrom(clazz)) {
            return new GuardedInvocation(GET_COLLECTION_LENGTH
                    .asType(callSiteType), Guards.isInstance(Collection.class,
                    callSiteType));
        }
        if(Map.class.isAssignableFrom(clazz)) {
            return new GuardedInvocation(GET_MAP_LENGTH.asType(callSiteType),
                    Guards.isInstance(Map.class, callSiteType));
        }
        // Can't retrieve length for objects that are neither arrays, nor
        // collections, nor maps.
        return null;
    }

    private GuardedInvocation getCallPropWithThis(
            CallSiteDescriptor callSiteDescriptor,
            LinkerServices linkerServices, Object... args) {
        final List<String> name = callSiteDescriptor.getTokenizedName();
        switch(name.size()) {
            case 3: {
                return getCallPropWithThis(callSiteDescriptor, linkerServices,
                        name.get(2), args);
            }
            default: {
                return null;
            }
        }
    }

    private GuardedInvocation getCallPropWithThis(
            CallSiteDescriptor callSiteDescriptor,
            LinkerServices linkerServices, String methodName, Object... args) {
        final MethodHandle invocation =
                getMethodInvocation(callSiteDescriptor, linkerServices,
                        methodName);
        return new GuardedInvocation(invocation, Guards.isOfClass(args[0]
                .getClass(), callSiteDescriptor.getMethodType()));
    }

    private MethodHandle getMethodInvocation(
            CallSiteDescriptor callSiteDescriptor,
            LinkerServices linkerServices, String methodName) {
        final DynamicMethod dynaMethod = methods.get(methodName);
        if(dynaMethod == null) {
            return null;
        }
        return dynaMethod == null ? null : dynaMethod.getInvocation(
                callSiteDescriptor, linkerServices);
    }

    private GuardedInvocation getPropertySetter(
            CallSiteDescriptor callSiteDescriptor,
            LinkerServices linkerServices, Object... arguments) {
        final List<String> name = callSiteDescriptor.getTokenizedName();
        final MethodType type = callSiteDescriptor.getMethodType();
        switch(name.size()) {
            case 2: {
                // Must have theee arguments: target object, property name, and
                // property value.
                callSiteDescriptor.assertParameterCount(3);
                // Create a new call site descriptor that drops the ID
                // argument. This is used for embedded overloaded method
                // lookup.
                final CallSiteDescriptor newDescriptor =
                        new CallSiteDescriptor(callSiteDescriptor.getName(),
                                type.dropParameterTypes(1, 2));
                return new GuardedInvocation(MethodHandles.insertArguments(
                        SET_PROPERTY_WITH_VARIABLE_ID, 0, newDescriptor,
                        linkerServices).asType(type), Guards.isOfClass(clazz,
                        type));
            }
            case 3: {
                // Must have two arguments: target object and property value
                callSiteDescriptor.assertParameterCount(2);
                // Fixed name - change to a call of setXxx() to allow for
                // overloaded setters
                return getCallPropWithThis(callSiteDescriptor, linkerServices,
                        getSetterMethodId(name.get(2)), arguments);
            }
            default: {
                // More than two name components; don't know what to do with it.
                return null;
            }
        }
    }

    private static String getSetterMethodId(String propertyName) {
        return "set" + Character.toUpperCase(propertyName.charAt(0))
                + propertyName.substring(1);
    }

    private GuardedInvocation getPropertyGetter(
            CallSiteDescriptor callSiteDescriptor) {
        final List<String> name = callSiteDescriptor.getTokenizedName();
        final MethodHandle getter;
        final MethodType type = callSiteDescriptor.getMethodType();
        switch(name.size()) {
            case 2: {
                // Must have exactly two arguments: receiver and name
                callSiteDescriptor.assertParameterCount(2);
                return new GuardedInvocation(GET_PROPERTY_WITH_VARIABLE_ID
                        .asType(type), Guards.isOfClass(clazz, type));
            }
            case 3: {
                // Must have exactly one argument: receiver
                callSiteDescriptor.assertParameterCount(1);
                // Fixed name
                final PropertyGetterDescriptor desc =
                        properties.get(name.get(2));
                if(desc == null) {
                    // No such property
                    return null;
                }
                getter = desc.getter;
                if(getter == null) {
                    // Property has no getter
                    return null;
                }
                // NOTE: since property getters are no-arg, we don't have to
                // worry about them being overloaded in a subclass. Therefore,
                // we can discover the most abstract superclass that has the
                // method, and use that as the guard with Guards.isInstance()
                // for a more stably linked call site.
                return new GuardedInvocation(getter.asType(type), Guards
                        .isInstance(desc.mostGenericClassForGetter, type));
            }
            default: {
                // Can't do anything with more than 3 name components
                return null;
            }
        }
    }

    private static final Lookup privateLookup =
            new Lookup(MethodHandles.lookup());

    private MethodHandle GET_PROPERTY_WITH_VARIABLE_ID =
            MethodHandles.insertArguments(privateLookup.findSpecial(
                    BeanLinker.class, "_getPropertyWithVariableId", MethodType
                            .methodType(Object.class, Object.class,
                                    Object.class)), 0, this);

    /**
     * This method is public for implementation reasons. Do not invoke it
     * directly. Retrieves a property value from an object.
     *
     * @param obj the object
     * @param id the property ID
     * @return the value of the property, or {@link Results#doesNotExist} if the
     * property does not exist, or {@link Results#notReadable} if the property
     * is not readable.
     * @throws Throwable rethrown underlying method handle invocation throwable.
     */
    public Object _getPropertyWithVariableId(Object obj, Object id)
            throws Throwable {
        PropertyGetterDescriptor desc = properties.get(String.valueOf(id));
        if(desc == null) {
            // No such property
            return Results.doesNotExist;
        }
        MethodHandle getter = desc.getter;
        if(getter == null) {
            return Results.notReadable;
        }
        return getter.invokeWithArguments(obj);
    }

    private MethodHandle SET_PROPERTY_WITH_VARIABLE_ID =
            MethodHandles.insertArguments(privateLookup.findSpecial(
                    BeanLinker.class, "_setPropertyWithVariableId", MethodType
                            .methodType(Results.class,
                                    CallSiteDescriptor.class,
                                    LinkerServices.class, Object.class,
                                    Object.class, Object.class)), 0, this);

    /**
     * This method is public for implementation reasons. Do not invoke it
     * directly. Sets a property on an object.
     *
     * @param callSiteDescriptor the descriptor of the setter call site
     * @param linkerServices the linker services used for value conversion
     * @param obj the object
     * @param id the property ID
     * @param value the new value for the property
     * @return {@link Results#ok} if the operation succeeded, or
     * {@link Results#notWritable} if the property is not writable.
     * @throws Throwable rethrown underlying method handle invocation throwable.
     */
    public Results _setPropertyWithVariableId(
            CallSiteDescriptor callSiteDescriptor,
            LinkerServices linkerServices, Object obj, Object id, Object value)
            throws Throwable {
        // TODO: this is quite likely terribly inefficient. Optimize.
        final MethodHandle invocation =
                getMethodInvocation(callSiteDescriptor, linkerServices,
                        getSetterMethodId(String.valueOf(id)));
        if(invocation != null) {
            invocation.invokeWithArguments(obj, value);
            return Results.ok;
        }
        return Results.notWritable;
    }

    private static class PropertyGetterDescriptor {
        // mostGenericClassForGetter is an optimization; since property getters
        // are no-arg, they can't be overloaded with the same number of
        // arguments. Therefore, it is safe to find the most generic superclass
        // or superinterface that declares the getter method, and use it as the
        // type guard, resulting in a more stable call site (one that'll be
        // relinked less).
        private final Class<?> mostGenericClassForGetter;
        private final MethodHandle getter;

        PropertyGetterDescriptor(Method getter) {
            getter =
                    getMostGenericGetter(getter.getName(), getter
                            .getReturnType(), getter.getDeclaringClass());
            this.getter = Lookup.PUBLIC.unreflect(getter);
            this.mostGenericClassForGetter = getter.getDeclaringClass();
        }

        private static Method getMostGenericGetter(String name,
                Class<?> returnType, Class<?> declaringClass) {
            if(declaringClass == null) {
                return null;
            }
            // Prefer interfaces
            for(Class<?> itf: declaringClass.getInterfaces()) {
                final Method itfGetter =
                        getMostGenericGetter(name, returnType, itf);
                if(itfGetter != null) {
                    return itfGetter;
                }
            }
            final Method superGetter =
                    getMostGenericGetter(name, returnType, declaringClass
                            .getSuperclass());
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
    }
}