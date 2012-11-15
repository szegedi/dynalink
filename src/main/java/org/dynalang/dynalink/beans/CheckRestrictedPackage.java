package org.dynalang.dynalink.beans;

import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.Security;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A utility class to check whether a given package has restricted access. If Dynalink code has the {@code
 * "getProperty.package.access"} {@code SecurityPermission}, then it will use the value of the security property
 * {@code "package.access"} to figure out if the package is restricted. If Dynalink code does not have this permission
 * it will delegate to {@link SecurityManager#checkPackageAccess(String)}.
 * The rationale for this design is that if Dynalink is running as part of a privileged classpath - boot or extension
 * class path, it will have all privileges, so a security manager's package access check might succeed if all other code
 * on the stack when requesting linking with a particular restricted class is also privileged. A subsequent linking
 * request from less privileged code would then also succeed in requesting methods in privileged package. On the other
 * hand, if Dynalink is privileged, it will be able to read the security property specifying restricted packages and
 * narrow the access based on it. Finally, if Dynalink itself is unprivileged, it will not be able to read the security
 * property, but it will also fail the security manager's package access.
 * With this design, Dynalink effectively restrains itself from giving unauthorized access to restricted packages from
 * classes doing the linking in case it itself has access to those packages.
 */
class CheckRestrictedPackage {
    private static AtomicReference<PackageAccessInfo> packageAccessInfoRef = new AtomicReference<>();
    // Check if we have the privilege to read the security property; if we don't, don't bother doing it later.
    private static final boolean canReadProperty = canReadProperty();

    static boolean isRestrictedClass(Class<?> clazz) {
        return !Modifier.isPublic(clazz.getModifiers()) || isRestrictedPackage(clazz.getPackage());
    }

    private static boolean isRestrictedPackage(Package pkg) {
        return pkg == null ? false : isRestrictedPackageName(pkg.getName());
    }

    static boolean isRestrictedPackageName(String pkgName) {
        if(System.getSecurityManager() == null) {
            // Free for all
            return false;
        }

        try {
            // First defer to the security manager
            System.getSecurityManager().checkPackageAccess(pkgName);
        } catch(SecurityException e) {
            return true;
        }

        if(!canReadProperty) {
            // If we know we are unable to read the property, don't bother
            return false;
        }

        try {
            // Make further restrictions in case security manager gave us a pass because all code on the stack is
            // privileged.
            return isPackageNameOnRestrictedList(pkgName);
        } catch(SecurityException e) {
            // We couldn't read "package.access" property; presume package is not restricted as security manager let it
            // pass and we are obviously not too privileged. If we get here, it means that we used to be allowed to read
            // the property (canReadProperty==true), but we lost the privilege.
            return false;
        }
    }

    private static boolean isPackageNameOnRestrictedList(String pkgName) {
        for(;;) {
            final String packageAccessSpec = readProperty();
            PackageAccessInfo packageAccessInfo = packageAccessInfoRef.get();
            if(packageAccessInfo == null || !packageAccessInfo.matches(packageAccessSpec)) {
                final PackageAccessInfo newPackageAccessInfo = new PackageAccessInfo(packageAccessSpec);
                if(!packageAccessInfoRef.compareAndSet(packageAccessInfo, newPackageAccessInfo)) {
                    continue;
                }
                packageAccessInfo = newPackageAccessInfo;
            }
            return packageAccessInfo.isRestrictedPackage(pkgName);
        }
    }

    private static boolean canReadProperty() {
        try {
            readProperty();
            return true;
        } catch(SecurityException e) {
            return false;
        }
    }

    private static String readProperty() {
        return AccessController.doPrivileged(new PrivilegedAction<String>() {
            @Override
            public String run() {
                return Security.getProperty("package.access");
            }
        });
    }

    private static class PackageAccessInfo {
        private final String packageAccessSpec;
        private final String[] restrictedPackageNames;

        PackageAccessInfo(String packageAccessSpec) {
            this.packageAccessSpec = packageAccessSpec;
            if(packageAccessSpec == null) {
                restrictedPackageNames = null;
            } else {
                final StringTokenizer tok = new StringTokenizer(packageAccessSpec, ",");
                final int count = tok.countTokens();
                restrictedPackageNames = new String[count];
                for(int i = 0; i < count; ++i) {
                    restrictedPackageNames[i] = tok.nextToken().trim();
                }
            }
        }

        boolean matches(String spec) {
            return packageAccessSpec == spec;
        }

        boolean isRestrictedPackage(String pkgName) {
            if(restrictedPackageNames == null || pkgName == null) {
                return false;
            }
            for (int i = 0; i < restrictedPackageNames.length; i++) {
                if (pkgName.startsWith(restrictedPackageNames[i]) || restrictedPackageNames[i].equals(pkgName + ".")) {
                    return true;
                }
            }
            return false;
        }
    }
}
