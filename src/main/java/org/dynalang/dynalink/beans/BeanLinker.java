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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.dynalang.dynalink.CallSiteDescriptor;
import org.dynalang.dynalink.beans.GuardedInvocationComponent.ValidationType;
import org.dynalang.dynalink.linker.GuardedInvocation;
import org.dynalang.dynalink.linker.LinkerServices;
import org.dynalang.dynalink.linker.TypeBasedGuardingDynamicLinker;
import org.dynalang.dynalink.support.Guards;
import org.dynalang.dynalink.support.Lookup;

/**
 * A class that provides linking capabilities for a single POJO class. Normally not used directly, but managed by
 * {@link BeansLinker}.
 *
 * @author Attila Szegedi
 */
class BeanLinker extends AbstractJavaLinker implements TypeBasedGuardingDynamicLinker {
    BeanLinker(Class<?> clazz) {
        super(clazz, Guards.getClassGuard(clazz), Guards.getInstanceOfGuard(clazz));
        if(clazz.isArray()) {
            // Some languages won't have a notion of manipulating collections. Exposing "length" on arrays as an
            // explicit property is beneficial for them.
            // REVISIT: is it maybe a code smell that "dyn:getLength" is not needed?
            addPropertyGetter("length", GET_ARRAY_LENGTH, false);
        }
    }

    @Override
    public boolean canLinkType(Class<?> type) {
        return type == clazz;
    }

    @Override
    FacetIntrospector createFacetIntrospector() {
        return new BeanIntrospector(clazz);
    }

    @Override
    protected GuardedInvocationComponent getGuardedInvocationComponent(CallSiteDescriptor callSiteDescriptor,
            LinkerServices linkerServices, List<String> operations) throws Exception {
        final GuardedInvocationComponent superGic = super.getGuardedInvocationComponent(callSiteDescriptor,
                linkerServices, operations);
        if(superGic != null) {
            return superGic;
        }
        if(operations.isEmpty()) {
            return null;
        }
        final String op = operations.get(0);
        // dyn:getElem(this, id)
        // id is typically either an int (for arrays and lists) or an object (for maps). linkerServices can provide
        // conversion from call site argument type though.
        if("getElem".equals(op)) {
            return getElementGetter(callSiteDescriptor, linkerServices, pop(operations));
        }
        if("setElem".equals(op)) {
            return getElementSetter(callSiteDescriptor, linkerServices, pop(operations));
        }
        // dyn:getLength(this) (works on Java arrays, collections, and maps)
        if("getLength".equals(op)) {
            return getLengthGetter(callSiteDescriptor);
        }
        return null;
    }

    private static MethodHandle GET_LIST_ELEMENT = Lookup.PUBLIC.findVirtual(List.class, "get",
            MethodType.methodType(Object.class, int.class));

    private static MethodHandle GET_MAP_ELEMENT = Lookup.PUBLIC.findVirtual(Map.class, "get",
            MethodType.methodType(Object.class, Object.class));

    private static MethodHandle LIST_GUARD = Guards.getInstanceOfGuard(List.class);
    private static MethodHandle MAP_GUARD = Guards.getInstanceOfGuard(Map.class);

