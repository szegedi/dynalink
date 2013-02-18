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

package org.dynalang.dynalink.beans;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import junit.framework.AssertionFailedError;
import junit.framework.TestCase;
import org.dynalang.dynalink.CallSiteDescriptor;
import org.dynalang.dynalink.linker.ConversionComparator.Comparison;
import org.dynalang.dynalink.linker.GuardedInvocation;
import org.dynalang.dynalink.linker.GuardingDynamicLinker;
import org.dynalang.dynalink.linker.LinkRequest;
import org.dynalang.dynalink.linker.LinkerServices;
import org.dynalang.dynalink.support.CallSiteDescriptorFactory;
import org.dynalang.dynalink.support.LinkRequestImpl;

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
