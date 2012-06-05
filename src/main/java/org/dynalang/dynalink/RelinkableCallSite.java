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

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MutableCallSite;
import java.lang.invoke.VolatileCallSite;

import org.dynalang.dynalink.linker.CallSiteDescriptor;
import org.dynalang.dynalink.linker.GuardedInvocation;

/**
 * Interface for relinkable call sites. Language runtimes wishing to use this framework must use subclasses of
 * {@link CallSite} that also implement this interface as their call sites. There is a readily usable
 * {@link MonomorphicCallSite} subclass that implements monomorphic inline caching strategy as well as a
 * {@link ChainedCallSite} that retains a chain of already linked method handles. The reason this is defined as an
 * interface instead of a concrete, albeit abstract class is that it allows independent implementations to choose
 * between {@link MutableCallSite} and {@link VolatileCallSite} as they see fit.
 *
 * @author Attila Szegedi
 * @version $Id: $
 */
public interface RelinkableCallSite {
    /**
     * Sets the initial target to a relink-and-invoke method.
     * @param relinkAndInvoke a relink-and-invoke method handle supplied by the {@link DynamicLinker}.
     */
    public void setRelinkAndInvoke(MethodHandle relinkAndInvoke);

    /**
     * Returns the descriptor for this call site.
     *
     * @return the descriptor for this call site.
     */
    public CallSiteDescriptor getDescriptor();

    /**
     * This method will be called once by the dynamic linker every time the call site is relinked.
     *
     * @param guardedInvocation the guarded invocation that the call site should set as its current target. Note that
     * the call sites are allowed to keep other non-invalidated invocations around for implementation of polymorphic
     * inline caches.
     * @param fallback the fallback method. This is a method matching the method type of the call site that is supplied
     * by the {@link DynamicLinker} to be used by this call site as a fallback when it can't invoke its target with the
     * passed arguments. The fallback method is such that when it's invoked, it'll try to discover the adequate target
     * for the invocation, subsequently invoke {@link #setGuardedInvocation(GuardedInvocation, MethodHandle)}, and
     * finally invoke the target.
     */
    public void setGuardedInvocation(GuardedInvocation guardedInvocation, MethodHandle fallback);
}