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
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.dynalang.dynalink.CallSiteDescriptor;
import org.dynalang.dynalink.DynamicLinker;
import org.dynalang.dynalink.DynamicLinkerFactory;
import org.dynalang.dynalink.MonomorphicCallSite;
import org.dynalang.dynalink.linker.GuardedInvocation;
import org.dynalang.dynalink.linker.GuardingTypeConverterFactory;
import org.dynalang.dynalink.linker.LinkerServices;
import org.dynalang.dynalink.linker.TypeBasedGuardingDynamicLinker;
import org.dynalang.dynalink.support.CallSiteDescriptorFactory;
import org.dynalang.dynalink.support.Guards;
import org.dynalang.dynalink.support.LinkRequestImpl;
import org.dynalang.dynalink.support.LinkerServicesImpl;
import org.dynalang.dynalink.support.Lookup;
import org.dynalang.dynalink.support.TypeConverterFactory;

import junit.framework.TestCase;

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
        final CallSiteDescriptor cs = createCallSiteDescriptor("add", MethodType.methodType(Object.class, Object.class,
                Object.class, Object.class));
        final MethodHandle mh = dm.getInvocation(cs, linkerServices);
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
                MethodHandles.publicLookup(), "dyn:callPropWithThis:format", MethodType.methodType(Object.class,
                        Object.class, Object.class, Object.class, Object.class)));
        linker.link(callSite);
        System.out.println(callSite.dynamicInvoker().invokeWithArguments(StaticClass.forClass(String.class),
                "%4.0f %4.0f", 12f, 1f));
    }

    public void testIntOrDouble() throws Throwable {
        final DynamicMethod dm = linker.getMethod("intOrDouble");
        final CallSiteDescriptor cs = createCallSiteDescriptor("intOrDouble", MethodType.methodType(Object.class,
                Object.class, Object.class));
        final MethodHandle mh = dm.getInvocation(cs, linkerServices);
        assertNotNull(mh);
        assertEquals("int", mh.invokeWithArguments(new Test1(), 1));
        assertEquals("double", mh.invokeWithArguments(new Test1(), 1.0));
    }

    public void testStringOrDouble() throws Throwable {
        final DynamicMethod dm = linker.getMethod("stringOrDouble");
        final CallSiteDescriptor cs = createCallSiteDescriptor("stringOrDouble", MethodType.methodType(Object.class,
                Object.class, Object.class));
        final MethodHandle mh = dm.getInvocation(cs, linkerServices);
        assertNotNull(mh);
        assertEquals("double", mh.invokeWithArguments(new Test1(), 1));
        assertEquals("double", mh.invokeWithArguments(new Test1(), 1.0));
    }

    public void testVarArg() throws Throwable {
        final DynamicMethod dm = linker.getMethod("boo");
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
                    public GuardedInvocation convertToType(Class<?> sourceType, Class<?> targetType) {
                        if(targetType == int.class) {
                            return new GuardedInvocation(new Lookup(MethodHandles.publicLookup()).findVirtual(
                                    Double.class, "intValue", MethodType.methodType(int.class)).asType(
                                            MethodType.methodType(int.class, sourceType)), Guards.isOfClass(
                                                    Double.class, MethodType.methodType(boolean.class, sourceType)));
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

        final CallSiteDescriptor csd = createCallSiteDescriptor("dyn:callPropWithThis:boo", MethodType.methodType(
                Object.class, (List)Collections.nCopies(args.length + 2, Object.class)));
        assertEquals(retval, linker.getMethod("boo").getInvocation(csd, ls).invokeWithArguments(argList));
    }

    public static void main(String[] args) throws Throwable {
        TypeBasedGuardingDynamicLinker linker = BeansLinker.getLinkerForClass(Test1.class);
        LinkerServices ls = new LinkerServicesImpl(new TypeConverterFactory(new ArrayList<GuardingTypeConverterFactory>()), linker);


        Test1 test1 = new Test1();
        GuardedInvocation inv = linker.getGuardedInvocation(new LinkRequestImpl(
                CallSiteDescriptorFactory.create(MethodHandles.publicLookup(), "dyn:callPropWithThis:add",
                        MethodType.methodType(Object.class, Object.class, Object.class, Object.class, Object.class, Object.class)),
                        null, null, null, null, null), ls);
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