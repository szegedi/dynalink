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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import junit.framework.TestCase;
import org.dynalang.dynalink.DynamicLinker;
import org.dynalang.dynalink.DynamicLinkerFactory;
import org.dynalang.dynalink.LinkerServicesFactory;
import org.dynalang.dynalink.MonomorphicCallSite;
import org.dynalang.dynalink.linker.GuardedInvocation;
import org.dynalang.dynalink.linker.GuardedTypeConversion;
import org.dynalang.dynalink.linker.GuardingTypeConverterFactory;
import org.dynalang.dynalink.linker.LinkerServices;
import org.dynalang.dynalink.linker.TypeBasedGuardingDynamicLinker;
import org.dynalang.dynalink.support.CallSiteDescriptorFactory;
import org.dynalang.dynalink.support.Guards;
import org.dynalang.dynalink.support.LinkRequestImpl;
import org.dynalang.dynalink.support.LinkerServicesImpl;
import org.dynalang.dynalink.support.Lookup;
import org.dynalang.dynalink.support.TypeConverterFactory;

/**
 *
 * @author Attila Szegedi
 */
public class TestOverloadedDynamicMethod extends TestCase {
    private BeanLinker linker;
    private LinkerServices linkerServices;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        linker = new BeanLinker(Test1.class);
        linkerServices = LinkerServicesFactory.getLinkerServices(linker);
    }

    public void testNoneMatchSignature() {
        final DynamicMethod dm = linker.getDynamicMethod("add");
        assertTrue(dm instanceof OverloadedDynamicMethod);

        // No zero-arg adds
        assertNull(getInvocation(dm, MethodType.methodType(int.class, Object.class), null));

        // No single-arg String add
        MethodHandle inv =
                getInvocation(dm, MethodType.methodType(String.class, Object.class, String.class), linkerServices);
        assertNull(String.valueOf(inv), inv);
    }

    public static MethodHandle getInvocation(DynamicMethod dm, MethodType methodType, LinkerServices linkerServices) {
        return dm.getInvocation(CallSiteDescriptorFactory.create(MethodHandles.publicLookup(), "", methodType), linkerServices);
    }

    public void testExactMatchSignature() throws Throwable {
        final DynamicMethod dm = linker.getDynamicMethod("add");
        // Two-arg String add should make a match
        final MethodHandle mh = getInvocation(dm, MethodType.methodType(String.class, Object.class, String.class,
                String.class), linkerServices);
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
        final DynamicMethod dm = linker.getDynamicMethod("add");
        // Two-arg String add should make a match
        final MethodHandle mh = getInvocation(dm, MethodType.methodType(Object.class, Object.class, Object.class,
                Object.class), linkerServices);
        assertNotNull(mh);
        // Must be able to invoke it with two Strings
        assertEquals("x", mh.invokeWithArguments(new Test1(), "a", "b"));
        // Must be able to invoke it with two ints
        assertEquals(1, mh.invokeWithArguments(new Test1(), 1, 2));
        // Must be able to invoke it with explicitly packed varargs
        assertEquals(4, mh.invokeWithArguments(new Test1(), 1, new int[] { 2 }));
    }

    public void testStringFormat() throws Throwable {
        DynamicLinker linker = new DynamicLinkerFactory().createLinker();
        MonomorphicCallSite callSite = new MonomorphicCallSite(CallSiteDescriptorFactory.create(
                MethodHandles.publicLookup(), "dyn:callMethod:format", MethodType.methodType(Object.class,
                        Object.class, Object.class, Object.class, Object.class)));
        linker.link(callSite);
        System.out.println(callSite.dynamicInvoker().invokeWithArguments(StaticClass.forClass(String.class),
                "%4.0f %4.0f", 12f, 1f));
    }

    public void testIntOrDouble() throws Throwable {
        final DynamicMethod dm = linker.getDynamicMethod("intOrDouble");
        final MethodHandle mh = getInvocation(dm, MethodType.methodType(Object.class, Object.class, Object.class),
                linkerServices);
        assertNotNull(mh);
        assertEquals("int", mh.invokeWithArguments(new Test1(), 1));
        assertEquals("double", mh.invokeWithArguments(new Test1(), 1.0));
    }

    public void testStringOrDouble() throws Throwable {
        final DynamicMethod dm = linker.getDynamicMethod("stringOrDouble");
        final MethodHandle mh = getInvocation(dm, MethodType.methodType(Object.class, Object.class, Object.class),
                linkerServices);
        assertNotNull(mh);
        assertEquals("double", mh.invokeWithArguments(new Test1(), 1));
        assertEquals("double", mh.invokeWithArguments(new Test1(), 1.0));
    }

    public void testVarArg() throws Throwable {
        final DynamicMethod dm = linker.getDynamicMethod("boo");
        // we want to link to the one-arg invocation
        assertBooReturns(1, linkerServices, 1);

        // we want to link to the vararg invocation
        assertBooReturns(2, linkerServices);

        // we want to link to the vararg invocation
        assertBooReturns(2, linkerServices, 1, 2);

        // If we're linking with a converter that knows how to convert double to int, then we want to make sure we
        // don't link to the vararg (Class, int[]) invocation but to the (Class, int) invocation.
        final LinkerServices ls = new LinkerServicesImpl(new TypeConverterFactory(Collections.singleton(
                new GuardingTypeConverterFactory() {
                    @Override
                    public GuardedTypeConversion convertToType(Class<?> sourceType, Class<?> targetType) {
                        if(targetType == int.class) {
                            return new GuardedTypeConversion(new GuardedInvocation(new Lookup(MethodHandles.publicLookup()).findVirtual(
                                    Double.class, "intValue", MethodType.methodType(int.class)).asType(
                                            MethodType.methodType(int.class, sourceType)), Guards.isOfClass(
                                                    Double.class, MethodType.methodType(boolean.class, sourceType))), true);
                        }
                        return null;
                    }
                })), linker);
        assertBooReturns(1, ls, 1.0);
    }

    private void assertBooReturns(int retval, LinkerServices ls, Object... args) throws Throwable {
        final List<Object> argList = new ArrayList<>();
        argList.add(new Test1());
        argList.add(String.class);
        argList.addAll(Arrays.asList(args));

        assertEquals(retval, getInvocation(linker.getDynamicMethod("boo"), MethodType.methodType(Object.class,
                Collections.nCopies(args.length + 2, Object.class).toArray(new Class[0])), ls).invokeWithArguments(
                        argList));
    }

    public static void main(String[] args) throws Throwable {
        TypeBasedGuardingDynamicLinker linker = BeansLinker.getLinkerForClass(Test1.class);
        LinkerServices ls = LinkerServicesFactory.getLinkerServices(linker);


        Test1 test1 = new Test1();
        GuardedInvocation inv = linker.getGuardedInvocation(new LinkRequestImpl(
                CallSiteDescriptorFactory.create(MethodHandles.publicLookup(), "dyn:callMethod:add",
                        MethodType.methodType(Object.class, Object.class, Object.class, Object.class, Object.class, Object.class)),
                        null, 0, false, null, null, null, null, null), ls);
        MethodHandle handle = inv.getInvocation();

        System.out.println(handle.invokeWithArguments(test1, 1, 2, 3, 4));
    }

    public static class Test1 {
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

        // The "boo" methods are meant to represent the situation with java.lang.reflect.Array.newInstance() which is
        // quite interesting.

        public int boo(Class<?> a, int b) {
            return 1;
        }

        public int boo(Class<?> a, int... b) {
            return 2;
        }

        public String intOrDouble(int i) {
            return "int";
        }

        public String intOrDouble(double d) {
            return "double";
        }

        public String stringOrDouble(String s) {
            return "String";
        }

        public String stringOrDouble(double d) {
            return "double";
        }
    }
}
