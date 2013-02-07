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

import java.lang.reflect.Modifier;

/**
 * A utility class to check whether a given class is in a package with restricted access e.g. "sun.*" etc. See
 * {@link CheckRestrictedPackageInternal} for implementation details.
 */
class CheckRestrictedPackage {
    /**
     * Returns true if the class is either not public, or it resides in a package with restricted access.
     * @param clazz the class to test
     * @return true if the class is either not public, or it resides in a package with restricted access.
     */
    static boolean isRestrictedClass(Class<?> clazz) {
        return !Modifier.isPublic(clazz.getModifiers()) ||
                (System.getSecurityManager() != null && isRestrictedPackage(clazz.getPackage()));
    }

    private static boolean isRestrictedPackage(Package pkg) {
        // Note: we broke out the actual implementation into CheckRestrictedPackageInternal, so we only load it when
        // needed - that is, if we need to check a non-public class with a non-null package, in presence of a security
        // manager.
        return pkg == null ? false : CheckRestrictedPackageInternal.isRestrictedPackageName(pkg.getName());
    }
}
