/*
   Copyright 2009 Attila Szegedi

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
package org.dynalang.dynalink.support;

import java.lang.ClassValue;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;
import java.util.LinkedList;
import java.util.List;

import org.dynalang.dynalink.GuardedInvocation;
import org.dynalang.dynalink.GuardingTypeConverterFactory;
import org.dynalang.dynalink.LinkerServices;
import org.dynalang.dynalink.beans.support.TypeUtilities;

/**
 * A factory for type converters. This class is the main implementation behind
 * the {@link LinkerServices#convertArguments(MethodHandle, MethodType)}. It
 * manages the known {@link GuardingTypeConverterFactory} instances and creates
 * appropriate converters for method handles.
 * @author Attila Szegedi
 * @version $Id: $
 */
public class TypeConverterFactory {

    private final GuardingTypeConverterFactory[] factories;
    private final ClassValue<ClassMap<MethodHandle>> converters = new ClassValue<ClassMap<MethodHandle>>() {
        protected ClassMap<MethodHandle> computeValue(final Class<?> sourceType) {
            return new ClassMap<MethodHandle>(sourceType.getClassLoader()) {
                protected MethodHandle computeValue(Class<?> targetType) {
                    return createConverter(sourceType, targetType);
                }
            };
        }
    };

    /**
     * Creates a new type converter factory from the available
     * {@link GuardingTypeConverterFactory} instances.
     * @param factories the {@link GuardingTypeConverterFactory} instances to
     * compose.
     */
    public TypeConverterFactory(
            Iterable<GuardingTypeConverterFactory> factories) {
        final List<GuardingTypeConverterFactory> l = new LinkedList<GuardingTypeConverterFactory>();
        for (GuardingTypeConverterFactory factory : factories) {
            l.add(factory);
        }
        this.factories = l.toArray(new GuardingTypeConverterFactory[l.size()]);

    }

    /**
     * Creates an implementation of {@link LinkerServices} that relies on this
     * type converter factory.
     * @return an implementation of {@link LinkerServices}.
     */
    public LinkerServices createLinkerServices() {
        return new LinkerServices() {

            public boolean canConvert(Class<?> from, Class<?> to) {
                return TypeConverterFactory.this.canConvert(from, to);
            }

            public MethodHandle convertArguments(MethodHandle handle,
                    MethodType fromType)
            {
                return TypeConverterFactory.this.convertArguments(handle,
                        fromType);
            }

        };
    }

    /**
     * Similar to {@link MethodHandle#asType(MethodType)} except it also hooks
     * in method handles produced by {@link GuardingTypeConverterFactory}
     * implementations, providing for language-specific type coercing of
     * parameters. It will apply {@link MethodHandle#asType(MethodType)} for
     * all primitive-to-primitive, wrapper-to-primitive, primitive-to-wrapper
     * conversions as well as for all upcasts. For all other conversions, it'll
     * insert {@link MethodHandles#filterArguments(MethodHandle, int,
     * MethodHandle...)} with composite filters provided by
     * {@link GuardingTypeConverterFactory} implementations. It doesn't use
     * language-specific conversions on the return type.
     * @param handle target method handle
     * @param fromType the types of source arguments
     * @return a method handle that is a suitable combination of
     * {@link MethodHandle#asType(MethodType)} and
     * {@link MethodHandles#filterArguments(MethodHandle, int, MethodHandle...)}
     * with {@link GuardingTypeConverterFactory} produced type converters as
     * filters.
     */
    public MethodHandle convertArguments(MethodHandle handle,
            final MethodType fromType) {
        final MethodType toType = handle.type();
        final int l = toType.parameterCount();
        if(l != fromType.parameterCount()) {
            throw new WrongMethodTypeException("Parameter counts differ");
        }
        int pos = 0;
        List<MethodHandle> converters = new LinkedList<MethodHandle>();
        for(int i = 0; i < l; ++i) {
            final Class<?> fromParamType = fromType.parameterType(i);
            final Class<?> toParamType = toType.parameterType(i);
            if(canAutoConvert(fromParamType, toParamType)) {
                handle = applyConverters(handle, pos, converters);
            }
            else {
                final MethodHandle converter = getTypeConverter(fromParamType,
                    toParamType);
                if(converter != null) {
                    if(converters.isEmpty()) {
                        pos = i;
                    }
                    converters.add(converter);
                } else {
                    handle = applyConverters(handle, pos, converters);
                }
            }
        }
        return applyConverters(handle, pos, converters).asType(fromType);
    }

