package org.dynalang.dynalink.beans;

import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * This class is never referenced directly from code of any other class, but is loaded into a secure class loader that
 * gives it no permissions whatsoever, so it can be used to reliably test whether a given package has restricted access
 * or not. See {@link CheckRestrictedPackageInternal} for details.
 * @author Attila Szegedi
 * @version $Id: $
 */
class RestrictedPackageTester implements PrivilegedAction<Void> {

    private final String pkgName;

    private RestrictedPackageTester(String pkgName) {
        this.pkgName = pkgName;
    }

    static void checkPackageAccess(String pkgName) {
        AccessController.doPrivileged(new RestrictedPackageTester(pkgName));
    }

    @Override
    public Void run() {
        System.getSecurityManager().checkPackageAccess(pkgName);
        return null;
    }
}
