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
import java.lang.invoke.MethodType;

import org.dynalang.dynalink.DynamicLinkerFactory;

import junit.framework.TestCase;

/**
 * @author Attila Szegedi
 */
public class TestPropertyGetter extends TestCase {
    public void testFixedNamePropertyGetter() throws Throwable {
        final RelinkCountingCallSite callSite =
                new RelinkCountingCallSite("dyn:getProp:foo", MethodType.methodType(Object.class, Object.class));
        new DynamicLinkerFactory().createLinker().link(callSite);
        final MethodHandle invoker = callSite.dynamicInvoker();

        final T1 t1 = new T1();
        t1.setFoo("abc");
        assertSame("abc", invoker.invokeWithArguments(t1));
        assertEquals(1, callSite.getRelinkCount());

        final T3 t3 = new T3();
        t3.setFoo("def");
        assertSame("def", invoker.invokeWithArguments(t3));
        // No relink - T3 is subclass of T1, and getters can't get overloaded,
        // so we can link a more type-stable invocation.
        assertEquals(1, callSite.getRelinkCount());

        final T2 t2 = new T2();
        t2.setFoo("ghi");
        assertSame("ghi", invoker.invokeWithArguments(t2));
        assertEquals(2, callSite.getRelinkCount());

        final T4 t4 = new T4();
        t4.foo = "jkl";
        assertSame("jkl", invoker.invokeWithArguments(t4));
        assertEquals(3, callSite.getRelinkCount());

        final T5 t5 = new T5();
        t5.foo = "mno";
        assertSame("mno", invoker.invokeWithArguments(t5));
        // Must relink - T5 is a subclass of T4, but field getters can be overloaded with a property getter.
        assertEquals(4, callSite.getRelinkCount());
    }

    public void testVariableNamePropertyGetter() throws Throwable {
        final RelinkCountingCallSite callSite =
                new RelinkCountingCallSite("dyn:getProp", MethodType.methodType(Object.class, Object.class,
                        String.class));
        new DynamicLinkerFactory().createLinker().link(callSite);
        final MethodHandle invoker = callSite.dynamicInvoker();
        final T1 t1 = new T1();
        t1.setFoo("abc");
        assertSame("abc", invoker.invokeWithArguments(t1, "foo"));
        assertEquals(1, callSite.getRelinkCount());
        t1.setFoo("def");
        assertSame("def", invoker.invokeWithArguments(t1, "foo"));
        assertEquals(1, callSite.getRelinkCount());

        final T3 t3 = new T3();
        t3.setFoo("ghi");
        t3.setBar("xyz");
        assertSame("ghi", invoker.invokeWithArguments(t3, "foo"));
        assertSame("xyz", invoker.invokeWithArguments(t3, "bar"));
        assertEquals(2, callSite.getRelinkCount());

        final T2 t2 = new T2();
        t2.setFoo("jkl");
        t2.setBar("mno");
        assertSame("jkl", invoker.invokeWithArguments(t2, "foo"));
        assertSame("mno", invoker.invokeWithArguments(t2, "bar"));
        assertEquals(3, callSite.getRelinkCount());
    }

    public static class T1 {
        private Object foo;

        public void setFoo(String foo) {
            this.foo = foo;
        }

        public Object getFoo() {
            return foo;
        }
    }

    public static class T2 {
        private Object foo;
        private Object bar;

        public void setFoo(Object foo) {
            this.foo = foo;
        }

        public Object getFoo() {
            return foo;
        }

        public void setBar(Object bar) {
            this.bar = bar;
        }

        public Object getBar() {
            return bar;
        }
    }

    public static class T3 extends T1 {
        private Object bar;

        public void setBar(Object bar) {
            this.bar = bar;
        }

        public Object getBar() {
            return bar;
        }
    }

    public static class T4 {
        public String foo;
    }

    public static class T5 {
        public String foo;
    }
}
