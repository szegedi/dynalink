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

package org.dynalang.dynalink.beans;

import static org.dynalang.dynalink.beans.TestBeansLinker.createCallSiteDescriptor;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.LinkedList;

import junit.framework.TestCase;

import org.dynalang.dynalink.linker.CallSiteDescriptor;
import org.dynalang.dynalink.linker.GuardingTypeConverterFactory;
import org.dynalang.dynalink.linker.LinkerServices;
import org.dynalang.dynalink.support.LinkerServicesImpl;
import org.dynalang.dynalink.support.TypeConverterFactory;

/**
 *
 * @author Attila Szegedi
 * @version $Id: $
 */
public class TestOverloadedDynamicMethod extends TestCase {
    private BeanLinker linker;
    private LinkerServices linkerServices;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        linker = new BeanLinker(Test1.class);
        linkerServices =
                new LinkerServicesImpl(new TypeConverterFactory(new LinkedList<GuardingTypeConverterFactory>()), linker);
    }

    public void testNoneMatchSignature() {
        final DynamicMethod dm = linker.getMethod("add");
        assertTrue(dm instanceof OverloadedDynamicMethod);

        // No zero-arg adds
        assertNull(dm.getInvocation(createCallSiteDescriptor("add", MethodType.methodType(int.class, Object.class)),
                null));

        // No single-arg String add
        MethodHandle inv =
                dm.getInvocation(
                        createCallSiteDescriptor("add", MethodType.methodType(String.class, Object.class, String.class)),
                        linkerServices);
        assertNull(String.valueOf(inv), inv);
    }

    public void testExactMatchSignature() throws Throwable {
        final DynamicMethod dm = linker.getMethod("add");
        // Two-arg String add should make a match
        final CallSiteDescriptor cs =
                createCallSiteDescriptor("add",
                        MethodType.methodType(String.class, Object.class, String.class, String.class));
        final MethodHandle mh = dm.getInvocation(cs, linkerServices);
        assertNotNull(mh);
        // Must be able to invoke it with two strings
        assertEquals("x", mh.invokeWithArguments(new Test1(), "a", "b"));
        // Must not be able to invoke it with two ints
        try {
            mh.invokeWithArguments(new Test1(), 1, 2);
        } catch(ClassCastException e) {
            // This is expected
        }
    }

    public void testVeryGenericSignature() throws Throwable {
        final DynamicMethod dm = linker.getMethod("add");
        // Two-arg String add should make a match
        final CallSiteDescriptor cs =
                createCallSiteDescriptor("add",
                        MethodType.methodType(Object.class, Object.class, Object.class, Object.class));
        final MethodHandle mh = dm.getInvocation(cs, linkerServices);
        assertNotNull(mh);
        // Must be able to invoke it with two Strings
        assertEquals("x", mh.invokeWithArguments(new Test1(), "a", "b"));
        // Must be able to invoke it with two ints
        assertEquals(1, mh.invokeWithArguments(new Test1(), 1, 2));
        // Must be able to invoke it with explicitly packed varargs
        assertEquals(4, mh.invokeWithArguments(new Test1(), 1, new int[] { 2 }));
    }

    public class Test1 {
        public int add(int i1, int i2) {
            return 1;
        }

        public int add(int i1, int i2, int i3) {
            return 2;
        }

        public String add(String i1, String i2) {
            return "x";
        }

        public int add(int i1, int... in) {
            return 4;
        }
    }
}