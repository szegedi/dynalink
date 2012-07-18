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
 * Default implementation of the {@link LinkRequest}, representing a link request to a call site that passes no language
 * runtime specific native context arguments on the stack.
 *
 * @author Attila Szegedi
 */
public class LinkRequestImpl implements LinkRequest {

    private final CallSiteDescriptor callSiteDescriptor;
    private final Object[] arguments;

    /**
     * Creates a new link request.
     *
     * @param callSiteDescriptor the descriptor for the call site being linked
     * @param arguments the arguments for the invocation
     */
    public LinkRequestImpl(CallSiteDescriptor callSiteDescriptor, Object... arguments) {
        this.callSiteDescriptor = callSiteDescriptor;
        this.arguments = arguments;
    }

    @Override
    public Object[] getArguments() {
        return arguments != null ? arguments.clone() : null;
    }

    @Override
    public Object getReceiver() {
        return arguments != null && arguments.length > 0 ? arguments[0] : null;
    }

    @Override
    public CallSiteDescriptor getCallSiteDescriptor() {
        return callSiteDescriptor;
    }

    @Override
    public LinkRequest withoutRuntimeContext() {
        return this;
    }

    @Override
    public LinkRequest replaceArguments(CallSiteDescriptor newCallSiteDescriptor, Object[] newArguments) {
        return new LinkRequestImpl(newCallSiteDescriptor, newArguments);
    }
}