package org.dynalang.dynalink.beans;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.Permissions;
import java.security.ProtectionDomain;
import java.security.SecureClassLoader;

/**
 * A utility class that can load a class with specified name into an isolated zero-permissions protection domain. It can
 * be used to load classes that perform security-sensitive operations with no privileges at all, therefore ensuring such
 * operations will only succeed if they would require no permissions, as well as to make sure that if these operations
 * bind some part of the security execution context to their results, the bound security context is completely
 * unprivileged. Such measures serve as firebreaks against accidental privilege escalation.
 */
class ZeroPermissionsClassLoader {

    private final String className;

    private ZeroPermissionsClassLoader(String className) {
        this.className = className;
    }

    /**
     * Load the named class into a zero-permissions protection domain. Even if the class is already loaded into the
     * Dynalink's class loader, an independent class is created from the same bytecode, thus the returned class will
     * never be identical with the one that might already be loaded.
     * @param className the fully qualified name of the class to load
     * @return the loaded class, lacking any permissions.
     * @throws SecurityException if the calling code lacks the {@code createClassLoader} runtime permission. This
     * normally means that Dynalink itself is running as untrusted code, and whatever functionality was meant to be
     * isolated into an unprivileged class is likely okay to be used directly too.
     */
    static Class<?> loadClass(String className) throws SecurityException {
        return new ZeroPermissionsClassLoader(className).loadClass();
    }

    private Class<?> loadClass() throws SecurityException {
        final ClassLoader loader = createClassLoader();
        try {
            final Class<?> clazz = Class.forName(className, true, loader);
            // Sanity check to ensure we didn't accidentally pick up the class from elsewhere
            if(clazz.getClassLoader() != loader) {
                throw new AssertionError(className + " was loaded from a different class loader");
            }
            return clazz;
        } catch(ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    private ClassLoader createClassLoader() throws SecurityException {
        final String lclassName = this.className;
        // We deliberately override loadClass instead of findClass so that we don't give a chance to finding this
        // class already loaded anywhere else. We use this class' loader as we need to be able to access implemented
        // interfaces etc.
        return new SecureClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if(name.equals(lclassName)) {
                    final byte[] bytes = getClassBytes();
                    // Define the class with a protection domain that grants (almost) no permissions.
                    Class<?> clazz = defineClass(name, bytes, 0, bytes.length, createMinimalPermissionsDomain());
                    if(resolve) {
                        resolveClass(clazz);
                    }
                    return clazz;
                }

                final int i = name.lastIndexOf('.');
                if (i != -1) {
                    final SecurityManager sm = System.getSecurityManager();
                    if (sm != null) {
                        sm.checkPackageAccess(name.substring(0, i));
                    }
                }
                return super.loadClass(name, resolve);
            }
        };
    }

    /**
     * Create a no-permissions protection domain. Except, it's not really a no-permissions protection domain, since we
     * need to give the protection domain the permission to access the package of the class being loaded.
     * @return a new (almost) no-permission protection domain.
     */
    private ProtectionDomain createMinimalPermissionsDomain() {
        final Permissions p = new Permissions();
        p.add(new RuntimePermission("accessClassInPackage." + className.substring(0, className.lastIndexOf('.'))));
        return new ProtectionDomain(null, p);
    }

    private byte[] getClassBytes() {
        try {
            final InputStream in = getClass().getResourceAsStream("/" + className.replace('.', '/') + ".class");
            try {
                final ByteArrayOutputStream out = new ByteArrayOutputStream(2048);
                for(;;) {
                    final int b = in.read();
                    if(b == -1) {
                        break;
                    }
                    out.write(b);
                }
                return out.toByteArray();
            } finally {
                in.close();
            }
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }
}
