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

package org.dynalang.dynalink;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.dynalang.dynalink.linker.GuardedInvocation;
import org.dynalang.dynalink.support.CallSiteDescriptorFactory;

import junit.framework.TestCase;

/**
 * Tests for the {@link MonomorphicCallSite}.
 *
 * @author Attila Szegedi
 */
public class TestMonomorphicCallSite extends TestCase {
    static {
        MethodHandles.lookup();
    }

    /**
     * Tests prohibition of setting null target.
     */
    public static void testSetNull() {
        try {
            createCallSite(MethodType.methodType(Void.TYPE)).relink(null, null);
            fail();
        } catch(NullPointerException e) {
            // This is expected
        }
    }

    /**
     * Tests setting a guardless target (it has to be linked directly).
     */
    public static void testSetGuardless() {
        final MethodHandle mh = MethodHandles.identity(Object.class);
        final MonomorphicCallSite mcs = createCallSite(mh.type());
        mcs.relink(new GuardedInvocation(mh, null), null);
        assertSame(mh, mcs.getTarget());
    }

    private static MonomorphicCallSite createCallSite(MethodType type) {
        return new MonomorphicCallSite(CallSiteDescriptorFactory.create(MethodHandles.publicLookup(), "", type));
    }
}