    private GuardedInvocationComponent getElementGetter(final CallSiteDescriptor callSiteDescriptor,
            final LinkerServices linkerServices, List<String> operations) throws Exception {
        final MethodType callSiteType = callSiteDescriptor.getMethodType();
        final Class<?> declaredType = callSiteType.parameterType(0);
        final GuardedInvocationComponent nextComponent = getGuardedInvocationComponent(callSiteDescriptor,
                linkerServices, operations);

        // If declared type of receiver at the call site is already an array, a list or map, bind without guard. Thing
        // is, it'd be quite stupid of a call site creator to go though invokedynamic when it knows in advance they're
        // dealing with an array, or a list or map, but hey...
        // Note that for arrays and lists, using LinkerServices.asType() will ensure that any language specific linkers
        // in use will get a chance to perform any (if there's any) implicit conversion to integer for the indices.
        final GuardedInvocationComponent gic;;
        final boolean isMap;
        if(declaredType.isArray()) {
            gic = new GuardedInvocationComponent(MethodHandles.arrayElementGetter(declaredType));
            isMap = false;
        } else if(List.class.isAssignableFrom(declaredType)) {
            gic = new GuardedInvocationComponent(GET_LIST_ELEMENT);
            isMap = false;
        } else if(Map.class.isAssignableFrom(declaredType)) {
            gic = new GuardedInvocationComponent(GET_MAP_ELEMENT);
            isMap = true;
        } else if(clazz.isArray()) {
            gic = getClassGuardedInvocationComponent(MethodHandles.arrayElementGetter(clazz), callSiteType);
            isMap = false;
        } else if(List.class.isAssignableFrom(clazz)) {
            gic = new GuardedInvocationComponent(GET_LIST_ELEMENT, Guards.asType(LIST_GUARD, callSiteType), List.class,
                    ValidationType.INSTANCE_OF);
            isMap = false;
        } else if(Map.class.isAssignableFrom(clazz)) {
            gic = new GuardedInvocationComponent(GET_MAP_ELEMENT, Guards.asType(MAP_GUARD, callSiteType), Map.class,
                    ValidationType.INSTANCE_OF);
            isMap = true;
        } else {
            // Can't retrieve elements for objects that are neither arrays, nor list, nor maps.
            return nextComponent;
        }

        // We can have "dyn:getElem:foo", especially in composites, i.e. "dyn:getElem|getProp|getMethod:foo"
        final String fixedKey = getFixedKey(callSiteDescriptor);
        // Convert the key to a number if we're working with a list or array
        final Object typedFixedKey;
        if(!isMap && fixedKey != null) {
            typedFixedKey = convertKey(fixedKey, linkerServices);
            if(typedFixedKey == null) {
                // key is not numeric, it can never succeed
                return nextComponent;
            }
        } else {
            typedFixedKey = fixedKey;
        }

        final GuardedInvocation gi = gic.getGuardedInvocation();
        final Binder binder = new Binder(linkerServices, callSiteType, typedFixedKey);
        final MethodHandle invocation = gi.getInvocation();

        if(nextComponent == null) {
            return gic.replaceInvocation(binder.bind(invocation));
        } else {
            final MethodHandle checkGuard;
            if(invocation == GET_LIST_ELEMENT) {
                checkGuard = RANGE_CHECK_LIST;
            } else if(invocation == GET_MAP_ELEMENT) {
                // TODO: A more complex solution could be devised for maps, one where we do a get() first, and fold it
                // into a GWT that tests if it returned null, and if it did, do another GWT with containsKey()
                // that returns constant null (on true), or falls back to next component (on false)
                checkGuard = CONTAINS_MAP;
            } else {
                checkGuard = RANGE_CHECK_ARRAY;
            }
            return nextComponent.compose(MethodHandles.guardWithTest(binder.bindTest(checkGuard),
                    binder.bind(invocation), nextComponent.getGuardedInvocation().getInvocation()), gi.getGuard(),
                    gic.getValidatorClass(), gic.getValidationType());
        }
    }

    private static String getFixedKey(final CallSiteDescriptor callSiteDescriptor) {
        return callSiteDescriptor.getNameTokenCount() == 2 ? null : callSiteDescriptor.getNameToken(
                CallSiteDescriptor.NAME_OPERAND);
    }

    private static Object convertKey(String fixedKey, LinkerServices linkerServices) throws Exception {
        try {
            if(linkerServices.canConvert(String.class, Integer.class)) {
                try {
                    return linkerServices.getTypeConverter(String.class, Integer.class).invoke(fixedKey);
                } catch(Exception|Error e) {
                    throw e;
                } catch(Throwable t) {
                    throw new RuntimeException(t);
                }
            }
            return Integer.valueOf(fixedKey);
        } catch(NumberFormatException e) {
            // key is not a number
            return null;
        }
    }

    /**
     * Contains methods to adapt an item getter/setter method handle to the requested type, optionally binding it to a
     * fixed key first.
     * @author Attila Szegedi
     * @version $Id: $
     */
    private static class Binder {
        private final LinkerServices linkerServices;
        private final MethodType methodType;
        private final Object fixedKey;

        Binder(LinkerServices linkerServices, MethodType methodType, Object fixedKey) {
            this.linkerServices = linkerServices;
            this.methodType = fixedKey == null ? methodType : methodType.insertParameterTypes(1, fixedKey.getClass());
            this.fixedKey = fixedKey;
        }

        private MethodHandle bind(MethodHandle handle) {
            return bindToFixedKey(linkerServices.asType(handle, methodType));
        }

