/*
   Copyright 2009-2013 Attila Szegedi

   Licensed under either the Apache License, Version 2.0 (the "Apache
   License") or the BSD License (the "BSD License"), with licensee
   being free to choose either of the two at their discretion.

   You may not use this file except in compliance with either the Apache
   License or the BSD License.

   A copy of the BSD License is available in the root directory of the
   source distribution of the project under the file name
   "Dynalink-License-BSD.txt".

   A copy of the Apache License is available in the root directory of the
   source distribution of the project under the file name
   "Dynalink-License-Apache-2.0.txt". Alternatively, you may obtain a
   copy of the Apache License at <http://www.apache.org/licenses/LICENSE-2.0>

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See your chosen License for the specific language governing permissions
   and limitations under that License.
*/

package org.dynalang.dynalink;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MutableCallSite;
import java.lang.invoke.VolatileCallSite;

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
 */
public interface RelinkableCallSite {
    /**
     * Initializes the relinkable call site by setting a relink-and-invoke method handle. The call site implementation
     * is supposed to set this method handle as its target.
     * @param relinkAndInvoke a relink-and-invoke method handle supplied by the {@link DynamicLinker}.
     */
    public void initialize(MethodHandle relinkAndInvoke);

    /**
     * Returns the descriptor for this call site.
     *
     * @return the descriptor for this call site.
     */
    public CallSiteDescriptor getDescriptor();

    /**
     * This method will be called by the dynamic linker every time the call site is normally relinked. It will be passed
     * a {@code GuardedInvocation} that the call site should incorporate into its target method handle. When this method
     * is called, the call site is allowed to keep other non-invalidated invocations around for implementation of
     * polymorphic inline caches and compose them with this invocation to form its final target.
     *
     * @param guardedInvocation the guarded invocation that the call site should incorporate into its target method
     * handle.
     * @param fallback the fallback method. This is a method matching the method type of the call site that is supplied
     * by the {@link DynamicLinker} to be used by this call site as a fallback when it can't invoke its target with the
     * passed arguments. The fallback method is such that when it's invoked, it'll try to discover the adequate target
     * for the invocation, subsequently invoke {@link #relink(GuardedInvocation, MethodHandle)} or
     * {@link #resetAndRelink(GuardedInvocation, MethodHandle)}, and finally invoke the target.
     */
    public void relink(GuardedInvocation guardedInvocation, MethodHandle fallback);

    /**
     * This method will be called by the dynamic linker every time the call site is relinked and the linker wishes the
     * call site to throw away any prior linkage state. It will be passed a {@code GuardedInvocation} that the call site
     * should use to build its target method handle. When this method is called, the call site is discouraged from
     * keeping previous state around, and is supposed to only link the current invocation.
     *
     * @param guardedInvocation the guarded invocation that the call site should use to build its target method handle.
     * @param fallback the fallback method. This is a method matching the method type of the call site that is supplied
     * by the {@link DynamicLinker} to be used by this call site as a fallback when it can't invoke its target with the
     * passed arguments. The fallback method is such that when it's invoked, it'll try to discover the adequate target
     * for the invocation, subsequently invoke {@link #relink(GuardedInvocation, MethodHandle)} or
     * {@link #resetAndRelink(GuardedInvocation, MethodHandle)}, and finally invoke the target.
     */
    public void resetAndRelink(GuardedInvocation guardedInvocation, MethodHandle fallback);
}