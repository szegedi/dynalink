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
