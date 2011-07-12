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

package org.dynalang.dynalink.linker;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.dynalang.dynalink.DynamicLinker;

/**
 * Interface for services provided to {@link GuardingDynamicLinker} instances by
 * the {@link DynamicLinker} that owns them. You can think of it as the
 * interface of the {@link DynamicLinker} that faces the
 * {@link GuardingDynamicLinker}s.
 *
 * @author Attila Szegedi
 * @version $Id: $
 */
public interface LinkerServices {
    /**
     * Similar to {@link MethodHandle#asType(MethodType)} except it also hooks
     * in method handles produced by {@link GuardingTypeConverterFactory}
     * implementations, providing for language-specific type coercing of
     * parameters. It will apply {@link MethodHandle#asType(MethodType)} for all
     * primitive-to-primitive, wrapper-to-primitive, primitive-to-wrapper
     * conversions as well as for all upcasts. For all other conversions, it'll
     * insert
     * {@link MethodHandles#filterArguments(MethodHandle, int, MethodHandle...)}
     * with composite filters provided by {@link GuardingTypeConverterFactory}
     * implementations. It doesn't use language-specific conversions on the
     * return type.
     *
     * @param handle target method handle
     * @param fromType the types of source arguments
     * @return a method handle that is a suitable combination of
     * {@link MethodHandle#asType(MethodType)} and
     * {@link MethodHandles#filterArguments(MethodHandle, int, MethodHandle...)}
     * with {@link GuardingTypeConverterFactory} produced type converters as
     * filters.
     */
    public MethodHandle asType(MethodHandle handle, MethodType fromType);

    /**
     * Returns true if there might exist a conversion between the requested
     * types (either an automatic JVM conversion, or one provided by any
     * available {@link GuardingTypeConverterFactory}), or false if there
     * definitely does not exist a conversion between the requested types. Note
     * that returning true does not guarantee that the conversion will succeed
     * at runtime (notably, if the "from" or "to" types are sufficiently
     * generic), but returning false guarantees that it would fail.
     *
     * @param from the source type for the conversion
     * @param to the target type for the conversion
     * @return true if there can be a conversion, false if there can not.
     */
    public boolean canConvert(Class<?> from, Class<?> to);

    /**
     * Creates a guarded invocation using the {@link DynamicLinker} that exposes
     * this linker services interface. Linkers can typically use them to
     * delegate linking of wrapped objects.
     *
     * @param linkRequest a request for linking the invocation
     * @return a guarded invocation linked by the top-level linker (or any of
     * its delegates). Can be null if no available linker is able to link the
     * invocation.
     * @throws Exception in case the top-level linker throws an exception
     */
    public GuardedInvocation getGuardedInvocation(LinkRequest linkRequest)
            throws Exception;
}