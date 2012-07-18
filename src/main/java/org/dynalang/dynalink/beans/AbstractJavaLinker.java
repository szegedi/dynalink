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

/**
 * Base class for linkers that can take a bunch of fields and
 * @author Attila Szegedi
 */

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
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.dynalang.dynalink.Results;
import org.dynalang.dynalink.linker.CallSiteDescriptor;
import org.dynalang.dynalink.linker.GuardedInvocation;
import org.dynalang.dynalink.linker.GuardingDynamicLinker;
import org.dynalang.dynalink.linker.LinkRequest;
import org.dynalang.dynalink.linker.LinkerServices;
import org.dynalang.dynalink.support.Guards;
import org.dynalang.dynalink.support.Lookup;
import org.dynalang.dynalink.support.TypeUtilities;

/**
 * A base class for both {@link StaticClassLinker} and {@link BeanLinker}. Deals with common aspects of property
 * exposure and method calls for both static and instance facets of a class.
 *
 * @author Attila Szegedi
 */
abstract class AbstractJavaLinker implements GuardingDynamicLinker {
    final Class<?> clazz;
    private final MethodHandle classGuard;
    private final MethodHandle assignableGuard;
    private final Map<String, AnnotatedMethodHandle> propertyGetters = new HashMap<String, AnnotatedMethodHandle>();
    private final Map<String, DynamicMethod> propertySetters = new HashMap<String, DynamicMethod>();
    private final ConcurrentMap<String, DynamicMethod> methods = new ConcurrentHashMap<String, DynamicMethod>();

    AbstractJavaLinker(Class<?> clazz, MethodHandle classGuard) {
        this(clazz, classGuard, classGuard);
    }

    AbstractJavaLinker(Class<?> clazz, MethodHandle classGuard, MethodHandle assignableGuard) {
        this.clazz = clazz;
        this.classGuard = classGuard;
        this.assignableGuard = assignableGuard;

        final FacetIntrospector introspector = createFacetIntrospector();
        final AccessibleMethodsLookup accessibleLookup = new AccessibleMethodsLookup(clazz);
        try {
            // Add explicit properties
            for(PropertyDescriptor descriptor: introspector.getProperties()) {
                final Method accReadMethod = accessibleLookup.getAccessibleMethod(descriptor.getReadMethod());
                final String name = descriptor.getName();
                if(accReadMethod != null) {
                    // getMostGenericGetter() will look for the most generic superclass that declares this getter. Since
                    // getters have zero args (aside from the receiver), they can't be overloaded, so we're free to link
                    // with an instanceof guard for the most generic one, creating more stable call sites.
                    addPropertyGetter(name, introspector.unreflect(getMostGenericGetter(accReadMethod)), true);
                }
                final Method accWriteMethod = accessibleLookup.getAccessibleMethod(descriptor.getWriteMethod());
                if(accWriteMethod != null) {
                    propertySetters.put(name, new SimpleDynamicMethod(introspector.unreflect(accWriteMethod)));
                }
            }

            final Collection<Field> fields = introspector.getFields();
            // Add field getters
            for(Field field: fields) {
                final String name = field.getName();
                if(!propertyGetters.containsKey(name)) {
                    // Only add field getter if we don't have an explicit property getter with the same name
                    addPropertyGetter(name, introspector.unreflectGetter(field), false);
                }
            }

            // Add instance methods
            for(Method method: introspector.getMethods()) {
                final Method accMethod = accessibleLookup.getAccessibleMethod(method);
                if(accMethod == null) {
                    continue;
                }
                final String name = method.getName();
                final MethodHandle methodHandle = introspector.unreflect(accMethod);
                addMember(name, methodHandle, methods);
                // Check if this method can be an alternative property setter
                if(isPropertySetter(accMethod)) {
                    addMember(Introspector.decapitalize(name.substring(3)), methodHandle, propertySetters);
                }
            }

            // Add field setters as property setters, but only for fields that have no property setter defined.
            for(Field field: fields) {
                if(Modifier.isFinal(field.getModifiers())) {
                    continue;
                }
                final String name = field.getName();
                if(!propertySetters.containsKey(name)) {
                    addMember(name, introspector.unreflectSetter(field), propertySetters);
                }
            }
        } finally {
            introspector.close();
        }
    }

    abstract FacetIntrospector createFacetIntrospector();