    private static MethodHandle applyConverters(MethodHandle handle, int pos,
        List<MethodHandle> converters) {
      if(!converters.isEmpty()) {
          handle = MethodHandles.filterArguments(handle, pos,
              converters.toArray(new MethodHandle[converters.size()]));
          converters.clear();
      }
      return handle;
    }

    /**
     * Returns true if there might exist a conversion between the requested
     * types (either an automatic JVM conversion, or one provided by any
     * available {@link GuardingTypeConverterFactory}), or false if there
     * definitely does not exist a conversion between the requested types. Note
     * that returning true does not guarantee that the conversion will succeed
     * at runtime (notably, if the "from" or "to" types are sufficiently
     * generic), but returning false guarantees that it would fail.
     * @param from the source type for the conversion
     * @param to the target type for the conversion
     * @return true if there can be a conversion, false if there can not.
     */
    public boolean canConvert(final Class<?> from, final Class<?> to) {
        return canAutoConvert(from, to) || getTypeConverter(from, to) != null;
    }

    /**
     * Determines whether it's safe to perform an automatic conversion
     * between the source and target class.
     * @param fromType convert from this class
     * @param toType convert to this class
     * @return true if it's safe to let MethodHandles.convertArguments() to
     * handle this conversion.
     */
    private static boolean canAutoConvert(final Class<?> fromType,
            final Class<?> toType)
    {
        if(fromType.isPrimitive()) {
            if(fromType == Void.TYPE) {
                return true;
            }
            if(toType.isPrimitive()) {
                if(toType == Void.TYPE) {
                    return true;
                }
                return canAutoConvertPrimitives(fromType, toType);
            }
            return canAutoConvertPrimitiveToReference(fromType, toType);
        }
        if(toType.isPrimitive()) {
            if(toType == Void.TYPE) {
                return true;
            }
            return canAutoConvertPrimitiveToReference(toType, fromType);
        }
        // In all other cases, only allow automatic conversion from a class to
        // its superclass or superinterface.
        return toType.isAssignableFrom(fromType);
    }

    private static boolean canAutoConvertPrimitiveToReference(
            final Class<?> primitiveType, final Class<?> refType)
    {
        return TypeUtilities.isAssignableFromBoxedPrimitive(refType) &&
            ((primitiveType != Byte.TYPE && primitiveType != Boolean.TYPE) ||
                    refType != Character.class);
    }

    private static boolean canAutoConvertPrimitives(Class<?> fromType, Class<?> toType) {
        // the only cast conversion not allowed between non-boolean primitives
        // is byte->char, all other narrowing and widening conversions are
        // allowed. boolean is converted to byte first, so same applies to it.
        return (fromType != Byte.TYPE && fromType != Boolean.TYPE) || toType != Character.TYPE;
    }

    private MethodHandle getTypeConverter(Class<?> sourceType, Class<?> targetType) {
        final MethodHandle converter = converters.get(sourceType).get(targetType);
        return converter == IDENTITY_CONVERSION ? null : converter;
    }

    private MethodHandle createConverter(Class<?> sourceType, Class<?> targetType) {
        final MethodType type = MethodType.methodType(targetType, sourceType);
        final MethodHandle identity = IDENTITY_CONVERSION.asType(type);
        MethodHandle last = identity;
        for(int i = factories.length; i --> 0;) {
            final GuardedInvocation next = factories[i].convertToType(
                    sourceType, targetType);
            if(next != null) {
                next.assertType(type);
                last = MethodHandles.guardWithTest(next.getGuard(),
                        next.getInvocation(), last);
            }
        }
        return last == identity ? IDENTITY_CONVERSION : last;
    }

    private static final MethodHandle IDENTITY_CONVERSION =
        new Lookup(MethodHandles.lookup()).findStatic(TypeConverterFactory.class,
                "_identityConversion", MethodType.methodType(Object.class,
                        Object.class));

    /**
     * This method is public for implementation reasons. Do not invoke it
     * directly. Returns the object passed in.
     * @param o the object
     * @return the object
     */
    public static Object _identityConversion(Object o) {
        return o;
    }
}