/*
   Copyright 2011 Attila Szegedi

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

/**
 * Represents a request to link a particular invocation at a particular call
 * site. Instances of these requests are being passed to
 * {@link GuardingDynamicLinker}.
 *
 * @author Attila Szegedi
 * @version $Id: $
 */
public interface LinkRequest {
    /**
     * Returns the call site descriptor for the call site being linked.
     *
     * @return the call site descriptor for the call site being linked.
     */
    public CallSiteDescriptor getCallSiteDescriptor();

    /**
     * Returns the arguments for the invocation being linked.
     *
     * @return the arguments for the invocation being linked.
     */
    public Object[] getArguments();

    /**
     * Returns a request stripped from runtime context arguments. Some language
     * runtimes will include runtime-specific context parameters in their call
     * sites as the last few arguments. If a linker does not recognize such
     * contexts at all, or does not recognize the call site as one with its own
     * context, it can ask for the alternative link request with context
     * parameters and arguments removed, and link against it instead.
     *
     * @return the context-stripped request. If the link request does not have
     * any language runtime specific context parameters, the same link request
     * is returned.
     */
    public LinkRequest withoutRuntimeContext();

    public LinkRequest replaceArguments(CallSiteDescriptor callSiteDescriptor,
            Object[] arguments);
}