        private MethodHandle bindTest(MethodHandle handle) {
            return bindToFixedKey(Guards.asType(handle, methodType));
        }

        private MethodHandle bindToFixedKey(MethodHandle handle) {
            return fixedKey == null ? handle : MethodHandles.insertArguments(handle, 1, fixedKey);
        }
    }

    private static MethodHandle RANGE_CHECK_ARRAY = findRangeCheck(Object.class);
    private static MethodHandle RANGE_CHECK_LIST = findRangeCheck(List.class);
    private static MethodHandle CONTAINS_MAP = Lookup.PUBLIC.findVirtual(Map.class, "containsKey",
            MethodType.methodType(boolean.class, Object.class));

    private static MethodHandle findRangeCheck(Class<?> collectionType) {
        return Lookup.findOwnStatic(MethodHandles.lookup(), "rangeCheck", boolean.class, collectionType, Object.class);
    }

    @SuppressWarnings("unused")
    private static final boolean rangeCheck(Object array, Object index) {
        if(!(index instanceof Number)) {
            return false;
        }
        final int intIndex = ((Number)index).intValue();
        return 0 <= intIndex && intIndex < Array.getLength(array);
    }

    @SuppressWarnings("unused")
    private static final boolean rangeCheck(List<?> list, Object index) {
        if(!(index instanceof Number)) {
            return false;
        }
        final int intIndex = ((Number)index).intValue();
        return 0 <= intIndex && intIndex < list.size();
    }

    private static MethodHandle SET_LIST_ELEMENT = Lookup.PUBLIC.findVirtual(List.class, "set",
            MethodType.methodType(Object.class, int.class, Object.class));

    private static MethodHandle PUT_MAP_ELEMENT = Lookup.PUBLIC.findVirtual(Map.class, "put",
            MethodType.methodType(Object.class, Object.class, Object.class));

    private GuardedInvocationComponent getElementSetter(CallSiteDescriptor callSiteDescriptor,
            LinkerServices linkerServices, List<String> operations) throws Exception {
        final MethodType callSiteType = callSiteDescriptor.getMethodType();
        final Class<?> declaredType = callSiteType.parameterType(0);

        final GuardedInvocationComponent gic;
        // If declared type of receiver at the call site is already an array, a list or map, bind without guard. Thing
        // is, it'd be quite stupid of a call site creator to go though invokedynamic when it knows in advance they're
        // dealing with an array, or a list or map, but hey...
        // Note that for arrays and lists, using LinkerServices.asType() will ensure that any language specific linkers
        // in use will get a chance to perform any (if there's any) implicit conversion to integer for the indices.
        final boolean isMap;
        if(declaredType.isArray()) {
            gic = new GuardedInvocationComponent(MethodHandles.arrayElementSetter(declaredType));
            isMap = false;
        } else if(List.class.isAssignableFrom(declaredType)) {
            gic = new GuardedInvocationComponent(SET_LIST_ELEMENT);
            isMap = false;
        } else if(Map.class.isAssignableFrom(declaredType)) {
            gic = new GuardedInvocationComponent(PUT_MAP_ELEMENT);
            isMap = true;
        } else if(clazz.isArray()) {
            gic = getClassGuardedInvocationComponent(MethodHandles.arrayElementSetter(clazz), callSiteType);
            isMap = false;
        } else if(List.class.isAssignableFrom(clazz)) {
            gic = new GuardedInvocationComponent(SET_LIST_ELEMENT, Guards.asType(LIST_GUARD, callSiteType), List.class,
                    ValidationType.INSTANCE_OF);
            isMap = false;
        } else if(Map.class.isAssignableFrom(clazz)) {
            gic = new GuardedInvocationComponent(PUT_MAP_ELEMENT, Guards.asType(MAP_GUARD, callSiteType), Map.class,
                    ValidationType.INSTANCE_OF);
            isMap = true;
        } else {
            // Can't set elements for objects that are neither arrays, nor list, nor maps.
            gic = null;
            isMap = false;
        }

        // In contrast to, say, getElementGetter, we only compute the nextComponent if the target object is not a map,
        // as maps will always succeed in setting the element and will never need to fall back to the next component
        // operation.
        final GuardedInvocationComponent nextComponent = isMap ? null : getGuardedInvocationComponent(
                callSiteDescriptor, linkerServices, operations);
        if(gic == null) {
            return nextComponent;
        }

        // We can have "dyn:setElem:foo", especially in composites, i.e. "dyn:setElem|setProp:foo"
        final String fixedKey = getFixedKey(callSiteDescriptor);
        // Convert the key to a number if we're working with a list or array
        final Object typedFixedKey;
        if(!isMap && fixedKey != null) {
            typedFixedKey = convertKey(fixedKey, linkerServices);
            if(typedFixedKey == null) {
                // key is not numeric, it can never succeed
                return nextComponent;
            }
        } else {
            typedFixedKey = fixedKey;
        }

        final GuardedInvocation gi = gic.getGuardedInvocation();
        final Binder binder = new Binder(linkerServices, callSiteType, typedFixedKey);
        final MethodHandle invocation = gi.getInvocation();

        if(nextComponent == null) {
            return gic.replaceInvocation(binder.bind(invocation));
        } else {
            final MethodHandle checkGuard = invocation == SET_LIST_ELEMENT ? RANGE_CHECK_LIST : RANGE_CHECK_ARRAY;
            return nextComponent.compose(MethodHandles.guardWithTest(binder.bindTest(checkGuard),
                    binder.bind(invocation), nextComponent.getGuardedInvocation().getInvocation()), gi.getGuard(),
                    gic.getValidatorClass(), gic.getValidationType());
        }
    }

