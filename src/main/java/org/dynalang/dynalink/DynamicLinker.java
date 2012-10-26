/*
   Copyright 2009-2012 Attila Szegedi

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package org.dynalang.dynalink;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.util.List;

import org.dynalang.dynalink.linker.GuardedInvocation;
import org.dynalang.dynalink.linker.GuardingDynamicLinker;
import org.dynalang.dynalink.linker.LinkRequest;
import org.dynalang.dynalink.linker.LinkerServices;
import org.dynalang.dynalink.support.CallSiteDescriptorFactory;
import org.dynalang.dynalink.support.LinkRequestImpl;
import org.dynalang.dynalink.support.Lookup;
import org.dynalang.dynalink.support.RuntimeContextLinkRequestImpl;

/**
 * The linker for {@link RelinkableCallSite} objects. Users of it (scripting frameworks and language runtimes) have to
 * create a linker using the {@link DynamicLinkerFactory} and invoke its link method from the invokedynamic bootstrap
 * methods to set the target of all the call sites in the code they generate. Usual usage would be to create one class
 * per language runtime to contain one linker instance as:
 *
 * <pre>
 * class MyLanguageRuntime {
 *     private static final GuardingDynamicLinker myLanguageLinker = new MyLanguageLinker();
 *     private static final DynamicLinker dynamicLinker = createDynamicLinker();
 *
 *     private static DynamicLinker createDynamicLinker() {
 *         final DynamicLinkerFactory factory = new DynamicLinkerFactory();
 *         factory.setPrioritizedLinker(myLanguageLinker);
 *         return factory.createLinker();
 *     }
 *
 *     public static CallSite bootstrap(MethodHandles.Lookup caller, String name, MethodType type) {
 *         return dynamicLinker.link(new MonomorphicCallSite(CallSiteDescriptorFactory.create(lookup, name, type)));
 *     }
 * }
 * </pre>
 *
 * Note how there are three components you will need to provide here:
 * <ul>
 * <li>You're expected to provide a {@link GuardingDynamicLinker} for your own language. If your runtime doesn't
 * have its own language and/or object model (i.e. it's a generic scripting shell), you don't need to implement a
 * dynamic linker; you would simply not invoke the {@code setPrioritizedLinker} method on the factory, or even better,
 * simply use {@link DefaultBootstrapper}.</li>
 * <li>The performance of the programs can depend on your choice of the class to represent call sites. The above
 * example used {@link MonomorphicCallSite}, but you might want to use {@link ChainedCallSite} instead. You'll need to
 * experiment and decide what fits your language runtime the best. You can subclass either of these or roll your own if
 * you need to.</li>
 * <li>You also need to provide {@link CallSiteDescriptor}s to your call sites. They are immutable objects that contain
 * all the information about the call site: the class performing the lookups, the name of the method being invoked, and
 * the method signature. The library has a default {@link CallSiteDescriptorFactory} for descriptors that you can use,
 * or you can create your own descriptor classes, especially if you need to add further information (values passed in
 * additional parameters to the bootstrap method) to them.</li>
 * </ul>
 *
 * @author Attila Szegedi
 */
public class DynamicLinker {
    private final LinkerServices linkerServices;
    private final int runtimeContextArgCount;
    private final boolean syncOnRelink;

    /**
     * Creates a new dynamic linker.
     *
     * @param linkerServices the linkerServices used by the linker, created by the factory.
     * @param runtimeContextArgCount see {@link DynamicLinkerFactory#setRuntimeContextArgCount(int)}
     */
    DynamicLinker(LinkerServices linkerServices, int runtimeContextArgCount, boolean syncOnRelink) {
        if(runtimeContextArgCount < 0) {
            throw new IllegalArgumentException("runtimeContextArgCount < 0");
        }
        this.runtimeContextArgCount = runtimeContextArgCount;
        this.linkerServices = linkerServices;
        this.syncOnRelink = syncOnRelink;
    }

