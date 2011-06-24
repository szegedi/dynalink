/*
   Copyright 2009 Attila Szegedi

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
package org.dynalang.dynalink.support;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;

import org.dynalang.dynalink.CallSiteDescriptor;
import org.dynalang.dynalink.DynamicLinker;
import org.dynalang.dynalink.DynamicLinkerFactory;
import org.dynalang.dynalink.GuardedInvocation;
import org.dynalang.dynalink.LinkRequest;
import org.dynalang.dynalink.LinkerServices;
import org.dynalang.dynalink.NoSuchDynamicMethodException;
import org.dynalang.dynalink.RelinkableCallSite;

/**
 * The implementation a master dynamic linker used by
 * {@link DynamicLinkerFactory}.
 * @author Attila Szegedi
 * @version $Id: $
 */
public class DynamicLinkerImpl implements DynamicLinker {

    private static final long serialVersionUID = 1L;

    private final LinkerServices linkerServices;
    private final int nativeContextArgCount;

    /**
     * Creates a new master linker that delegates to a single guarding dynamic
     * linker (this is usually a {@link CompositeGuardingDynamicLinker} though.
     * @param linkerServices the linkerServices used by the linker
     * @param nativeContextArgCount see
     * {@link DynamicLinkerFactory#setNativeContextArgCount(int)}
     */
    public DynamicLinkerImpl(final LinkerServices linkerServices,
        final int nativeContextArgCount) {
        this.nativeContextArgCount = nativeContextArgCount;
        this.linkerServices = linkerServices;
    }

    public void link(final RelinkableCallSite callSite) {
        callSite.setRelink(createRelinkAndInvokeMethod(callSite));
    }

    private static final MethodHandle RELINK_AND_INVOKE =
        new Lookup(MethodHandles.lookup()).findSpecial(DynamicLinkerImpl.class,
                "_relinkAndInvoke", MethodType.methodType(Object.class,
                        CallSiteDescriptor.class, RelinkableCallSite.class,
                        Object[].class));

    private MethodHandle createRelinkAndInvokeMethod(final RelinkableCallSite callSite) {
        // Make a bound MH of invoke() for this linker and call site
        final CallSiteDescriptor descriptor = callSite.getCallSiteDescriptor();
        final MethodHandle boundInvoker = MethodHandles.insertArguments(
                RELINK_AND_INVOKE, 0, this, descriptor, callSite);
        // Make a MH that gathers all arguments to the invocation into an
        // Object[]
        final MethodType type = descriptor.getMethodType();
        final MethodHandle collectingInvoker = boundInvoker.asCollector(
            Object[].class, type.parameterCount());
        // Make a MH that converts all args to Object
        return collectingInvoker.asType(type);
    }

    /**
     * This method is public for implementation reasons. Do not invoke it
     * directly. Relinks a call site conforming to the invocation arguments,
     * and then invokes the newly linked method handle.
     * @param callSiteDescriptor the descriptor of the call site
     * @param callSite the call site itself
     * @param arguments arguments to the invocation
     * @return return value of the invocation
    * @throws Throwable rethrown underlying method handle invocation throwable.
     */
    public Object _relinkAndInvoke(final CallSiteDescriptor callSiteDescriptor,
            RelinkableCallSite callSite, Object... arguments) throws Throwable {
        final LinkRequest linkRequest = nativeContextArgCount == 0 ?
            new LinkRequestImpl(callSiteDescriptor, arguments) :
            new NativeContextLinkRequestImpl(callSiteDescriptor, arguments,
                nativeContextArgCount);
        // Find a suitable method handle with a guard
        GuardedInvocation guardedInvocation =
            linkerServices.getGuardedInvocation(linkRequest);

        // None found - throw an exception
        if(guardedInvocation == null) {
            throw new NoSuchDynamicMethodException();
        }

        // If our call sites have a native context, and the linker produced a
        // non-native invocation, adapt the produced invocation into native
        // invocation.
        if(nativeContextArgCount > 0) {
            final MethodType origType = callSiteDescriptor.getMethodType();
            final int paramCount = origType.parameterCount();
            final int contextStart = paramCount - nativeContextArgCount;
            if(guardedInvocation.getInvocation().type().parameterCount() ==
              contextStart) {
                final List<Class<?>> prefix =
                    origType.parameterList().subList(contextStart, paramCount);
                final MethodHandle guard = guardedInvocation.getGuard();
                guardedInvocation = new GuardedInvocation(
                    MethodHandles.dropArguments(
                        guardedInvocation.getInvocation(), contextStart,
                        prefix), guard == null ? null :
                          MethodHandles.dropArguments(guard, contextStart,
                              prefix));
            }
        }

        // Allow the call site to relink and execute its inline caching
        // strategy.
        callSite.setGuardedInvocation(guardedInvocation);

        // Invoke the method. Note we bypass the guard, as the assumption is
        // that the current arguments will pass the guard (and there actually
        // might be no guard at all).
        return guardedInvocation.getInvocation().invokeWithArguments(arguments);
    }
}
