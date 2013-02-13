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

package org.dynalang.dynalink.beans;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.dynalang.dynalink.CallSiteDescriptor;
import org.dynalang.dynalink.linker.ConversionComparator.Comparison;
import org.dynalang.dynalink.linker.GuardedInvocation;
import org.dynalang.dynalink.linker.GuardingDynamicLinker;
import org.dynalang.dynalink.linker.LinkRequest;
import org.dynalang.dynalink.linker.LinkerServices;
import org.dynalang.dynalink.support.CallSiteDescriptorFactory;
import org.dynalang.dynalink.support.LinkRequestImpl;

import junit.framework.AssertionFailedError;
import junit.framework.TestCase;

/**
 * Tests {@link BeansLinker} corner cases not exercised by other tests.
 *
 * @author Attila Szegedi
 */
public class TestBeansLinker extends TestCase {
    /**
     * Tests all conditions under which a BeansLinker would not link based solely on its own arguments.
     *
     * @throws Exception if an exception occurs
     */
    public void testAllNulls() throws Exception {
        final BeansLinker linker = new BeansLinker();
        final LinkerServices ls = new LinkerServices() {
            @Override
            public boolean canConvert(Class<?> from, Class<?> to) {
                throw new AssertionFailedError();
            }

            @Override
            public MethodHandle asType(MethodHandle handle, MethodType fromType) {
                throw new AssertionFailedError();
            }

            @Override
            public GuardedInvocation getGuardedInvocation(LinkRequest lreq) throws Exception {
                throw new AssertionFailedError();
            }

            @Override
            public Comparison compareConversion(Class<?> sourceType, Class<?> targetType1, Class<?> targetType2) {
                throw new AssertionFailedError();
            }

            @Override
            public MethodHandle getTypeConverter(Class<?> sourceType, Class<?> targetType) {
                throw new AssertionFailedError();
            }
        };

        // Can't link with null arguments
        assertNull(getGuardedInvocation(linker, createCallSiteDescriptor("dyn:foo", MethodType.methodType(Void.TYPE)),
                ls, (Object[])null));

        // Can't link with zero arguments
        assertNull(getGuardedInvocation(linker, createCallSiteDescriptor("dyn:foo", MethodType.methodType(Void.TYPE)),
                ls, new Object[0]));

        // Can't link with single null argument
        assertNull(getGuardedInvocation(linker,
                createCallSiteDescriptor("dyn:foo", MethodType.methodType(Void.TYPE, Object.class)), ls,
                new Object[] { null }));

        // Can't link with name that has less than two components
        assertNull(getGuardedInvocation(linker,
                createCallSiteDescriptor("", MethodType.methodType(Void.TYPE, Object.class)), ls, new Object()));
        assertNull(getGuardedInvocation(linker,
                createCallSiteDescriptor("foo", MethodType.methodType(Void.TYPE, Object.class)), ls, new Object()));

        // Can't link with name that doesn't start with dyn:
        assertNull(getGuardedInvocation(linker,
                createCallSiteDescriptor("dynx:foo", MethodType.methodType(Void.TYPE, Object.class)), ls, new Object()));
    }

    private static GuardedInvocation getGuardedInvocation(GuardingDynamicLinker linker, CallSiteDescriptor descriptor,
            LinkerServices linkerServices, Object... args) throws Exception {
        return linker.getGuardedInvocation(new LinkRequestImpl(descriptor, false, args), linkerServices);
    }

    public void testInvalidName() throws Exception {
        assertNull(getGuardedInvocation(new BeansLinker(),
                createCallSiteDescriptor("dyn", MethodType.methodType(int.class)), null, new Object[1]));
    }

    public static CallSiteDescriptor createCallSiteDescriptor(String name, MethodType methodType) {
        return CallSiteDescriptorFactory.create(MethodHandles.publicLookup(), name, methodType);
    }

}
