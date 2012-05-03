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

import org.dynalang.dynalink.linker.CallSiteDescriptor;
import org.dynalang.dynalink.linker.GuardedInvocation;
import org.dynalang.dynalink.linker.GuardingDynamicLinker;
import org.dynalang.dynalink.linker.LinkRequest;
import org.dynalang.dynalink.linker.LinkerServices;
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
 *         final MonomorphicCallSite callSite = new MonomorphicCallSite(lookup, name, type);
 *         dynamicLinker.link(callSite);
 *         return callSite;
 *     }
 * }
 * </pre>
 *
 * Note how you're expected to implement a {@link GuardingDynamicLinker} for your own language. If your runtime doesn't
 * have its own language and/or object model (i.e. it's a generic scripting shell), you don't need to implement a
 * dynamic linker; you would simply not invoke the <tt>setPrioritizedLinker</tt> on the factory, or even better, you
 * would simply use {@link DefaultBootstrapper}.
 *
 * @author Attila Szegedi
 * @version $Id: $
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
     * Links an invokedynamic call site. It will install a relink method handle into the call site that hooks into the
     * multi-language dispatch and relinking mechanisms.
     *
     * @param callSite the call site to link.
     */
    public void link(final RelinkableCallSite callSite) {
        callSite.setTarget(createRelinkAndInvokeMethod(callSite));
    }

    private static final MethodHandle RELINK_AND_INVOKE = new Lookup(MethodHandles.lookup()).findSpecial(
            DynamicLinker.class, "_relinkAndInvoke",
            MethodType.methodType(Object.class, RelinkableCallSite.class, Object[].class));

    private MethodHandle createRelinkAndInvokeMethod(final RelinkableCallSite callSite) {
        // Make a bound MH of invoke() for this linker and call site
        final MethodHandle boundInvoker = MethodHandles.insertArguments(RELINK_AND_INVOKE, 0, this, callSite);
        // Make a MH that gathers all arguments to the invocation into an Object[]
        final MethodType type = callSite.getDescriptor().getMethodType();
        final MethodHandle collectingInvoker = boundInvoker.asCollector(Object[].class, type.parameterCount());
        // Make a MH that converts all args to Object
        return collectingInvoker.asType(type);
    }

    /**
     * Relinks a call site conforming to the invocation arguments, and then invokes the newly linked method handle.
     *
     * @param callSite the call site itself
     * @param arguments arguments to the invocation
     * @return return value of the invocation
     * @throws Throwable rethrown underlying method handle invocation throwable.
     */
    @SuppressWarnings("unused")
    private Object _relinkAndInvoke(RelinkableCallSite callSite, Object... arguments) throws Throwable {
        final CallSiteDescriptor callSiteDescriptor = callSite.getDescriptor();
        final LinkRequest linkRequest =
                runtimeContextArgCount == 0 ? new LinkRequestImpl(callSiteDescriptor, arguments)
                        : new RuntimeContextLinkRequestImpl(callSiteDescriptor, arguments, runtimeContextArgCount);

        // Find a suitable method handle with a guard
        GuardedInvocation guardedInvocation = linkerServices.getGuardedInvocation(linkRequest);

        // None found - throw an exception
        if(guardedInvocation == null) {
            throw new NoSuchDynamicMethodException();
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
        // Invoke the method. Note we bypass the guard, as the assumption is that the current arguments will pass the
        // guard (and there actually might be no guard at all).
        return guardedInvocation.getInvocation().invokeWithArguments(arguments);
    }
}
