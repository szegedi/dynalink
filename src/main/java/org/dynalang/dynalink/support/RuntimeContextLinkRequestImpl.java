/*
   Copyright 2009-2013 Attila Szegedi

   Licensed under both the Apache License, Version 2.0 (the "Apache License")
   and the BSD License (the "BSD License"), with licensee being free to
   choose either of the two at their discretion.

   You may not use this file except in compliance with either the Apache
   License or the BSD License.

   If you choose to use this file in compliance with the Apache License, the
   following notice applies to you:

       You may obtain a copy of the Apache License at

           http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing, software
       distributed under the License is distributed on an "AS IS" BASIS,
       WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
       implied. See the License for the specific language governing
       permissions and limitations under the License.

   If you choose to use this file in compliance with the BSD License, the
   following notice applies to you:

       Redistribution and use in source and binary forms, with or without
       modification, are permitted provided that the following conditions are
       met:
       * Redistributions of source code must retain the above copyright
         notice, this list of conditions and the following disclaimer.
       * Redistributions in binary form must reproduce the above copyright
         notice, this list of conditions and the following disclaimer in the
         documentation and/or other materials provided with the distribution.
       * Neither the name of the copyright holder nor the names of
         contributors may be used to endorse or promote products derived from
         this software without specific prior written permission.

       THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
       IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
       TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
       PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL COPYRIGHT HOLDER
       BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
       CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
       SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
       BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
       WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
       OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
       ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
     * @param callSiteUnstable true if the call site being linked is considered unstable
     * @param runtimeContextArgCount the number of the leading arguments on the stack that represent the language
     * runtime specific context arguments.
     * @throws IllegalArgumentException if runtimeContextArgCount is less than 1.
     */
    public RuntimeContextLinkRequestImpl(CallSiteDescriptor callSiteDescriptor, boolean callSiteUnstable,
            Object[] arguments, int runtimeContextArgCount) {
        super(callSiteDescriptor, callSiteUnstable, arguments);
        if(runtimeContextArgCount < 1) {
            throw new IllegalArgumentException("runtimeContextArgCount < 1");
        }
        this.runtimeContextArgCount = runtimeContextArgCount;
    }

    @Override
    public LinkRequest withoutRuntimeContext() {
        if(contextStrippedRequest == null) {
            contextStrippedRequest =
                    new LinkRequestImpl(CallSiteDescriptorFactory.dropParameterTypes(getCallSiteDescriptor(), 1,
                            runtimeContextArgCount + 1), isCallSiteUnstable(), getTruncatedArguments());
        }
        return contextStrippedRequest;
    }

    @Override
    public LinkRequest replaceArguments(CallSiteDescriptor callSiteDescriptor, Object[] arguments) {
        return new RuntimeContextLinkRequestImpl(callSiteDescriptor, isCallSiteUnstable(), arguments,
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