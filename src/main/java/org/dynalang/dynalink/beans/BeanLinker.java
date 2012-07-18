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

import org.dynalang.dynalink.linker.CallSiteDescriptor;
import org.dynalang.dynalink.linker.GuardedInvocation;
import org.dynalang.dynalink.linker.LinkRequest;
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
    public GuardedInvocation getGuardedInvocation(LinkRequest request, final LinkerServices linkerServices)
            throws Exception {
        final GuardedInvocation gi = super.getGuardedInvocation(request, linkerServices);
        if(gi != null) {
            return gi;
        }
        final LinkRequest ncrequest = request.withoutRuntimeContext();
        // BeansLinker already checked that the name is at least 2 elements long and the first element is "dyn".
        final CallSiteDescriptor callSiteDescriptor = ncrequest.getCallSiteDescriptor();
        final String op = callSiteDescriptor.getNameToken(1);
        final Object[] arguments = ncrequest.getArguments();
        // dyn:getElem(this, id)
        // id is typically either an int (for arrays and lists) or an object (for maps). linkerServices can provide
        // conversion from call site argument type though.
        if("getElem" == op) {
            return getElementGetter(callSiteDescriptor, linkerServices, arguments);
        }
        if("setElem" == op) {
            return getElementSetter(callSiteDescriptor, linkerServices, arguments);
        }
        // dyn:getLength(this) (works on Java arrays, collections, and maps)
        if("getLength" == op) {
            return getLengthGetter(callSiteDescriptor, arguments);
        }
        return null;
    }

    private static MethodHandle GET_LIST_ELEMENT = Lookup.PUBLIC.findVirtual(List.class, "get",
            MethodType.methodType(Object.class, int.class));

    private static MethodHandle GET_MAP_ELEMENT = Lookup.PUBLIC.findVirtual(Map.class, "get",
            MethodType.methodType(Object.class, Object.class));

    private static MethodHandle LIST_GUARD = Guards.getInstanceOfGuard(List.class);
    private static MethodHandle MAP_GUARD = Guards.getInstanceOfGuard(Map.class);

    private GuardedInvocation getElementGetter(final CallSiteDescriptor callSiteDescriptor,
            final LinkerServices linkerServices, final Object... arguments) {
        assertParameterCount(callSiteDescriptor, 2);
        final MethodType callSiteType = callSiteDescriptor.getMethodType();
        final Class<?> declaredType = callSiteType.parameterType(0);
        // If declared type of receiver at the call site is already an array, a list or map, bind without guard. Thing
        // is, it'd be quite stupid of a call site creator to go though invokedynamic when it knows in advance they're
        // dealing with an array, or a list or map, but hey...
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
        // Can't retrieve elements for objects that are neither arrays, nor list, nor maps.
        return null;
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

    private static MethodHandle COLLECTION_GUARD = Guards.getInstanceOfGuard(Collection.class);

    private GuardedInvocation getLengthGetter(CallSiteDescriptor callSiteDescriptor, Object... arguments) {
        assertParameterCount(callSiteDescriptor, 1);
        final MethodType callSiteType = callSiteDescriptor.getMethodType();
        final Class<?> declaredType = callSiteType.parameterType(0);
        // If declared type of receiver at the call site is already an array, collection, or map, bind without guard.
        // Thing is, it'd be quite stupid of a call site creator to go though invokedynamic when it knows in advance
        // they're dealing with an array, collection, or map, but hey...
        if(declaredType.isArray()) {
            return new GuardedInvocation(GET_ARRAY_LENGTH.asType(callSiteType), null);
        } else if(Collection.class.isAssignableFrom(declaredType)) {
            return new GuardedInvocation(GET_COLLECTION_LENGTH.asType(callSiteType), null);
        } else if(Map.class.isAssignableFrom(declaredType)) {
            return new GuardedInvocation(GET_MAP_LENGTH.asType(callSiteType), null);
        }

        // Otherwise, create a binding based on the actual type of the argument with an appropriate guard.
        if(clazz.isArray()) {
            return new GuardedInvocation(GET_ARRAY_LENGTH.asType(callSiteType), Guards.isArray(0, callSiteType));
        } if(Collection.class.isAssignableFrom(clazz)) {
            return new GuardedInvocation(GET_COLLECTION_LENGTH.asType(callSiteType), Guards.asType(COLLECTION_GUARD,
                    callSiteType));
        } if(Map.class.isAssignableFrom(clazz)) {
            return new GuardedInvocation(GET_MAP_LENGTH.asType(callSiteType), Guards.asType(MAP_GUARD, callSiteType));
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