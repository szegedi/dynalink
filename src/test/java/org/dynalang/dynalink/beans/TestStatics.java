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
import junit.framework.TestCase;
import org.dynalang.dynalink.DynamicLinker;
import org.dynalang.dynalink.DynamicLinkerFactory;
import org.dynalang.dynalink.MonomorphicCallSite;
import org.dynalang.dynalink.support.CallSiteDescriptorFactory;

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