    void addPropertyGetter(String name, MethodHandle handle, boolean overloadSafe) {
        propertyGetters.put(name, new AnnotatedMethodHandle(handle, overloadSafe));
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

    private void addMember(String name, MethodHandle mh, Map<String, DynamicMethod> methodMap) {
        final DynamicMethod existingMethod = methodMap.get(name);
        final DynamicMethod newMethod = addMember(mh, existingMethod, clazz, name);
        if(newMethod != existingMethod) {
            methodMap.put(name, newMethod);
        }
    }

    static DynamicMethod createDynamicMethod(Iterable<MethodHandle> methodHandles, Class<?> clazz, String name) {
        DynamicMethod dynMethod = null;
        for(MethodHandle methodHandle: methodHandles) {
            dynMethod = addMember(methodHandle, dynMethod, clazz, name);
        }
        return dynMethod;
    }

    private static DynamicMethod addMember(MethodHandle mh, DynamicMethod existing, Class<?> clazz, String name) {
        if(existing == null) {
            return new SimpleDynamicMethod(mh);
        } else if(existing.contains(mh)) {
            return existing;
        } else if(existing instanceof SimpleDynamicMethod) {
            final OverloadedDynamicMethod odm = new OverloadedDynamicMethod(clazz, name);
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
    public GuardedInvocation getGuardedInvocation(LinkRequest request, final LinkerServices linkerServices)
            throws Exception {
        final LinkRequest ncrequest = request.withoutRuntimeContext();
        // BeansLinker already checked that the name is at least 2 elements
        // long and the first element is "dyn".
        final CallSiteDescriptor callSiteDescriptor = ncrequest.getCallSiteDescriptor();
        final String op = callSiteDescriptor.getNameToken(1);
        // Either dyn:getProp:name(this) or dyn:getProp(this, name)
        if("getProp" == op) {
            return getPropertyGetter(callSiteDescriptor);
        }
        // Either dyn:setProp:name(this, value) or dyn:setProp(this, name,
        // value)
        if("setProp" == op) {
            return getPropertySetter(callSiteDescriptor, linkerServices, ncrequest.getArguments());
        }
        // Either dyn:callPropWithThis:name(this[,args]) or
        // dyn:callPropWithThis(this,name[,args]).
        if("callPropWithThis" == op) {
            return getCallPropWithThis(callSiteDescriptor, linkerServices, ncrequest.getArguments());
        }
        return null;
    }

    MethodHandle getClassGuard(CallSiteDescriptor desc) {
        return getClassGuard(desc.getMethodType());
    }

    MethodHandle getClassGuard(MethodType type) {
        return Guards.asType(classGuard, type);
    }

    private MethodHandle getAssignableGuard(MethodType type) {
        return Guards.asType(assignableGuard, type);
    }

    private GuardedInvocation getCallPropWithThis(CallSiteDescriptor callSiteDescriptor, LinkerServices linkerServices,
            Object... args) throws ClassNotFoundException {
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
            LinkerServices linkerServices, String methodName, Map<String, DynamicMethod> methodMap)
    throws ClassNotFoundException {
        final MethodHandle invocation =
                getDynamicMethodInvocation(callSiteDescriptor, linkerServices, methodName, methodMap);
        return invocation == null ? null : new GuardedInvocation(invocation,
                getClassGuard(callSiteDescriptor));
    }

    private MethodHandle getDynamicMethodInvocation(CallSiteDescriptor callSiteDescriptor,
            LinkerServices linkerServices, String methodName, Map<String, DynamicMethod> methodMap)
    throws ClassNotFoundException {
        DynamicMethod dynaMethod = methodMap.get(methodName);
        if(dynaMethod == null) {
            dynaMethod = getExplicitSignatureDynamicMethod(callSiteDescriptor, linkerServices, methodName, methodMap);
            if(dynaMethod == null) {
                return null;
            }
        }
        return dynaMethod.getInvocation(callSiteDescriptor, linkerServices);
    }

    private DynamicMethod getExplicitSignatureDynamicMethod(CallSiteDescriptor callSiteDescriptor,
            LinkerServices linkerServices, String methodName, Map<String, DynamicMethod> methodsMap)
    throws ClassNotFoundException {
        // What's below is meant to support the "name(type, type, ...)" syntax that programmers can use in a method name
        // to manually pin down an exact overloaded variant. This is not usually required, as the overloaded method
        // resolution works correctly in almost every situation. However, in presence of many language-specific
        // conversions with a radically dynamic language, most overloaded methods will end up being constantly selected
        // at invocation time, so a programmer knowledgable of the situation might choose to pin down an exact overload
        // for performance reasons.

        // Is the method name lexically of the form "name(types)"?
        final int lastChar = methodName.length() - 1;
        if(methodName.charAt(lastChar) != ')') {
            return null;
        }
        final int openBrace = methodName.indexOf('(');
        if(openBrace == -1) {
            return null;
        }

        // Find an existing method for the "name" part
        final DynamicMethod simpleNamedMethod = methodsMap.get(methodName.substring(0, openBrace));
        if(simpleNamedMethod == null) {
            return null;
        }

        // Try to get the handle for the explicit parameter types. Note that any formal parameter types on the method
        // must be visible to declaring class' loader, so we can use it for class name resolution.
        final MethodHandle explicitHandle = simpleNamedMethod.getInvocation(callSiteDescriptor, linkerServices,
                getTypes(methodName.substring(openBrace + 1, lastChar), clazz.getClassLoader()));
        if(explicitHandle == null) {
            return null;
        }

        // Encapsulate the handle in a SimpleDynamicMethod
        final DynamicMethod simpleDynaMethod;
        if(simpleNamedMethod instanceof SimpleDynamicMethod) {
            simpleDynaMethod = simpleNamedMethod;
        } else {
            simpleDynaMethod = new SimpleDynamicMethod(explicitHandle);
        }

        // Save it for subsequent lookups
        methodsMap.put(methodName, simpleDynaMethod );

        return simpleDynaMethod;
    }

    private static List<Class<?>> getTypes(String typeSpec, ClassLoader classLoader) throws ClassNotFoundException {
        final StringTokenizer tok = new StringTokenizer(typeSpec, ",");
        final List<Class<?>> list = new ArrayList<>(tok.countTokens());
        while(tok.hasMoreTokens()) {
            final String name = tok.nextToken().trim();
            Class<?> type;
            if(name.indexOf('.') == -1) {
                type = TypeUtilities.getPrimitiveTypeByName(name);
                if(type == null) {
                    type = Class.forName("java.lang." + name, true, classLoader);
                }
            } else {
                type = Class.forName(name, true, classLoader);
            }
            list.add(type);
        }
        return list;
    }

    private GuardedInvocation getPropertySetter(CallSiteDescriptor callSiteDescriptor, LinkerServices linkerServices,
            Object... arguments) throws ClassNotFoundException {
        final MethodType type = callSiteDescriptor.getMethodType();
        switch(callSiteDescriptor.getNameTokenCount()) {
            case 2: {
                // Must have three arguments: target object, property name, and property value.
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
                // NOTE: since property getters (not field getters!) are no-arg, we don't have to worry about them being
                // overloaded in a subclass. Therefore, we can discover the most abstract superclass that has the
                // method, and use that as the guard with Guards.isInstance() for a more stably linked call site. If
                // we're linking against a field getter, don't make the assumption.
                return new GuardedInvocation(getter.asType(type), annGetter.overloadSafe ? getAssignableGuard(type) :
                    getClassGuard(type));
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
    // TODO: WHY IS THIS INSTANCE METHOD?
    private MethodHandle GET_PROPERTY_WITH_VARIABLE_ID = privateLookup.findSpecial(AbstractJavaLinker.class,
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
    //TODO: WHY IS THIS INSTANCE METHOD?
    private MethodHandle SET_PROPERTY_WITH_VARIABLE_ID = privateLookup.findSpecial(AbstractJavaLinker.class,
            "_setPropertyWithVariableId", MethodType.methodType(Results.class, CallSiteDescriptor.class,
                    LinkerServices.class, Object.class, Object.class, Object.class)).bindTo(this);

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
        final MethodHandle invocation =
                getDynamicMethodInvocation(callSiteDescriptor, linkerServices, String.valueOf(id), propertySetters);
        if(invocation != null) {
            invocation.invokeWithArguments(obj, value);
            return Results.ok;
        }
        return Results.notWritable;
    }

    private static Method getMostGenericGetter(Method getter) {
        return getMostGenericGetter(getter.getName(), getter.getReturnType(), getter.getDeclaringClass());
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