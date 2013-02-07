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

package org.dynalang.dynalink.beans;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.dynalang.dynalink.DynamicLinker;
import org.dynalang.dynalink.DynamicLinkerFactory;
import org.dynalang.dynalink.MonomorphicCallSite;
import org.dynalang.dynalink.support.CallSiteDescriptorFactory;

import junit.framework.TestCase;

public class TestNew extends TestCase {
    private static final DynamicLinker linker = new DynamicLinkerFactory().createLinker();

    public void testAll() throws Throwable {
        test("a", "b", "String,String", ctor("a", "b"));
        test("a", null, "String", ctor("a"));
        test("a", "1f", "String,int", ctor("a", 31));
    }

    private static void test(String foo, String bar, String ctor, T1 obj) {
        assertEquals(foo, obj.foo);
        assertEquals(bar, obj.bar);
        assertEquals(ctor, obj.ctor);
    }

    private static T1 ctor(Object arg1) throws Throwable {
        return T1.class.cast(getInvoker("dyn:new", T1.class, StaticClass.class, Object.class).invoke(
                StaticClass.forClass(T1.class), arg1));
    }

    private static T1 ctor(Object arg1, Object arg2) throws Throwable {
        return T1.class.cast(getInvoker("dyn:new", T1.class, StaticClass.class, Object.class, Object.class).invoke(
                StaticClass.forClass(T1.class), arg1, arg2));
    }

    private static MethodHandle getInvoker(String name, Class<?> retType, Class<?>... paramTypes) {
        return linker.link(new MonomorphicCallSite(CallSiteDescriptorFactory.create(
                MethodHandles.publicLookup(), name, MethodType.methodType(retType, paramTypes)))).dynamicInvoker();
    }

    public static class T1 {
        public final String foo;
        public final String bar;
        public final String ctor;

        public T1(String foo, String bar, String ctor) {
            this.foo = foo;
            this.bar = bar;
            this.ctor = ctor;
        }

        public T1(String foo, String bar) {
            this(foo, bar, "String,String");
        }

        public T1(String foo, int bar) {
            this(foo, Integer.toHexString(bar), "String,int");
        }

        public T1(String foo) {
            this(foo, null, "String");
        }
    }
}
