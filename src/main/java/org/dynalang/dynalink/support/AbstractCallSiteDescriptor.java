/*
   Copyright 2009-2013 Attila Szegedi

   Licensed under either the Apache License, Version 2.0 (the "Apache
   License") or the 3-clause BSD License (the "BSD License"), with licensee
   being free to choose either of the two at their discretion.

   You may not use this file except in compliance with either the Apache
   License or the BSD License.

   A copy of the BSD License is available in the root directory of the
   source distribution of the project under the file name
   "LICENSE-BSD.txt".

   A copy of the Apache License is available in the root directory of the
   source distribution of the project under the file name
   "LICENSE-Apache-2.0.txt". Alternatively, you may obtain a copy of the
   Apache License at <http://www.apache.org/licenses/LICENSE-2.0>

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See your chosen License for the specific language governing permissions
   and limitations under that License.
*/

package org.dynalang.dynalink.support;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.util.Objects;

import org.dynalang.dynalink.CallSiteDescriptor;

/**
 * A base class for call site descriptor implementations. Provides reconstruction of the name from the tokens, as well
 * as a generally useful {@code equals} and {@code hashCode} methods.
 * @author Attila Szegedi
 */
public abstract class AbstractCallSiteDescriptor implements CallSiteDescriptor {

    @Override
    public String getName() {
        return appendName(new StringBuilder(getNameLength())).toString();
    }

   @Override
   public Lookup getLookup() {
       return MethodHandles.publicLookup();
   }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof CallSiteDescriptor && equals((CallSiteDescriptor)obj);
    }

    /**
     * Returns true if this call site descriptor is equal to the passed call site descriptor.
     * @param csd the other call site descriptor.
     * @return true if they are equal.
     */
    public boolean equals(CallSiteDescriptor csd) {
        if(csd == null) {
            return false;
        }
        if(csd == this) {
            return true;
        }
        final int ntc = getNameTokenCount();
        if(ntc != csd.getNameTokenCount()) {
            return false;
        }
        for(int i = ntc; i-- > 0;) { // Reverse order as variability is higher at the end
            if(!Objects.equals(getNameToken(i), csd.getNameToken(i))) {
                return false;
            }
        }
        if(!getMethodType().equals(csd.getMethodType())) {
            return false;
        }
        return lookupsEqual(getLookup(), csd.getLookup());
    }

    @Override
    public int hashCode() {
        final int c = getNameTokenCount();
        int h = 0;
        for(int i = 0; i < c; ++i) {
            h = h * 31 + getNameToken(i).hashCode();
        }
        return h * 31 + getMethodType().hashCode();
    }

    @Override
    public String toString() {
        final String mt = getMethodType().toString();
        final String l = getLookup().toString();
        final StringBuilder b = new StringBuilder(l.length() + 1 + mt.length() + getNameLength());
        return appendName(b).append(mt).append("@").append(l).toString();
    }

    private int getNameLength() {
        final int c = getNameTokenCount();
        int l = 0;
        for(int i = 0; i < c; ++i) {
            l += getNameToken(i).length();
        }
        return l +  c - 1;
    }

    private StringBuilder appendName(StringBuilder b) {
        b.append(getNameToken(0));
        final int c = getNameTokenCount();
        for(int i = 1; i < c; ++i) {
            b.append(':').append(getNameToken(i));
        }
        return b;
    }

    private static boolean lookupsEqual(Lookup l1, Lookup l2) {
        if(l1 == l2) {
            return true;
        }
        if(l1.lookupClass() != l2.lookupClass()) {
            return false;
        }
        return l1.lookupModes() == l2.lookupModes();
    }
}
