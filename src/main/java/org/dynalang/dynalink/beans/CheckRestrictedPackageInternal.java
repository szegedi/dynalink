/*
   Copyright 2009-2013 Attila Szegedi

   Licensed under both the Apache License, Version 2.0 (the "Apache License")
   and the BSD License (the "BSD License"), with licensee being free to
   choose either of the two at their discretion.

   You may not use this file except in compliance with either the Apache
   License or the BSD License.

   If you choose to use this file in compliance with the Apache License, the
   following notice applies to you:

       You may obtain a copy of the Apache License at

           http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing, software
       distributed under the License is distributed on an "AS IS" BASIS,
       WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
       implied. See the License for the specific language governing
       permissions and limitations under the License.

   If you choose to use this file in compliance with the BSD License, the
   following notice applies to you:

       Redistribution and use in source and binary forms, with or without
       modification, are permitted provided that the following conditions are
       met:
       * Redistributions of source code must retain the above copyright
         notice, this list of conditions and the following disclaimer.
       * Redistributions in binary form must reproduce the above copyright
         notice, this list of conditions and the following disclaimer in the
         documentation and/or other materials provided with the distribution.
       * Neither the name of the copyright holder nor the names of
         contributors may be used to endorse or promote products derived from
         this software without specific prior written permission.

       THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
       IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
       TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
       PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL COPYRIGHT HOLDER
       BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
       CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
       SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
       BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
       WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
       OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
       ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package org.dynalang.dynalink.beans;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * A utility class to check whether a given class is in a package with restricted access e.g. "sun.*". These packages
 * are normally listed in the security property "package.access" for most JRE implementations, although we fortunately
 * don't rely on it but solely on {@link SecurityManager#checkPackageAccess(String)}).
 *
 * This class accomplishes the check in a fashion that works reliably even if Dynalink itself (and all the code on the
 * stack that led to the invocation) has the permission to access the restricted package.
 *
 * If Dynalink has a broad set of privileges (notably, it is loaded from boot or extension class path), then it loads
 * the {@link RestrictedPackageTester} class into a isolated secure class loader that gives it no permissions
 * whatsoever, and uses this completely unprivileged class to subsequently invoke
 * {@link SecurityManager#checkPackageAccess(String)}. This will reliably throw a {@link SecurityException} for every
 * restricted package even if Dynalink and other code on the stack have the requisite {@code "accessClassInPackage.*"}
 * {@link RuntimePermission} privilege.
 *
 * On the other hand, if Dynalink does not have a broad set of privileges normally granted by the boot or extension
 * class path, it will probably lack the privilege to create a new secure class loader into which to load the tester
 * class. In this case, it will invoke {@link SecurityManager#checkPackageAccess(String)} itself with the reasoning that
 * it will also be sufficient to discover whether a package is restricted or not.
 *
 * The rationale for this design is that if Dynalink is running as part of a privileged classpath - boot or extension
 * class path, it will have all privileges, so a security manager's package access check might succeed if all other code
 * on the stack when requesting linking with a particular restricted class is also privileged. A subsequent linking
 * request from less privileged code would then also succeed in requesting methods in privileged package. On the other
 * hand, if Dynalink is privileged, it will be able to delegate the package access check to the unprivileged class and
 * narrow the access based on its result. Finally, if Dynalink itself is unprivileged, it will not be able to load the
 * unprivileged class, but then it will also fail the security manager's package access.
 *
 * With this design, Dynalink effectively restrains itself from giving unauthorized access to restricted packages from
 * classes doing the linking in case it itself has access to those packages. The only way to defeat it would be to
 * selectively give Dynalink some {@code "accessClassInPackage.*"} permissions while denying it the privilege to
 * manipulate class loaders.
 */
class CheckRestrictedPackageInternal {
    private static final MethodHandle PACKAGE_ACCESS_CHECK = getPackageAccessCheckMethod();
    private static final String TESTER_CLASS_NAME = "org.dynalang.dynalink.beans.RestrictedPackageTester";

    /**
     * Returns true if the specified package has restricted access.
     * @param pkgName the name of the package to check.
     * @return true if the specified package has restricted access, false otherwise.
     * @throws NullPointerException if pkgName is null, or if there is {@link System#getSecurityManager()} returns null
     * as this method is only expected to be invoked in the presence of a security manager.
     */
    static boolean isRestrictedPackageName(String pkgName) {
        try {
            if(PACKAGE_ACCESS_CHECK != null) {
                // If we were able to load our unprivileged tester class, use it to check package access
                try {
                    PACKAGE_ACCESS_CHECK.invokeExact(pkgName);
                } catch(Error|RuntimeException e) {
                    throw e;
                } catch(Throwable t) {
                    throw new RuntimeException(t);
                }
            } else {
                // If we didn't have sufficient permissions to load our unprivileged tester class, we're definitely not
                // running in a privileged class path, so invoking SecurityManager.checkPackageAccess() directly should
                // have the same effect as going through an unprivileged tester.
                System.getSecurityManager().checkPackageAccess(pkgName);
            }
            return false;
        } catch(SecurityException e) {
            return true;
        }
    }

    private static MethodHandle getPackageAccessCheckMethod() {
        try {
            return AccessController.doPrivileged(new PrivilegedAction<MethodHandle>() {
                @Override
                public MethodHandle run() {
                    return getPackageAccessCheckMethodInternal();
                }
            });
        } catch(SecurityException e) {
            // We don't have sufficient privileges to load our tester class into a separate protection domain, so just
            // return null so isRestrictedPackageName() will default to itself invoking
            // SecurityManager.checkPackageAccess().
            return null;
        }
    }

    static MethodHandle getPackageAccessCheckMethodInternal() {
        try {
            // Can't use MethodHandles.lookup().findStatic() -- even though both this class and the loaded class are in
            // the same package, findStatic() will throw an IllegalAccessException since they have different class
            // loaders. That's why we have to use unreflect with a setAccessible(true)...
            final Method m = getTesterClass().getDeclaredMethod("checkPackageAccess", String.class);
            m.setAccessible(true);
            return MethodHandles.lookup().unreflect(m);
        } catch(IllegalAccessException|NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }

    private static Class<?> getTesterClass() {
        return ZeroPermissionsClassLoader.loadClass(TESTER_CLASS_NAME);
    }
}
