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

import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;

import org.dynalang.dynalink.CallSiteDescriptor;

/**
 * A call site descriptor that stores a specific {@link Lookup}. It does not, however, store static bootstrap arguments.
 * @author Attila Szegedi
 */
class LookupCallSiteDescriptor extends DefaultCallSiteDescriptor {
    private Lookup lookup;

    /**
     * Create a new call site descriptor from explicit information.
     * @param tokenizedName the name of the method
     * @param methodType the method type
     * @param lookup the lookup
     */
    LookupCallSiteDescriptor(String[] tokenizedName, MethodType methodType, Lookup lookup) {
        super(tokenizedName, methodType);
        this.lookup = lookup;
    }

    @Override
    public Lookup getLookup() {
        return lookup;
    }

    @Override
    public CallSiteDescriptor changeMethodType(MethodType newMethodType) {
        return new LookupCallSiteDescriptor(getTokenizedName(), newMethodType, lookup);
    }
}
