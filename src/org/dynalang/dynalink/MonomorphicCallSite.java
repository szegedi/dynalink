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
package org.dynalang.dynalink;

import java.dyn.MethodHandle;
import java.dyn.MethodHandles;
import java.dyn.MethodType;

/**
 * A relinkable call site that implements monomorphic inline caching strategy.
 * @author Attila Szegedi
 * @version $Id: $
 */
public class MonomorphicCallSite extends RelinkableCallSite 
{
    /**
     * Creates a new call site with monomorphic inline caching strategy.
     * @param name the name of the method at the call site
     * @param type the method type of the call site
     */
    public MonomorphicCallSite(String name, MethodType type) {
        super(name, type);
    }

    /**
     * @deprecated use {@link #MonomorphicCallSite(String, MethodType)} 
     * instead.
     */
    @Deprecated
    public MonomorphicCallSite(Class<?> callerClass, String name, MethodType type) {
        this(name, type);
    }

    @Override
    public void setGuardedInvocation(GuardedInvocation guardedInvocation) {
        if(guardedInvocation == null)
        {
            throw new IllegalArgumentException("guardedInvocation == null");
        }
        final MethodHandle guard = guardedInvocation.getGuard();
        final MethodHandle invocation = guardedInvocation.getInvocation();
        try {
            setTarget(guard == null ? invocation : MethodHandles.guardWithTest(
                    guard, invocation, getRelink()));
        }
        catch(IllegalArgumentException e) {
            // Provide more information than the default JDK implementation
            throw new IllegalArgumentException("invocation and guard types " +
                    "do not match. invocation=" + invocation.type() + 
                    " guard=" + guard.type(), e);
        }
    }
}