    private static MethodHandle GET_ARRAY_LENGTH = Lookup.PUBLIC.findStatic(Array.class, "getLength",
            MethodType.methodType(int.class, Object.class));

    private static MethodHandle GET_COLLECTION_LENGTH = Lookup.PUBLIC.findVirtual(Collection.class, "size",
            MethodType.methodType(int.class));

    private static MethodHandle GET_MAP_LENGTH = Lookup.PUBLIC.findVirtual(Map.class, "size",
            MethodType.methodType(int.class));

    private static MethodHandle COLLECTION_GUARD = Guards.getInstanceOfGuard(Collection.class);

    private GuardedInvocationComponent getLengthGetter(CallSiteDescriptor callSiteDescriptor) {
        assertParameterCount(callSiteDescriptor, 1);
        final MethodType callSiteType = callSiteDescriptor.getMethodType();
        final Class<?> declaredType = callSiteType.parameterType(0);
        // If declared type of receiver at the call site is already an array, collection, or map, bind without guard.
        // Thing is, it'd be quite stupid of a call site creator to go though invokedynamic when it knows in advance
        // they're dealing with an array, collection, or map, but hey...
        if(declaredType.isArray()) {
            return new GuardedInvocationComponent(GET_ARRAY_LENGTH.asType(callSiteType));
        } else if(Collection.class.isAssignableFrom(declaredType)) {
            return new GuardedInvocationComponent(GET_COLLECTION_LENGTH.asType(callSiteType));
        } else if(Map.class.isAssignableFrom(declaredType)) {
            return new GuardedInvocationComponent(GET_MAP_LENGTH.asType(callSiteType));
        }

        // Otherwise, create a binding based on the actual type of the argument with an appropriate guard.
        if(clazz.isArray()) {
            return new GuardedInvocationComponent(GET_ARRAY_LENGTH.asType(callSiteType), Guards.isArray(0,
                    callSiteType), ValidationType.IS_ARRAY);
        } if(Collection.class.isAssignableFrom(clazz)) {
            return new GuardedInvocationComponent(GET_COLLECTION_LENGTH.asType(callSiteType), Guards.asType(
                    COLLECTION_GUARD, callSiteType), Collection.class, ValidationType.INSTANCE_OF);
        } if(Map.class.isAssignableFrom(clazz)) {
            return new GuardedInvocationComponent(GET_MAP_LENGTH.asType(callSiteType), Guards.asType(MAP_GUARD,
                    callSiteType), Map.class, ValidationType.INSTANCE_OF);
        }
        // Can't retrieve length for objects that are neither arrays, nor collections, nor maps.
        return null;
    }

    private static void assertParameterCount(CallSiteDescriptor descriptor, int paramCount) {
        if(descriptor.getMethodType().parameterCount() != paramCount) {
            throw new BootstrapMethodError(descriptor.getName() + " must have exactly " + paramCount + " parameters.");
        }
    }
}