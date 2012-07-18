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
            createCallSite(MethodType.methodType(Void.TYPE)).setGuardedInvocation(null, null);
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
        mcs.setGuardedInvocation(new GuardedInvocation(mh, null), null);
        assertSame(mh, mcs.getTarget());
    }

    private static MonomorphicCallSite createCallSite(MethodType type) {
        return new MonomorphicCallSite(CallSiteDescriptorFactory.create(MethodHandles.publicLookup(), "", type));
    }
}