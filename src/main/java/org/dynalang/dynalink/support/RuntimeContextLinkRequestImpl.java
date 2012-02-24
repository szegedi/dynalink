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

import org.dynalang.dynalink.linker.CallSiteDescriptor;
import org.dynalang.dynalink.linker.LinkRequest;

/**
 * A link request implementation for call sites that pass language runtime specific context arguments on the stack.
 *
 * @author Attila Szegedi
 * @version $Id: $
 */
public class RuntimeContextLinkRequestImpl extends LinkRequestImpl {

    private final int runtimeContextArgCount;
    private LinkRequestImpl contextStrippedRequest;

    /**
     * Creates a new link request.
     *
     * @param callSiteDescriptor the descriptor for the call site being linked
     * @param arguments the arguments for the invocation
     * @param runtimeContextArgCount the number of the trailing arguments on the stack that represent the language
     * runtime specific context arguments.
     */
    public RuntimeContextLinkRequestImpl(CallSiteDescriptor callSiteDescriptor, Object[] arguments,
            int runtimeContextArgCount) {
        super(callSiteDescriptor, arguments);
        this.runtimeContextArgCount = runtimeContextArgCount;
    }

    @Override
    public LinkRequest withoutRuntimeContext() {
        if(contextStrippedRequest == null) {
            final CallSiteDescriptor callSiteDescriptor = getCallSiteDescriptor();
            final int argCount = callSiteDescriptor.getMethodType().parameterCount();
            contextStrippedRequest =
                    new LinkRequestImpl(callSiteDescriptor.dropParameterTypes(argCount - runtimeContextArgCount,
                            argCount), getTruncatedArguments());
        }
        return contextStrippedRequest;
    }

    @Override
    public LinkRequest replaceArguments(CallSiteDescriptor callSiteDescriptor, Object[] arguments) {
        return new RuntimeContextLinkRequestImpl(callSiteDescriptor, arguments, runtimeContextArgCount);
    }

    private Object[] getTruncatedArguments() {
        final Object[] args = getArguments();
        final Object[] newargs = new Object[args.length - runtimeContextArgCount];
        System.arraycopy(args, 0, newargs, 0, newargs.length);
        return newargs;
    }
}