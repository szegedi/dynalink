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

import java.dyn.MutableCallSite;
import java.dyn.MethodHandle;
import java.dyn.MethodType;

/**
 * Base class for relinkable call sites. Language runtimes wishing to use this
 * framework must use subclasses of this class as their call sites. There is a
 * readily usable {@link MonomorphicCallSite} subclass that implements 
 * monomorphic inline caching strategy.
 * @author Attila Szegedi
 * @version $Id: $
 */
public abstract class RelinkableCallSite extends MutableCallSite
{
    private MethodHandle relink;
    private final CallSiteDescriptor callSiteDescriptor;
    
    /**
     * Creates a new relinkable call site
     * @param name the name of the method at the call site
     * @param type the method type of the call site
     */
    protected RelinkableCallSite(String name, MethodType type) {
        super(type);
        callSiteDescriptor = new CallSiteDescriptor(name, type);
    }
    
    /**
     * Sets the relink method. This is a method matching the method type of the
     * call site that will try to discover the adequate target for the 
     * invocation and then subsequently invoke 
     * {@link #setGuardedInvocation(GuardedInvocation)}. This method is 
     * normally only called by the {@link DynamicLinker} implementation.
     * @param relink the relink method handle.
     * @throws IllegalArgumentException if the relink is null
     * @throws IllegalStateException if the method was already called
     */
    public void setRelink(MethodHandle relink) {
        if(relink == null) {
            throw new IllegalArgumentException("relink == null");
        }
        if(this.relink != null) {
            throw new IllegalStateException("this.relink already set");
        }
        this.relink = relink;
        // Set it as the initial target
        setTarget(relink);
    }

    /**
     * Returns the relink method handle
     * @return the method handle for relinking this call site.
     */
    protected MethodHandle getRelink() {
        return relink;
    }

    /**
     * Returns the descriptor for this call site.
     * @return the descriptor for this call site.
     */
    public CallSiteDescriptor getCallSiteDescriptor() {
        return callSiteDescriptor;
    }
    
    /**
     * Should be implemented by subclasses. Will be called once every time the
     * call site is relinked. 
     * @param guardedInvocation the guarded invocation that the call site 
     * should set as its target.
     */
    public abstract void setGuardedInvocation(GuardedInvocation guardedInvocation);
}