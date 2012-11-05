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
package org.dynalang.dynalink.support;

import org.dynalang.dynalink.CallSiteDescriptor;
import org.dynalang.dynalink.linker.LinkRequest;

/**
 * A link request implementation for call sites that pass language runtime specific context arguments on the stack. The
 * context specific arguments should be the first "n" arguments.
 *
 * @author Attila Szegedi
 */
public class RuntimeContextLinkRequestImpl extends LinkRequestImpl {

    private final int runtimeContextArgCount;
    private LinkRequestImpl contextStrippedRequest;

    /**
     * Creates a new link request.
     *
     * @param callSiteDescriptor the descriptor for the call site being linked
     * @param arguments the arguments for the invocation
     * @param callSiteMegamorphic true if the call site being linked is considered megamorphic
     * @param runtimeContextArgCount the number of the leading arguments on the stack that represent the language
     * runtime specific context arguments.
     * @throws IllegalArgumentException if runtimeContextArgCount is less than 1.
     */
    public RuntimeContextLinkRequestImpl(CallSiteDescriptor callSiteDescriptor, boolean callSiteMegamorphic,
            Object[] arguments, int runtimeContextArgCount) {
        super(callSiteDescriptor, callSiteMegamorphic, arguments);
        if(runtimeContextArgCount < 1) {
            throw new IllegalArgumentException("runtimeContextArgCount < 1");
        }
        this.runtimeContextArgCount = runtimeContextArgCount;
    }

    @Override
    public LinkRequest withoutRuntimeContext() {
        if(contextStrippedRequest == null) {
            contextStrippedRequest =
                    new LinkRequestImpl(getCallSiteDescriptor().dropParameterTypes(1, runtimeContextArgCount + 1),
                            isCallSiteMegamorphic(), getTruncatedArguments());
        }
        return contextStrippedRequest;
    }

    @Override
    public LinkRequest replaceArguments(CallSiteDescriptor callSiteDescriptor, Object[] arguments) {
        return new RuntimeContextLinkRequestImpl(callSiteDescriptor, isCallSiteMegamorphic(), arguments,
                runtimeContextArgCount);
    }

    private Object[] getTruncatedArguments() {
        final Object[] args = getArguments();
        final Object[] newargs = new Object[args.length - runtimeContextArgCount];
        newargs[0] = args[0]; // "this" remains at the 0th position
        System.arraycopy(args, runtimeContextArgCount + 1, newargs, 1, newargs.length - 1);
        return newargs;
    }
}