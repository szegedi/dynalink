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

import static org.dynalang.dynalink.beans.TestBeansLinker.createCallSiteDescriptor;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.dynalang.dynalink.CallSiteDescriptor;
import org.dynalang.dynalink.DynamicLinker;
import org.dynalang.dynalink.DynamicLinkerFactory;
import org.dynalang.dynalink.LinkerServicesFactory;
import org.dynalang.dynalink.linker.GuardedInvocation;
import org.dynalang.dynalink.linker.GuardingDynamicLinker;
import org.dynalang.dynalink.support.LinkRequestImpl;

import junit.framework.TestCase;

public class TestLengthGetter extends TestCase {
    public void testEarlyBoundArrayLengthGetter() throws Throwable {
        testEarlyBoundArrayLengthGetter(byte[].class);
        testEarlyBoundArrayLengthGetter(short[].class);
        testEarlyBoundArrayLengthGetter(char[].class);
        testEarlyBoundArrayLengthGetter(int[].class);
        testEarlyBoundArrayLengthGetter(long[].class);
        testEarlyBoundArrayLengthGetter(float[].class);
        testEarlyBoundArrayLengthGetter(double[].class);
        testEarlyBoundArrayLengthGetter(Object[].class);
        testEarlyBoundArrayLengthGetter(String[].class);
    }

    private static GuardedInvocation getGuardedInvocation(GuardingDynamicLinker linker, CallSiteDescriptor descriptor,
            Object... args) throws Exception {
        return linker.getGuardedInvocation(new LinkRequestImpl(descriptor, false, args),
                LinkerServicesFactory.getLinkerServices(linker));
    }

    private static void testEarlyBoundArrayLengthGetter(Class<?> arrayClass) throws Throwable {
        testEarlyBoundArrayLengthGetter(arrayClass, "getLength", true);
    }

    private static void testEarlyBoundArrayLengthGetter(Class<?> arrayClass, String op, boolean early) throws Throwable {
        final BeansLinker bl = new BeansLinker();
        final CallSiteDescriptor csd =
                createCallSiteDescriptor("dyn:" + op, MethodType.methodType(int.class, arrayClass));
        final Object array = Array.newInstance(arrayClass.getComponentType(), 2);
        final GuardedInvocation inv = getGuardedInvocation(bl, csd, array);
        if(early) {
            // early bound, as call site guarantees we'll pass an array
            assertNull(inv.getGuard());
        }
        final MethodHandle mh = inv.getInvocation();
        assertNotNull(mh);
        assertEquals(csd.getMethodType(), mh.type());
        assertEquals(2, mh.invokeWithArguments(array));
    }

    private static void testArrayLengthPropertyGetter(Class<?> arrayClass) throws Throwable {
        testEarlyBoundArrayLengthGetter(arrayClass, "getProp:length", false);
    }

    public static void testArrayLengthPropertyGetter() throws Throwable {
        testArrayLengthPropertyGetter(byte[].class);
        testArrayLengthPropertyGetter(short[].class);
        testArrayLengthPropertyGetter(char[].class);
        testArrayLengthPropertyGetter(int[].class);
        testArrayLengthPropertyGetter(long[].class);
        testArrayLengthPropertyGetter(float[].class);
        testArrayLengthPropertyGetter(double[].class);
        testArrayLengthPropertyGetter(Object[].class);
        testArrayLengthPropertyGetter(String[].class);
    }

    public void testEarlyBoundCollectionLengthGetter() throws Throwable {
        final BeansLinker bl = new BeansLinker();
        final CallSiteDescriptor csd =
                createCallSiteDescriptor("dyn:getLength", MethodType.methodType(int.class, List.class));
        final GuardedInvocation inv = getGuardedInvocation(bl, csd, Collections.EMPTY_LIST);
        assertNull(inv.getGuard());
        final MethodHandle mh = inv.getInvocation();
        assertNotNull(mh);
        assertEquals(csd.getMethodType(), mh.type());
        assertEquals(0, mh.invokeWithArguments(new Object[] { Collections.EMPTY_LIST }));
        assertEquals(2, mh.invokeWithArguments(new Object[] { Arrays.asList(new Object[] { "a", "b" }) }));
    }

    public void testEarlyBoundMapLengthGetter() throws Throwable {
        final BeansLinker bl = new BeansLinker();
        final CallSiteDescriptor csd =
                createCallSiteDescriptor("dyn:getLength", MethodType.methodType(int.class, Map.class));
        final GuardedInvocation inv = getGuardedInvocation(bl, csd, Collections.EMPTY_MAP);
        assertNull(inv.getGuard());
        final MethodHandle mh = inv.getInvocation();
        assertNotNull(mh);
        assertEquals(csd.getMethodType(), mh.type());
        assertEquals(0, mh.invokeWithArguments(Collections.EMPTY_MAP));
        assertEquals(1, mh.invokeWithArguments(Collections.singletonMap("a", "b")));
    }

    public void testLateBoundLengthGetter() throws Throwable {
        final DynamicLinker linker = new DynamicLinkerFactory().createLinker();
        final RelinkCountingCallSite callSite =
                new RelinkCountingCallSite("dyn:getLength", MethodType.methodType(int.class, Object.class));

        linker.link(callSite);
        assertEquals(0, callSite.getRelinkCount());
        MethodHandle callSiteInvoker = callSite.dynamicInvoker();
        assertEquals(2, callSiteInvoker.invokeWithArguments(new int[2]));
        assertEquals(1, callSite.getRelinkCount());
        assertEquals(3, callSiteInvoker.invokeWithArguments(new Object[] { new Object[3] }));
        // No relink - length getter applies to all array classes
        assertEquals(1, callSite.getRelinkCount());
        assertEquals(4, callSiteInvoker.invokeWithArguments(new long[4]));
        // Still no relink
        assertEquals(1, callSite.getRelinkCount());

        assertEquals(5, callSiteInvoker.invokeWithArguments(new Object[] { Arrays.asList(new Object[5]) }));
        // Relinked for collections
        assertEquals(2, callSite.getRelinkCount());
        assertEquals(0, callSiteInvoker.invokeWithArguments(new HashSet<Object>()));
        // No relink for various collection types
        assertEquals(2, callSite.getRelinkCount());

        assertEquals(1, callSiteInvoker.invokeWithArguments(Collections.singletonMap("1", "2")));
        // Relinked for maps
        assertEquals(3, callSite.getRelinkCount());
        assertEquals(0, callSiteInvoker.invokeWithArguments(new HashMap<Object, Object>()));
        // No relink for various map types
        assertEquals(3, callSite.getRelinkCount());

        assertEquals(6, callSiteInvoker.invokeWithArguments(new long[6]));
        // Relinked again for arrays
        assertEquals(4, callSite.getRelinkCount());
    }
}