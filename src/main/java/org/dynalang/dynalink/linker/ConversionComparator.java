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


/**
 * Optional interface to be implemented by {@link GuardingTypeConverterFactory} implementers. Language-specific
 * conversions can cause increased overloaded method resolution ambiguity, as many methods can become applicable because
 * of additional conversions. The static way of selecting the "most specific" method will fail more often, because there
 * will be multiple maximally specific method with unrelated signatures. In these cases, language runtimes can be asked
 * to resolve the ambiguity by expressing preferences for one conversion over the other.
 * @author Attila Szegedi
 */
public interface ConversionComparator {
    /**
     * Enumeration of possible outcomes of comparing one conversion to another.
     */
    enum Comparison {
        INDETERMINATE,
        TYPE_1_BETTER,
        TYPE_2_BETTER,
    }

    /**
     * Determines which of the two target types is the preferred conversion target from a source type.
     * @param sourceType the source type.
     * @param targetType1 one potential target type
     * @param targetType2 another potential target type.
     * @return one of Comparison constants that establish which - if any - of the target types is preferred for the
     * conversion.
     */
    public Comparison compareConversion(Class<?> sourceType, Class<?> targetType1, Class<?> targetType2);
}
