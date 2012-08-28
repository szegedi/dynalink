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

package org.dynalang.dynalink.linker;

import org.dynalang.dynalink.support.TypeUtilities;

/**
 * Optional interface that can be implemented by {@link GuardingDynamicLinker} implementations to provide
 * language-runtime specific implicit type conversion capabilities. Note that if you implement this interface, you will
 * very likely want to implement {@link ConversionComparator} interface too, as your additional language-specific
 * conversions, in absence of a strategy for prioritizing these conversions, will cause more ambiguity in selecting the
 * correct overload when trying to link to an overloaded POJO method.
 *
 * @author Attila Szegedi
 */
public interface GuardingTypeConverterFactory {
    /**
     * Returns a guarded invocation that receives an Object of the specified source type and returns an Object converted
     * to the specified target type. The type of the invocation is targetType(sourceType), while the type of the guard
     * is boolean(sourceType). Note that this will never be invoked for type conversions allowed by the JLS 5.3 "Method
     * Invocation Conversion", see {@link TypeUtilities#isMethodInvocationConvertible(Class, Class)} for details. An
     * implementation can assume it is never requested to produce a converter for these conversions.
     *
     * @param sourceType source type
     * @param targetType the target type.
     * @return a guarded invocation that can take an object (if it passes guard) and returns another object that is its
     * representation coerced into the target type. In case the factory is certain it is unable to handle a conversion,
     * it can return null. In case the factory is certain that it can always handle the conversion, it can return an
     * unconditional invocation (one whose guard is null).
     * @throws Exception if there was an error during creation of the converter
     */
    public GuardedInvocation convertToType(Class<?> sourceType, Class<?> targetType) throws Exception;
}