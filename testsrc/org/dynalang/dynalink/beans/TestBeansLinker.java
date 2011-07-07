/*
   Copyright 2009-2011 Attila Szegedi

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

package org.dynalang.dynalink.beans;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.BootstrapMethodError;

import junit.framework.AssertionFailedError;
import junit.framework.TestCase;

import org.dynalang.dynalink.CallSiteDescriptor;
import org.dynalang.dynalink.GuardedInvocation;
import org.dynalang.dynalink.GuardingDynamicLinker;
import org.dynalang.dynalink.LinkRequest;
import org.dynalang.dynalink.LinkerServices;
import org.dynalang.dynalink.support.LinkRequestImpl;

/**
 * Tests {@link BeansLinker} corner cases not exercised by other tests.
 *
 * @author Attila Szegedi
 * @version $Id: $
 */
public class TestBeansLinker extends TestCase {
    /**
     * Tests all conditions under which a BeansLinker would not link based
     * solely on its own arguments.
     *
     * @throws Exception if an exception occurs
     */
    public void testAllNulls() throws Exception {
        final BeansLinker linker = new BeansLinker();
        final LinkerServices ls = new LinkerServices() {
            public boolean canConvert(Class<?> from, Class<?> to) {
                throw new AssertionFailedError();
            }

            public MethodHandle convertArguments(MethodHandle handle,
                    MethodType fromType) {
                throw new AssertionFailedError();
            }

            public GuardedInvocation getGuardedInvocation(LinkRequest lreq)
                    throws Exception {
                throw new AssertionFailedError();
            }

        };

        // Can't link with null arguments
        assertNull(getGuardedInvocation(linker, createCallSiteDescriptor(
                "dyn:foo", MethodType.methodType(Void.TYPE)), ls,
                (Object[])null));

        // Can't link with zero arguments
        assertNull(getGuardedInvocation(linker, createCallSiteDescriptor(
                "dyn:foo", MethodType.methodType(Void.TYPE)), ls, new Object[0]));

        // Can't link with single null argument
        assertNull(getGuardedInvocation(linker, createCallSiteDescriptor(
                "dyn:foo", MethodType.methodType(Void.TYPE, Object.class)), ls,
                new Object[] { null }));

        // Can't link with name that has less than two components
        assertNull(getGuardedInvocation(linker, createCallSiteDescriptor("",
                MethodType.methodType(Void.TYPE, Object.class)), ls,
                new Object()));
        assertNull(getGuardedInvocation(linker, createCallSiteDescriptor("foo",
                MethodType.methodType(Void.TYPE, Object.class)), ls,
                new Object()));

        // Can't link with name that doesn't start with dyn:
        assertNull(getGuardedInvocation(linker, createCallSiteDescriptor(
                "dynx:foo", MethodType.methodType(Void.TYPE, Object.class)),
                ls, new Object()));
    }

    private static GuardedInvocation getGuardedInvocation(
            GuardingDynamicLinker linker, CallSiteDescriptor descriptor,
            LinkerServices linkerServices, Object... args) throws Exception {
        return linker.getGuardedInvocation(
                new LinkRequestImpl(descriptor, args), linkerServices);
    }

    public void testInvalidName() throws Exception {
        try {
            getGuardedInvocation(new BeansLinker(), createCallSiteDescriptor(
                    "dyn", MethodType.methodType(int.class)), null,
                    new Object[1]);
            fail();
        } catch(BootstrapMethodError e) {
            // ignored - it is supposed to fail
        }
    }

    public static  CallSiteDescriptor createCallSiteDescriptor(String name,
            MethodType methodType) {
        return new CallSiteDescriptor(MethodHandles.publicLookup(), name, methodType);
    }

}
