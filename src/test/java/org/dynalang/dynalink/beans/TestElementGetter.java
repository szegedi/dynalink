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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import junit.framework.TestCase;
import org.dynalang.dynalink.DynamicLinkerFactory;

public class TestElementGetter extends TestCase {
    public void testEarlyBoundPrimitiveArrayElementGetter() throws Throwable {
        final RelinkCountingCallSite cs =
                new RelinkCountingCallSite("dyn:getElem", MethodType.methodType(Object.class, int[].class, int.class));
        new DynamicLinkerFactory().createLinker().link(cs);
        final MethodHandle invoker = cs.dynamicInvoker();
        final int[] x = new int[] { 3, 2, 1 };
        assertEquals(3, invoker.invokeWithArguments(x, 0));
        assertEquals(2, invoker.invokeWithArguments(x, 1));
        assertEquals(1, invoker.invokeWithArguments(x, 2));
        assertEquals(1, cs.getRelinkCount());
    }

    public void testEarlyBoundObjectArrayElementGetter() throws Throwable {
        final RelinkCountingCallSite cs =
                new RelinkCountingCallSite("dyn:getElem",
                        MethodType.methodType(Object.class, Object[].class, int.class));
        new DynamicLinkerFactory().createLinker().link(cs);
        final MethodHandle invoker = cs.dynamicInvoker();
        final Integer[] x = new Integer[] { 3, 2, 1 };
        final String[] y = new String[] { "a", "b", "c" };
        assertEquals(3, invoker.invokeWithArguments(x, 0));
        assertEquals(2, invoker.invokeWithArguments(x, 1));
        assertEquals(1, invoker.invokeWithArguments(x, 2));
        assertEquals("a", invoker.invokeWithArguments(y, 0));
        assertEquals("b", invoker.invokeWithArguments(y, 1));
        assertEquals("c", invoker.invokeWithArguments(y, 2));
        // All are assignable from Object[]
        assertEquals(1, cs.getRelinkCount());
    }

    public void testEarlyBoundListElementGetter() throws Throwable {
        final RelinkCountingCallSite cs =
                new RelinkCountingCallSite("dyn:getElem", MethodType.methodType(Object.class, List.class, int.class));
        new DynamicLinkerFactory().createLinker().link(cs);
        final MethodHandle invoker = cs.dynamicInvoker();
        final List<Integer> x = new ArrayList<Integer>(Arrays.asList(new Integer[] { 3, 2, 1 }));
        final List<String> y = new LinkedList<String>(Arrays.asList(new String[] { "a", "b", "c" }));
        assertEquals(3, invoker.invokeWithArguments(x, 0));
        assertEquals(2, invoker.invokeWithArguments(x, 1));
        assertEquals(1, invoker.invokeWithArguments(x, 2));
        assertEquals("a", invoker.invokeWithArguments(y, 0));
        assertEquals("b", invoker.invokeWithArguments(y, 1));
        assertEquals("c", invoker.invokeWithArguments(y, 2));
        // All are assignable from List
        assertEquals(1, cs.getRelinkCount());
    }

    public void testEarlyBoundMapElementGetter() throws Throwable {
        final RelinkCountingCallSite cs =
                new RelinkCountingCallSite("dyn:getElem", MethodType.methodType(Object.class, Map.class, Object.class));
        new DynamicLinkerFactory().createLinker().link(cs);
        final MethodHandle invoker = cs.dynamicInvoker();
        final Map<Integer, Integer> x = new HashMap<Integer, Integer>();
        x.put(0, 3);
        x.put(1, 2);
        x.put(2, 1);
        final Map<String, String> y = new TreeMap<String, String>();
        y.put("a", "x");
        y.put("b", "y");
        y.put("c", "z");
        assertEquals(3, invoker.invokeWithArguments(x, 0));
        assertEquals(2, invoker.invokeWithArguments(x, 1));
        assertEquals(1, invoker.invokeWithArguments(x, 2));
        assertEquals("x", invoker.invokeWithArguments(y, "a"));
        assertEquals("y", invoker.invokeWithArguments(y, "b"));
        assertEquals("z", invoker.invokeWithArguments(y, "c"));
        // All are assignable from Map
        assertEquals(1, cs.getRelinkCount());
    }

    public void testLateBoundMapElementGetter() throws Throwable {
        final RelinkCountingCallSite cs =
                new RelinkCountingCallSite("dyn:getElem", MethodType.methodType(Object.class, Object.class,
                        Object.class));
        new DynamicLinkerFactory().createLinker().link(cs);
        final MethodHandle invoker = cs.dynamicInvoker();
        final Map<Integer, Integer> x = new HashMap<Integer, Integer>();
        x.put(0, 3);
        x.put(1, 2);
        x.put(2, 1);
        final Map<String, String> y = new TreeMap<String, String>();
        y.put("a", "x");
        y.put("b", "y");
        y.put("c", "z");
        final List<String> z = new LinkedList<String>(Arrays.asList(new String[] { "a", "b", "c" }));
        assertEquals(3, invoker.invokeWithArguments(x, 0));
        assertEquals(2, invoker.invokeWithArguments(x, 1));
        assertEquals(1, invoker.invokeWithArguments(x, 2));
        assertEquals("x", invoker.invokeWithArguments(y, "a"));
        assertEquals("y", invoker.invokeWithArguments(y, "b"));
        assertEquals("z", invoker.invokeWithArguments(y, "c"));
        // All are assignable from Map
        assertEquals(1, cs.getRelinkCount());

        assertEquals("a", invoker.invokeWithArguments(z, 0));
        assertEquals("b", invoker.invokeWithArguments(z, 1));
        assertEquals("c", invoker.invokeWithArguments(z, 2));
        // Had to relink from Map to List
        assertEquals(2, cs.getRelinkCount());

        final String[] w = new String[] { "a", "b", "c" };
        assertEquals("a", invoker.invokeWithArguments(w, 0));
        assertEquals("b", invoker.invokeWithArguments(w, 1));
        assertEquals("c", invoker.invokeWithArguments(w, 2));
        // Had to relink from List to array
        assertEquals(3, cs.getRelinkCount());
    }
}