    /**
     * Links an invokedynamic call site. It will install a method handle into the call site that invokes the relinking
     * mechanism of this linker. Next time the call site is invoked, it will be linked for the actual arguments it was
     * invoked with.
     *
     * @param callSite the call site to link.
     * @return the callSite, for easy call chaining.
     */
    public <T extends RelinkableCallSite> T link(final T callSite) {
        callSite.setRelinkAndInvoke(createRelinkAndInvokeMethod(callSite));
        return callSite;
    }

    /**
     * Returns the object representing the lower level linker services of this class that are normally exposed to
     * individual language-specific linkers. While as a user of this class you normally only care about the
     * {@link #link(RelinkableCallSite)} method, in certain circumstances you might want to use the lower level services
     * directly; either to lookup specific method handles, to access the type converters, and so on.
     * @return the object representing the linker services of this class.
     */
    public LinkerServices getLinkerServices() {
        return linkerServices;
    }

    private static final MethodHandle RELINK = Lookup.findOwnSpecial(MethodHandles.lookup(), "relink",
            MethodHandle.class, RelinkableCallSite.class, Object[].class);

    private MethodHandle createRelinkAndInvokeMethod(final RelinkableCallSite callSite) {
        // Make a bound MH of invoke() for this linker and call site
        final MethodHandle boundRelinker = MethodHandles.insertArguments(RELINK, 0, this, callSite);
        // Make a MH that gathers all arguments to the invocation into an Object[]
        final MethodType type = callSite.getDescriptor().getMethodType();
        final MethodHandle collectingRelinker = boundRelinker.asCollector(Object[].class, type.parameterCount());
        return MethodHandles.foldArguments(MethodHandles.exactInvoker(type), collectingRelinker.asType(
                type.changeReturnType(MethodHandle.class)));
    }

    /**
     * Relinks a call site conforming to the invocation arguments.
     *
     * @param callSite the call site itself
     * @param arguments arguments to the invocation
     * @return return the method handle for the invocation
     * @throws Exception rethrows any exception thrown by the linkers
     */
    @SuppressWarnings("unused")
    private MethodHandle relink(RelinkableCallSite callSite, Object... arguments) throws Exception {
        final CallSiteDescriptor callSiteDescriptor = callSite.getDescriptor();
        final LinkRequest linkRequest =
                runtimeContextArgCount == 0 ? new LinkRequestImpl(callSiteDescriptor, arguments)
                        : new RuntimeContextLinkRequestImpl(callSiteDescriptor, arguments, runtimeContextArgCount);

        // Find a suitable method handle with a guard
        GuardedInvocation guardedInvocation = linkerServices.getGuardedInvocation(linkRequest);

        // None found - throw an exception
        if(guardedInvocation == null) {
            throw new NoSuchDynamicMethodException(callSiteDescriptor.toString());
        }

        // If our call sites have a runtime context, and the linker produced a context-stripped invocation, adapt the
        // produced invocation into contextual invocation (by dropping the context...)
        if(runtimeContextArgCount > 0) {
            final MethodType origType = callSiteDescriptor.getMethodType();
            final MethodHandle invocation = guardedInvocation.getInvocation();
            if(invocation.type().parameterCount() == origType.parameterCount() - runtimeContextArgCount) {
                final List<Class<?>> prefix = origType.parameterList().subList(1, runtimeContextArgCount + 1);
                final MethodHandle guard = guardedInvocation.getGuard();
                guardedInvocation = guardedInvocation.dropArguments(1, prefix);
            }
        }

        // Allow the call site to relink and execute its inline caching strategy.
        callSite.setGuardedInvocation(guardedInvocation, createRelinkAndInvokeMethod(callSite));
        if(syncOnRelink) {
            MutableCallSite.syncAll(new MutableCallSite[] { (MutableCallSite)callSite });
        }
        return guardedInvocation.getInvocation();
    }
}
