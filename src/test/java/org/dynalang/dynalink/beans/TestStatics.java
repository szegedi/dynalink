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

import org.dynalang.dynalink.DynamicLinker;
import org.dynalang.dynalink.DynamicLinkerFactory;
import org.dynalang.dynalink.MonomorphicCallSite;
import org.dynalang.dynalink.support.CallSiteDescriptorFactory;

import junit.framework.TestCase;

public class TestStatics extends TestCase {
    private static final DynamicLinker linker = new DynamicLinkerFactory().createLinker();

    public void testFieldGetterSetter() throws Throwable {
        final Object statics = getProperty("static", T1.class);
        setProperty("foo", statics, "fooValue");
        assertEquals("fooValue", getProperty("foo", statics));
    }

    public void testPropertyGetterSetter() throws Throwable {
        final Object statics = getProperty("static", T2.class);
        setProperty("foo", statics, "fooValue");
        assertTrue(T2.invokedSetFooString);
        assertEquals("fooValue", getProperty("foo", statics));
        setProperty("foo", statics, 31);
        assertTrue(T2.invokedSetFooInt);
        assertEquals("1f", getProperty("foo", statics));
        getInvoker("dyn:callMethod:setFoo", Void.TYPE, Object.class, Object.class).invoke(statics, "blah");
        assertEquals("blah", getProperty("foo", statics));
    }

    private static void setProperty(String name, Object obj, Object value) throws Throwable {
        getInvoker("dyn:setProp:" + name, Void.TYPE, Object.class, Object.class).invoke(obj, value);
    }

    private static Object getProperty(String name, Object obj) throws Throwable {
        return getInvoker("dyn:getProp:" + name, Object.class, Object.class).invoke(obj);
    }

    private static MethodHandle getInvoker(String name, Class<?> retType, Class<?>... paramTypes) {
        return linker.link(new MonomorphicCallSite(CallSiteDescriptorFactory.create(
                MethodHandles.publicLookup(), name, MethodType.methodType(retType, paramTypes)))).dynamicInvoker();
    }

    public static class T1 {
        public static String foo;
    }

    public static class T2 {
        public static String foo;
        private static boolean invokedGetFoo = false;
        private static boolean invokedSetFooString = false;
        private static boolean invokedSetFooInt = false;

        public static String getFoo() {
            invokedGetFoo = true;
            return foo;
        }

        public static void setFoo(String foo) {
            invokedSetFooString = true;
            T2.foo = foo;
        }

        public static void setFoo(int foo) {
            invokedSetFooInt = true;
            T2.foo = Integer.toHexString(foo);
        }
    }
}
