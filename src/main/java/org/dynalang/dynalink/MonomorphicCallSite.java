/*
   Copyright 2009-2011 Attila Szegedi

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

import org.dynalang.dynalink.linker.CallSiteDescriptor;
import org.dynalang.dynalink.support.AbstractRelinkableCallSite;

/**
 * A relinkable call site that implements monomorphic inline caching strategy.
 * After it linked a method, it will keep it until either its guard evaluates to
 * false, or its switchpoint is invalidated, at which time it will throw away
 * the previous linkage, and trigger relinking with its associated
 * {@link DynamicLinker}.
 *
 * @author Attila Szegedi
 * @version $Id: $
 */
public class MonomorphicCallSite extends AbstractRelinkableCallSite {
    /**
     * Creates a new call site with monomorphic inline caching strategy.
     * @param descriptor the descriptor for this call site
     */
    public MonomorphicCallSite(CallSiteDescriptor descriptor) {
        super(descriptor);
    }

    @Override
    protected MethodHandle getGuardFallback() {
        // Relink the call site when a guard fails.
        return getRelink();
    }

    @Override
    protected MethodHandle getSwitchPointFallback() {
        // Relink the call site when a switch point invalidates the linked
        // method.
        return getRelink();
    }
}