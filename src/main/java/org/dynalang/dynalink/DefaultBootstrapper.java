package org.dynalang.dynalink;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.dynalang.dynalink.linker.CallSiteDescriptor;

/**
 * A convenience default bootstrapper that exposes static bootstrap methods which language runtimes that need the very
 * default behavior can use with minimal setup. When first referenced, it will create a dynamic linker with default
 * settings for the {@link DynamicLinkerFactory}, and its bootstrap methods will create {@link MonomorphicCallSite} for
 * all call sites. It has two bootstrap methods: one creates call sites that use the
 * {@link MethodHandles#publicLookup()} as the lookup for all call sites and disregard the one passed in as the caller,
 * and one that just uses the passed caller as the lookup scope. Using the public lookup one is advised if your language
 * runtime has no concept of interacting with Java visibility scopes, as it results in a more lightweight runtime
 * information.
 *
 * @author Attila Szegedi
 * @version $Id: $
 */
public class DefaultBootstrapper {
    private static final DynamicLinker dynamicLinker = new DynamicLinkerFactory().createLinker();

    private DefaultBootstrapper() {
    }

    /**
     * Use this method as your bootstrap method (see the documentation of the java.lang.invoke package for how to do
     * this). In case your language runtime doesn't have a concept of interaction with Java access scopes, you might
     * want to consider using
     * {@link #publicBootstrap(java.lang.invoke.MethodHandles.Lookup, String, MethodType, Object...)} instead.
     *
     * @param caller the caller's lookup
     * @param name the name of the method at the call site
     * @param type the method signature at the call site
     * @param args additional static constant arguments passed at the call site to the bootstrap method
     * @return a new {@link MonomorphicCallSite} linked with the default dynamic linker.
     */
    public static CallSite bootstrap(MethodHandles.Lookup caller, String name, MethodType type, final Object... args) {
        return bootstrapInternal(caller, name, type, args);
    }

    /**
     * Use this method as your bootstrap method (see the documentation of the java.lang.invoke package for how to do
     * this) when your language runtime doesn't have a concept of interaction with Java access scopes. If you need to
     * preserve the different caller Lookup objects in the call sites, use
     * {@link #bootstrap(java.lang.invoke.MethodHandles.Lookup, String, MethodType, Object...)} instead
     *
     * @param caller the caller's lookup. It is ignored as the call sites will be created with
     * {@link MethodHandles#publicLookup()} instead.
     * @param name the name of the method at the call site
     * @param type the method signature at the call site
     * @param args additional static constant arguments passed at the call site to the bootstrap method
     * @return a new {@link MonomorphicCallSite} linked with the default dynamic linker.
     */
    public static CallSite publicBootstrap(MethodHandles.Lookup caller, String name, MethodType type,
            final Object... args) {
        return bootstrapInternal(MethodHandles.publicLookup(), name, type, args);
    }

    private static CallSite bootstrapInternal(MethodHandles.Lookup caller, String name, MethodType type,
            final Object... args) {
        final MonomorphicCallSite callSite =
                new MonomorphicCallSite(CallSiteDescriptor.create(caller, name, type, args));
        dynamicLinker.link(callSite);
        return callSite;
    }
}