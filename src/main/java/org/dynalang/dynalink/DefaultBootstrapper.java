package org.dynalang.dynalink;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;


/**
 * A convenience default bootstrapper that exposes a static bootstrap method to
 * which language runtimes that need the very default behavior can use with
 * minimal setup. When first referenced, it will create a dynamic linker with
 * default settings for the {@link DynamicLinkerFactory}, and its bootstrap
 * method will create {@link MonomorphicCallSite} for all call sites.
 *
 * @author Attila Szegedi
 * @version $Id: $
 */
public class DefaultBootstrapper {
    private static final DynamicLinker dynamicLinker =
            new DynamicLinkerFactory().createLinker();

    private DefaultBootstrapper() {
    }

    /**
     * Use this method as your bootstrap method (see the documentation of the
     * java.lang.invoke package for how to do this).
     *
     * @param caller the caller's lookup
     * @param name the name of the method at the call site
     * @param type the method signature at the call site
     * @return a new {@link MonomorphicCallSite} linked with the default dynamic
     * linker.
     */
    public static CallSite bootstrap(MethodHandles.Lookup caller, String name,
            MethodType type) {
        final MonomorphicCallSite callSite =
                new MonomorphicCallSite(caller, name, type);
        dynamicLinker.link(callSite);
        return callSite;
    }
}