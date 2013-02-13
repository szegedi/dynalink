/*
   Copyright 2009-2013 Attila Szegedi

   Licensed under either the Apache License, Version 2.0 (the "Apache
   License") or the BSD License (the "BSD License"), with licensee
   being free to choose either of the two at their discretion.

   You may not use this file except in compliance with either the Apache
   License or the BSD License.

   A copy of the BSD License is available in the root directory of the
   source distribution of the project under the file name
   "Dynalink-License-BSD.txt".

   A copy of the Apache License is available in the root directory of the
   source distribution of the project under the file name
   "Dynalink-License-Apache-2.0.txt". Alternatively, you may obtain a
   copy of the Apache License at <http://www.apache.org/licenses/LICENSE-2.0>

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See your chosen License for the specific language governing permissions
   and limitations under that License.
*/

package org.dynalang.dynalink.support;

import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;

import org.dynalang.dynalink.CallSiteDescriptor;

/**
 * A default, fairly light implementation of a call site descriptor used for describing non-standard operations. It does
 * not store {@link Lookup} objects but always returns the public lookup from its {@link #getLookup()} method. If you
 * need to support non-public lookup, you can use {@link LookupCallSiteDescriptor}.
 * @author Attila Szegedi
 */
class DefaultCallSiteDescriptor extends AbstractCallSiteDescriptor {

    private final String[] tokenizedName;
    private final MethodType methodType;

    DefaultCallSiteDescriptor(String[] tokenizedName, MethodType methodType) {
        this.tokenizedName = tokenizedName;
        this.methodType = methodType;
    }

    @Override
    public int getNameTokenCount() {
        return tokenizedName.length;
    }

    @Override
    public String getNameToken(int i) {
        try {
            return tokenizedName[i];
        } catch(ArrayIndexOutOfBoundsException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    String[] getTokenizedName() {
        return tokenizedName;
    }

    @Override
    public MethodType getMethodType() {
        return methodType;
    }

    @Override
    public CallSiteDescriptor changeMethodType(MethodType newMethodType) {
        return CallSiteDescriptorFactory.getCanonicalPublicDescriptor(new DefaultCallSiteDescriptor(tokenizedName,
                newMethodType));
    }
}
