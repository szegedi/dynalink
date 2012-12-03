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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dynalang.dynalink.CallSiteDescriptor;
import org.dynalang.dynalink.beans.GuardedInvocationComponent.ValidationType;
import org.dynalang.dynalink.linker.GuardedInvocation;
import org.dynalang.dynalink.linker.GuardingDynamicLinker;
import org.dynalang.dynalink.linker.LinkRequest;
import org.dynalang.dynalink.linker.LinkerServices;
import org.dynalang.dynalink.support.CallSiteDescriptorFactory;
import org.dynalang.dynalink.support.Guards;
import org.dynalang.dynalink.support.Lookup;

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
    private final Map<String, DynamicMethod> methods = new HashMap<String, DynamicMethod>();

    AbstractJavaLinker(Class<?> clazz, MethodHandle classGuard) {
        this(clazz, classGuard, classGuard);
    }

    AbstractJavaLinker(Class<?> clazz, MethodHandle classGuard, MethodHandle assignableGuard) {
        this.clazz = clazz;
        this.classGuard = classGuard;
        this.assignableGuard = assignableGuard;

        final FacetIntrospector introspector = createFacetIntrospector();
        final AccessibleMethodsLookup accessibleLookup = new AccessibleMethodsLookup(clazz, isInstanceLinker());
        try {
            // Add explicit properties
            for(PropertyDescriptor descriptor: introspector.getProperties()) {
                final Method accReadMethod = accessibleLookup.getAccessibleMethod(descriptor.getReadMethod());
                final String name = descriptor.getName();
                if(accReadMethod != null) {
                    // getMostGenericGetter() will look for the most generic superclass that declares this getter. Since
                    // getters have zero args (aside from the receiver), they can't be overloaded, so we're free to link
                    // with an instanceof guard for the most generic one, creating more stable call sites.
                    setPropertyGetter(name, introspector.unreflect(getMostGenericGetter(accReadMethod)), true);
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
                    setPropertyGetter(name, introspector.unreflectGetter(field), false);
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

    abstract boolean isInstanceLinker();

    void setPropertyGetter(String name, MethodHandle handle, boolean overloadSafe) {
        propertyGetters.put(name, new AnnotatedMethodHandle(handle, overloadSafe));
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
        // BeansLinker already checked that the name is at least 2 elements long and the first element is "dyn".
        final CallSiteDescriptor callSiteDescriptor = ncrequest.getCallSiteDescriptor();
        final String op = callSiteDescriptor.getNameToken(CallSiteDescriptor.OPERATOR);
        // Either dyn:callMethod:name(this[,args]) or dyn:callMethod(this,name[,args]).
        if("callMethod" == op) {
            return getCallPropWithThis(callSiteDescriptor, linkerServices);
        }
        List<String> operations = CallSiteDescriptorFactory.tokenizeOperators(callSiteDescriptor);
        while(!operations.isEmpty()) {
            final GuardedInvocationComponent gic = getGuardedInvocationComponent(callSiteDescriptor, linkerServices,
                    operations);
            if(gic != null) {
                return gic.getGuardedInvocation();
            }
            operations = pop(operations);
        }
        return null;
    }

    protected GuardedInvocationComponent getGuardedInvocationComponent(CallSiteDescriptor callSiteDescriptor,
            LinkerServices linkerServices, List<String> operations) throws Exception {
        if(operations.isEmpty()) {
            return null;
        }
        final String op = operations.get(0);
        // Either dyn:getProp:name(this) or dyn:getProp(this, name)
        if("getProp".equals(op)) {
            return getPropertyGetter(callSiteDescriptor, linkerServices, pop(operations));
        }
        // Either dyn:setProp:name(this, value) or dyn:setProp(this, name, value)
        if("setProp".equals(op)) {
            return getPropertySetter(callSiteDescriptor, linkerServices, pop(operations));
        }
        // Either dyn:getMethod:name(this), or dyn:getMethod(this, name)
        if("getMethod".equals(op)) {
            return getMethodGetter(callSiteDescriptor, linkerServices, pop(operations));
        }
        return null;
    }

    static final <T> List<T> pop(List<T> l) {
        return l.subList(1, l.size());
    }

    MethodHandle getClassGuard(CallSiteDescriptor desc) {
        return getClassGuard(desc.getMethodType());
    }

    MethodHandle getClassGuard(MethodType type) {
        return Guards.asType(classGuard, type);
    }

    GuardedInvocationComponent getClassGuardedInvocationComponent(MethodHandle invocation, MethodType type) {
        return new GuardedInvocationComponent(invocation, getClassGuard(type), clazz, ValidationType.EXACT_CLASS);
    }

    private MethodHandle getAssignableGuard(MethodType type) {
        return Guards.asType(assignableGuard, type);
    }

    private GuardedInvocation getCallPropWithThis(CallSiteDescriptor callSiteDescriptor, LinkerServices linkerServices) {
        switch(callSiteDescriptor.getNameTokenCount()) {
            case 3: {
                return createGuardedDynamicMethodInvocation(callSiteDescriptor.getMethodType(), linkerServices,
                        callSiteDescriptor.getNameToken(CallSiteDescriptor.NAME_OPERAND), methods);
            }
            default: {
                return null;
            }
        }
    }

    private GuardedInvocation createGuardedDynamicMethodInvocation(MethodType callSiteType,
            LinkerServices linkerServices, String methodName, Map<String, DynamicMethod> methodMap){
        final MethodHandle inv = getDynamicMethodInvocation(callSiteType, linkerServices, methodName, methodMap);
        return inv == null ? null : new GuardedInvocation(inv, getClassGuard(callSiteType));
    }

    private static MethodHandle getDynamicMethodInvocation(MethodType callSiteType, LinkerServices linkerServices,
            String methodName, Map<String, DynamicMethod> methodMap) {
        final DynamicMethod dynaMethod = getDynamicMethod(methodName, methodMap);
        return dynaMethod != null ? dynaMethod.getInvocation(callSiteType, linkerServices) : null;
    }

    private static DynamicMethod getDynamicMethod(String methodName, Map<String, DynamicMethod> methodMap) {
        final DynamicMethod dynaMethod = methodMap.get(methodName);
        return dynaMethod != null ? dynaMethod : getExplicitSignatureDynamicMethod(methodName, methodMap);
    }

    private static SimpleDynamicMethod getExplicitSignatureDynamicMethod(String methodName,
            Map<String, DynamicMethod> methodsMap) {
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

        // Try to get a narrowed dynamic method for the explicit parameter types.
        return simpleNamedMethod.getMethodForExactParamTypes(methodName.substring(openBrace + 1, lastChar));
    }

    private static final MethodHandle IS_METHOD_HANDLE_NOT_NULL = Guards.isNotNull().asType(MethodType.methodType(
            boolean.class, MethodHandle.class));
    private static final MethodHandle CONSTANT_NULL_DROP_METHOD_HANDLE = MethodHandles.dropArguments(
            MethodHandles.constant(Object.class, null), 0, MethodHandle.class);

    private GuardedInvocationComponent getPropertySetter(CallSiteDescriptor callSiteDescriptor,
            LinkerServices linkerServices, List<String> operations) throws Exception {
        final MethodType type = callSiteDescriptor.getMethodType();
        switch(callSiteDescriptor.getNameTokenCount()) {
            case 2: {
                // Must have three arguments: target object, property name, and property value.
                assertParameterCount(callSiteDescriptor, 3);

                // What's below is basically:
                //   foldArguments(guardWithTest(isNotNull, invoke, null|nextComponent.invocation),
                //     get_setter_handle(type, linkerServices))
                // only with a bunch of method signature adjustments. Basically, retrieve method setter
                // MethodHandle; if it is non-null, invoke it, otherwise either return null, or delegate to next
                // component's invocation.

                // Call site type is "ret_type(object_type,property_name_type,property_value_type)", which we'll
                // abbreviate to R(O, N, V) going forward.
                // We want setters that conform to "R(O, V)"
                final MethodType setterType = type.dropParameterTypes(1, 2);
                // Bind property setter handle to the expected setter type and linker services. Type is
                // MethodHandle(Object, String, Object)
                final MethodHandle boundGetter = MethodHandles.insertArguments(getPropertySetterHandle, 0, setterType,
                        linkerServices);

                // Cast getter to MethodHandle(O, N, V)
                final MethodHandle typedGetter = linkerServices.asType(boundGetter, type.changeReturnType(
                        MethodHandle.class));

                // Handle to invoke the setter R(MethodHandle, O, V)
                final MethodHandle invokeHandle = MethodHandles.exactInvoker(setterType);
                // Handle to invoke the setter, dropping unnecessary fold arguments R(MethodHandle, O, N, V)
                final MethodHandle invokeHandleFolded = MethodHandles.dropArguments(invokeHandle, 2, type.parameterType(
                        1));
                final GuardedInvocationComponent nextComponent = getGuardedInvocationComponent(callSiteDescriptor,
                        linkerServices, operations);

                final MethodHandle fallbackFolded;
                if(nextComponent == null) {
                    // Object(MethodHandle)->R(MethodHandle, O, N, V); returns constant null
                    fallbackFolded = MethodHandles.dropArguments(CONSTANT_NULL_DROP_METHOD_HANDLE, 1,
                            type.parameterList()).asType(type.insertParameterTypes(0, MethodHandle.class));
                } else {
                    // R(O, N, V)->R(MethodHandle, O, N, V); adapts the next component's invocation to drop the
                    // extra argument resulting from fold
                    fallbackFolded = MethodHandles.dropArguments(nextComponent.getGuardedInvocation().getInvocation(),
                            0, MethodHandle.class);
                }

                // fold(R(MethodHandle, O, N, V), MethodHandle(O, N, V))
                final MethodHandle compositeSetter = MethodHandles.foldArguments(MethodHandles.guardWithTest(
                            IS_METHOD_HANDLE_NOT_NULL, invokeHandleFolded, fallbackFolded), typedGetter);
                if(nextComponent == null) {
                    return getClassGuardedInvocationComponent(compositeSetter, type);
                } else {
                    return nextComponent.compose(compositeSetter, getClassGuard(type), clazz,
                            ValidationType.EXACT_CLASS);
                }
            }
            case 3: {
                // Must have two arguments: target object and property value
                assertParameterCount(callSiteDescriptor, 2);
                final GuardedInvocation gi = createGuardedDynamicMethodInvocation(callSiteDescriptor.getMethodType(),
                        linkerServices, callSiteDescriptor.getNameToken(CallSiteDescriptor.NAME_OPERAND),
                        propertySetters);
                // If we have a property setter with this name, this composite operation will always stop here
                if(gi != null) {
                    return new GuardedInvocationComponent(gi, clazz, ValidationType.EXACT_CLASS);
                }
                // If we don't have a property setter with this name, always fall back to the next operation in the
                // composite (if any)
                return getGuardedInvocationComponent(callSiteDescriptor, linkerServices, operations);
            }
            default: {
                // More than two name components; don't know what to do with it.
                return null;
            }
        }
    }

    private static final Lookup privateLookup = new Lookup(MethodHandles.lookup());

    private static final MethodHandle IS_ANNOTATED_HANDLE_NOT_NULL = Guards.isNotNull().asType(MethodType.methodType(
            boolean.class, AnnotatedMethodHandle.class));
    private static final MethodHandle CONSTANT_NULL_DROP_ANNOTATED_HANDLE = MethodHandles.dropArguments(
            MethodHandles.constant(Object.class, null), 0, AnnotatedMethodHandle.class);
    private static final MethodHandle GET_ANNOTATED_HANDLE = privateLookup.findGetter(AnnotatedMethodHandle.class,
            "handle", MethodHandle.class);
    private static final MethodHandle GENERIC_PROPERTY_GETTER_HANDLER_INVOKER = MethodHandles.filterArguments(
            MethodHandles.invoker(MethodType.methodType(Object.class, Object.class)), 0, GET_ANNOTATED_HANDLE);

    private GuardedInvocationComponent getPropertyGetter(CallSiteDescriptor callSiteDescriptor,
            LinkerServices linkerServices, List<String> ops) throws Exception {
        final MethodType type = callSiteDescriptor.getMethodType();
        switch(callSiteDescriptor.getNameTokenCount()) {
            case 2: {
                // Must have exactly two arguments: receiver and name
                assertParameterCount(callSiteDescriptor, 2);

                // What's below is basically:
                //   foldArguments(guardWithTest(isNotNull, invoke(get_handle), null|nextComponent.invocation), get_getter_handle)
                // only with a bunch of method signature adjustments. Basically, retrieve method getter
                // AnnotatedMethodHandle; if it is non-null, invoke its "handle" field, otherwise either return null,
                // or delegate to next component's invocation.

                final MethodHandle typedGetter = linkerServices.asType(getPropertyGetterHandle, type.changeReturnType(
                        AnnotatedMethodHandle.class));
                // Object(AnnotatedMethodHandle, Object)->R(AnnotatedMethodHandle, T0)
                final MethodHandle invokeHandleTyped = linkerServices.asType(GENERIC_PROPERTY_GETTER_HANDLER_INVOKER,
                        MethodType.methodType(type.returnType(), AnnotatedMethodHandle.class, type.parameterType(0)));
                // Since it's in the target of a fold, drop the unnecessary second argument
                // R(AnnotatedMethodHandle, T0)->R(AnnotatedMethodHandle, T0, T1)
                final MethodHandle invokeHandleFolded = MethodHandles.dropArguments(invokeHandleTyped, 2,
                        type.parameterType(1));
                final GuardedInvocationComponent nextComponent = getGuardedInvocationComponent(callSiteDescriptor,
                        linkerServices, ops);

                final MethodHandle fallbackFolded;
                if(nextComponent == null) {
                    // Object(AnnotatedMethodHandle)->R(AnnotatedMethodHandle, T0, T1); returns constant null
                    fallbackFolded = MethodHandles.dropArguments(CONSTANT_NULL_DROP_ANNOTATED_HANDLE, 1,
                            type.parameterList()).asType(type.insertParameterTypes(0, AnnotatedMethodHandle.class));
                } else {
                    // R(T0, T1)->R(AnnotatedMethodHAndle, T0, T1); adapts the next component's invocation to drop the
                    // extra argument resulting from fold
                    fallbackFolded = MethodHandles.dropArguments(nextComponent.getGuardedInvocation().getInvocation(),
                            0, AnnotatedMethodHandle.class);
                }

                // fold(R(AnnotatedMethodHandle, T0, T1), AnnotatedMethodHandle(T0, T1))
                final MethodHandle compositeGetter = MethodHandles.foldArguments(MethodHandles.guardWithTest(
                            IS_ANNOTATED_HANDLE_NOT_NULL, invokeHandleFolded, fallbackFolded), typedGetter);
                if(nextComponent == null) {
                    return getClassGuardedInvocationComponent(compositeGetter, type);
                } else {
                    return nextComponent.compose(compositeGetter, getClassGuard(type), clazz,
                            ValidationType.EXACT_CLASS);
                }
            }
            case 3: {
                // Must have exactly one argument: receiver
                assertParameterCount(callSiteDescriptor, 1);
                // Fixed name
                final AnnotatedMethodHandle annGetter = propertyGetters.get(callSiteDescriptor.getNameToken(
                        CallSiteDescriptor.NAME_OPERAND));
                if(annGetter == null) {
                    // We have no such property, always delegate to the next component operation
                    return getGuardedInvocationComponent(callSiteDescriptor, linkerServices, ops);
                }
                final MethodHandle getter = annGetter.handle;
                // NOTE: since property getters (not field getters!) are no-arg, we don't have to worry about them being
                // overloaded in a subclass. Therefore, we can discover the most abstract superclass that has the
                // method, and use that as the guard with Guards.isInstance() for a more stably linked call site. If
                // we're linking against a field getter, don't make the assumption.
                // NOTE: No delegation to the next component operation if we have a property with this name, even if its
                // value is null.
                return new GuardedInvocationComponent(linkerServices.asType(getter, type), annGetter.overloadSafe ?
                        getAssignableGuard(type) : getClassGuard(type), clazz, annGetter.overloadSafe ?
                                ValidationType.INSTANCE_OF : ValidationType.EXACT_CLASS);
            }
            default: {
                // Can't do anything with more than 3 name components
                return null;
            }
        }
    }

    private static final MethodHandle IS_DYNAMIC_METHOD_NOT_NULL = Guards.asType(Guards.isNotNull(),
            MethodType.methodType(boolean.class, DynamicMethod.class));
    private static final MethodHandle DYNAMIC_METHOD_IDENTITY = MethodHandles.identity(DynamicMethod.class);

    private GuardedInvocationComponent getMethodGetter(CallSiteDescriptor callSiteDescriptor,
            LinkerServices linkerServices, List<String> ops) throws Exception {
        final MethodType type = callSiteDescriptor.getMethodType();
        switch(callSiteDescriptor.getNameTokenCount()) {
            case 2: {
                // Must have exactly two arguments: receiver and name
                assertParameterCount(callSiteDescriptor, 2);
                final GuardedInvocationComponent nextComponent = getGuardedInvocationComponent(callSiteDescriptor,
                        linkerServices, ops);
                if(nextComponent == null) {
                    // No next component operation; just return a component for this operation.
                    return getClassGuardedInvocationComponent(linkerServices.asType(getDynamicMethod, type), type);
                } else {
                    // What's below is basically:
                    //   foldArguments(guardWithTest(isNotNull, identity, nextComponent.invocation), getter)
                    // only with a bunch of method signature adjustments. Basically, execute method getter; if
                    // it returns a non-null DynamicMethod, use identity to return it, otherwise delegate to
                    // nextComponent's invocation.

                    final MethodHandle typedGetter = linkerServices.asType(getDynamicMethod, type.changeReturnType(
                            DynamicMethod.class));
                    // Since it is part of the foldArgument() target, it will have extra args that we need to drop.
                    final MethodHandle returnMethodHandle = linkerServices.asType(MethodHandles.dropArguments(
                            DYNAMIC_METHOD_IDENTITY, 1, type.parameterList()), type.insertParameterTypes(0,
                                    DynamicMethod.class));
                    final MethodHandle nextComponentInvocation = nextComponent.getGuardedInvocation().getInvocation();
                    // The assumption is that getGuardedInvocationComponent() already asType()'d it correctly
                    assert nextComponentInvocation.type().equals(type);
                    // Since it is part of the foldArgument() target, we have to drop an extra arg it receives.
                    final MethodHandle nextCombinedInvocation = MethodHandles.dropArguments(nextComponentInvocation, 0,
                            DynamicMethod.class);
                    // Assemble it all into a fold(guard(isNotNull, identity, nextInvocation), get)
                    final MethodHandle compositeGetter = MethodHandles.foldArguments(MethodHandles.guardWithTest(
                            IS_DYNAMIC_METHOD_NOT_NULL, returnMethodHandle, nextCombinedInvocation), typedGetter);

                    return nextComponent.compose(compositeGetter, getClassGuard(type), clazz,
                            ValidationType.EXACT_CLASS);
                }
            }
            case 3: {
                // Must have exactly one argument: receiver
                assertParameterCount(callSiteDescriptor, 1);
                final DynamicMethod method = getDynamicMethod(callSiteDescriptor.getNameToken(
                        CallSiteDescriptor.NAME_OPERAND));
                if(method == null) {
                    // We have no such method, always delegate to the next component
                    return getGuardedInvocationComponent(callSiteDescriptor, linkerServices, ops);
                }
                // No delegation to the next component of the composite operation; if we have a method with that name,
                // we'll always return it at this point.
                return getClassGuardedInvocationComponent(linkerServices.asType(MethodHandles.dropArguments(
                        MethodHandles.constant(DynamicMethod.class, method), 0, type.parameterType(0)), type), type);
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

    private static MethodHandle GET_PROPERTY_GETTER_HANDLE = MethodHandles.dropArguments(privateLookup.findOwnSpecial(
            "getPropertyGetterHandle", Object.class, Object.class), 1, Object.class);
    private final MethodHandle getPropertyGetterHandle = GET_PROPERTY_GETTER_HANDLE.bindTo(this);

    /**
     * @param id the property ID
     * @return the method handle for retrieving the property, or null if the property does not exist
     */
    @SuppressWarnings("unused")
    private Object getPropertyGetterHandle(Object id) {
        return propertyGetters.get(id);
    }

    // Type is MethodHandle(BeanLinker, MethodType, LinkerServices, Object, String, Object), of which the two "Object"
    // args are dropped; this makes handles with first three args conform to "Object, String, Object" though, which is
    // a typical property setter with variable name signature (target, name, value).
    private static final MethodHandle GET_PROPERTY_SETTER_HANDLE = MethodHandles.dropArguments(MethodHandles.dropArguments(
            privateLookup.findOwnSpecial("getPropertySetterHandle", MethodHandle.class, MethodType.class,
                    LinkerServices.class, Object.class), 3, Object.class), 5, Object.class);
    // Type is MethodHandle(MethodType, LinkerServices, Object, String, Object)
    private final MethodHandle getPropertySetterHandle = GET_PROPERTY_SETTER_HANDLE.bindTo(this);

    @SuppressWarnings("unused")
    private MethodHandle getPropertySetterHandle(MethodType setterType, LinkerServices linkerServices, Object id) {
        return getDynamicMethodInvocation(setterType, linkerServices, String.valueOf(id), propertySetters);
    }

    private static MethodHandle GET_DYNAMIC_METHOD = MethodHandles.dropArguments(privateLookup.findOwnSpecial(
            "getDynamicMethod", DynamicMethod.class, Object.class), 1, Object.class);
    private final MethodHandle getDynamicMethod = GET_DYNAMIC_METHOD.bindTo(this);

    @SuppressWarnings("unused")
    private DynamicMethod getDynamicMethod(Object name) {
        return getDynamicMethod(String.valueOf(name), methods);
    }

    /**
     * Returns a dynamic method of the specified name.
     *
     * @param name name of the method
     * @return the dynamic method (either {@link SimpleDynamicMethod} or {@link OverloadedDynamicMethod}, or null if the
     * method with the specified name does not exist.
     */
    public DynamicMethod getDynamicMethod(String name) {
        return getDynamicMethod(name, methods);
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
        final MethodHandle handle;
        /*private*/ final boolean overloadSafe;

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