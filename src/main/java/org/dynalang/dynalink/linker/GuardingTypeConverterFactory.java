/*
   Copyright 2009-2013 Attila Szegedi

   Licensed under either the Apache License, Version 2.0 (the "Apache
   License") or the BSD License (the "BSD License"), with licensee
   being free to choose either of the two at their discretion.

   You may not use this file except in compliance with either the Apache
   License or the BSD License.

   A copy of the BSD License is available in the root directory of the
   source distribution of the project under the file name
   "Dynalink-License-BSD.txt".

   A copy of the Apache License is available in the root directory of the
   source distribution of the project under the file name
   "Dynalink-License-Apache-2.0.txt". Alternatively, you may obtain a
   copy of the Apache License at <http://www.apache.org/licenses/LICENSE-2.0>

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See your chosen License for the specific language governing permissions
   and limitations under that License.
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