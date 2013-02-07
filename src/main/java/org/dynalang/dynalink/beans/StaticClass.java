/*
   Copyright 2009-2013 Attila Szegedi

   Licensed under either the Apache License, Version 2.0 (the "Apache
   License") or the 3-clause BSD License (the "BSD License"), with licensee
   being free to choose either of the two at their discretion.

   You may not use this file except in compliance with either the Apache
   License or the BSD License.

   A copy of the BSD License is available in the root directory of the
   source distribution of the project under the file name
   "LICENSE-BSD.txt".

   A copy of the Apache License is available in the root directory of the
   source distribution of the project under the file name
   "LICENSE-Apache-2.0.txt". Alternatively, you may obtain a copy of the
   Apache License at <http://www.apache.org/licenses/LICENSE-2.0>

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See your chosen License for the specific language governing permissions
   and limitations under that License.
*/

package org.dynalang.dynalink.beans;

import java.io.Serializable;

/**
 * Object that represents the static facet of a class (its static methods, properties, and fields, as well as
 * construction of instances using "dyn:new"). Objects of this class are recognized by the {@link BeansLinker} as being
 * special, and operations on them will be linked against the represented class' static facet. The "class" synthetic
 * property is additionally recognized and returns the Java {@link Class} object, as per {@link #getRepresentedClass()}
 * method. Conversely, {@link Class} objects exposed through {@link BeansLinker} expose the "static" synthetic property
 * which returns an instance of this class.
 */
public class StaticClass implements Serializable {
    private static final ClassValue<StaticClass> staticClasses = new ClassValue<StaticClass>() {
        @Override
        protected StaticClass computeValue(Class<?> type) {
            return new StaticClass(type);
        }
    };

    private static final long serialVersionUID = 1L;

    private final Class<?> clazz;

    /*private*/ StaticClass(Class<?> clazz) {
        clazz.getClass(); // NPE check
        this.clazz = clazz;
    }

    /**
     * Retrieves the {@link StaticClass} instance for the specified class.
     * @param clazz the class for which the static facet is requested.
     * @return the {@link StaticClass} instance representing the specified class.
     */
    public static StaticClass forClass(Class<?> clazz) {
        return staticClasses.get(clazz);
    }

    /**
     * Returns the represented Java class.
     * @return the represented Java class.
     */
    public Class<?> getRepresentedClass() {
        return clazz;
    }

    @Override
    public String toString() {
        return "JavaClassStatics[" + clazz.getName() + "]";
    }

    private Object readResolve() {
        return forClass(clazz);
    }
}