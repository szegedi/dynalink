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
 * Default implementation of the {@link LinkRequest}, representing a link request to a call site that passes no language
 * runtime specific native context arguments on the stack.
 *
 * @author Attila Szegedi
 */
public class LinkRequestImpl implements LinkRequest {

    private final CallSiteDescriptor callSiteDescriptor;
    private final Object callSiteToken;
    private final Object[] arguments;
    private final boolean callSiteUnstable;
    private final int linkCount;
    
    /**
     * Creates a new link request.
     *
     * @param callSiteDescriptor the descriptor for the call site being linked
     * @param callSiteToken the opaque token for the call site being linked.
     * @param linkCount how many times this callsite has been linked/relinked
     * @param callSiteUnstable true if the call site being linked is considered unstable
     * @param arguments the arguments for the invocation
     */
    public LinkRequestImpl(final CallSiteDescriptor callSiteDescriptor, final Object callSiteToken, final int linkCount, final boolean callSiteUnstable, final Object... arguments) {
        this.callSiteDescriptor = callSiteDescriptor;
        this.callSiteToken = callSiteToken;
        this.linkCount = linkCount;
        this.callSiteUnstable = callSiteUnstable;
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
    public Object getCallSiteToken() {
        return callSiteToken;
    }

    @Override
    public boolean isCallSiteUnstable() {
        return callSiteUnstable;
    }

    @Override
    public int getLinkCount() {
    	return linkCount;
    }
    
    @Override
    public LinkRequest withoutRuntimeContext() {
        return this;
    }

    @Override
    public LinkRequest replaceArguments(final CallSiteDescriptor newCallSiteDescriptor, final Object[] newArguments) {
        return new LinkRequestImpl(newCallSiteDescriptor, callSiteToken, linkCount, callSiteUnstable, newArguments);
    }
}
