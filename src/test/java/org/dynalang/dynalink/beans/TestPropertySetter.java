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
import junit.framework.TestCase;
import org.dynalang.dynalink.DynamicLinkerFactory;

public class TestPropertySetter extends TestCase {
    public void testFixedNamePropertySetter() throws Throwable {
        final RelinkCountingCallSite callSite =
                new RelinkCountingCallSite("dyn:setProp:foo", MethodType.methodType(Void.TYPE, Object.class,
                        Object.class));
        new DynamicLinkerFactory().createLinker().link(callSite);
        final MethodHandle invoker = callSite.dynamicInvoker();
        final T1 t1 = new T1();
        invoker.invokeWithArguments(t1, "abc");
        assertSame("abc", t1.foo);
        assertEquals(1, callSite.getRelinkCount());
        invoker.invokeWithArguments(t1, "def");
        assertSame("def", t1.foo);
        assertEquals(1, callSite.getRelinkCount());
        final T2 t2 = new T2();
        final Object val = new Object();
        invoker.invokeWithArguments(t2, val);
        assertSame(val, t2.foo);
        assertEquals(2, callSite.getRelinkCount());
    }

    public void testVariableNamePropertySetter() throws Throwable {
        final RelinkCountingCallSite callSite =
                new RelinkCountingCallSite("dyn:setProp", MethodType.methodType(Void.TYPE, Object.class, String.class,
                        Object.class));
        new DynamicLinkerFactory().createLinker().link(callSite);
        final MethodHandle invoker = callSite.dynamicInvoker();
        final T1 t1 = new T1();
        invoker.invokeWithArguments(t1, "foo", "abc");
        assertSame("abc", t1.foo);
        assertEquals(1, callSite.getRelinkCount());
        invoker.invokeWithArguments(t1, "foo", "def");
        assertSame("def", t1.foo);
        assertEquals(1, callSite.getRelinkCount());
        final T2 t2 = new T2();
        final Object val1 = new Object();
        invoker.invokeWithArguments(t2, "foo", val1);
        assertSame(val1, t2.foo);
        assertEquals(2, callSite.getRelinkCount());
        final Object val2 = new Object();
        invoker.invokeWithArguments(t2, "bar", val2);
        assertSame(val2, t2.bar);
        assertEquals(2, callSite.getRelinkCount());
    }

    public static class T1 {
        private Object foo;

        public void setFoo(String foo) {
            this.foo = foo;
        }
    }

    public static class T2 {
        private Object foo;
        private Object bar;

        public void setFoo(Object foo) {
            this.foo = foo;
        }

        public void setBar(Object... args) {
            this.bar = args[0];
        }

        public void setBar(int x) {
            this.bar = x;
        }
    }
}
