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

package org.dynalang.dynalink.support;

import java.util.LinkedList;
import java.util.List;
import java.util.ServiceLoader;

import org.dynalang.dynalink.DynamicLinkerFactory;
import org.dynalang.dynalink.linker.GuardingDynamicLinker;

/**
 * Provides methods for automatic discovery of all guarding dynamic linkers listed in the
 * <tt>/META-INF/services/org.dynalang.dynalink.linker.GuardingDynamicLinker</tt> resources of all JAR files for a
 * particular class loader. Ordinarily, you will not use this class directly, but you will use a
 * {@link DynamicLinkerFactory} instead.
 */
public class AutoDiscovery {

    private AutoDiscovery() {
    }

    /**
     * Discovers all guarding dynamic linkers listed in JAR files of the context class loader of the current thread.
     *
     * @return a list of available linkers. Can be zero-length list but not null.
     */
    public static List<GuardingDynamicLinker> loadLinkers() {
        return getLinkers(ServiceLoader.load(GuardingDynamicLinker.class));
    }

    /**
     * Discovers all guarding dynamic linkers listed in JAR files of the specified class loader.
     *
     * @param cl the class loader to use
     * @return a list of guarding dynamic linkers available through the specified class loader. Can be zero-length list
     * but not null.
     */
    public static List<GuardingDynamicLinker> loadLinkers(ClassLoader cl) {
        return getLinkers(ServiceLoader.load(GuardingDynamicLinker.class, cl));
    }

    /**
     * I can't believe there's no Collections API for making a List given an Iterator...
     */
    private static <T> List<T> getLinkers(ServiceLoader<T> loader) {
        final List<T> list = new LinkedList<T>();
        for(final T linker: loader) {
            list.add(linker);
        }
        return list;
    }
}