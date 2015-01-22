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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import junit.framework.TestCase;
import org.dynalang.dynalink.DynamicLinker;
import org.dynalang.dynalink.DynamicLinkerFactory;
import org.dynalang.dynalink.MonomorphicCallSite;
import org.dynalang.dynalink.NoSuchDynamicMethodException;
import org.dynalang.dynalink.support.CallSiteDescriptorFactory;

public class TestCompositeOperations extends TestCase {

    public void testGetElemPropMethodOnMap() throws Throwable {
        final String op = "dyn:getElem|getProp|getMethod";
        final Map<String, Object> map = new HashMap<>();
        assertNull(invokeBoth(op, map, "foo"));
        assertEquals(true, invokeBoth(op, map, "empty"));
        assertSame(getDynamicMethod(HashMap.class, "size"), invokeBoth(op, map, "size"));
        map.put("foo", "bar");
        assertSame("bar", invokeBoth(op, map, "foo"));
        map.put("empty", "xyz");
        assertSame("xyz", invokeBoth(op, map, "empty"));
        map.put("size", "abc");
        assertSame("abc", invokeBoth(op, map, "size"));
    }

    public static class HashMapWithProperty extends HashMap<Object, Object> {
        private Object color;

        public Object setColor(Object color) {
            return this.color = color;
        }

        public Object getColor() {
            return color;
        }
    }

    public void testSetElemPropOnMap() throws Throwable {
        final String op = "dyn:setElem|setProp";
        final HashMapWithProperty map = new HashMapWithProperty();
        invoke(op, map, "color", "foo");
        assertSame("foo", map.get("color"));
        invokeFixedKey(op, map, "color", "bar");
        assertSame("bar", map.get("color"));
        assertNull(map.getColor());
    }

    public void testSetPropElemOnMap() throws Throwable {
        final String op = "dyn:setProp|setElem";
        final HashMapWithProperty map = new HashMapWithProperty();

        invoke(op, map, "color", "foo");
        assertNull(map.get("color"));
        assertSame("foo", map.getColor());

        invokeFixedKey(op, map, "color", "bar");
        assertNull(map.get("color"));
        assertSame("bar", map.getColor());

        invoke(op, map, "shape", "baz");
        assertSame("baz", map.get("shape"));
        invokeFixedKey(op, map, "shape", "bay");
        assertSame("bay", map.get("shape"));

        assertSame("bar", map.getColor());
    }

    public static class ArrayListWithProperty extends ArrayList<Object> {
        private Object color;

        public Object setColor(Object color) {
            return this.color = color;
        }

        public Object getColor() {
            return color;
        }
    }

    public void testSetElemPropOnList() throws Throwable {
        final String op = "dyn:setElem|setProp";
        final ArrayListWithProperty list = new ArrayListWithProperty();
        invoke(op, list, "color", "foo1");
        assertSame("foo1", list.getColor());
        invokeFixedKey(op, list, "color", "foo2");
        assertSame("foo2", list.getColor());
        list.add("elem0");
        invoke(op, list, 0, "elem0-changed1");
        assertSame("elem0-changed1", list.get(0));
        invokeFixedKey(op, list, 0, "elem0-changed2");
        assertSame("elem0-changed2", list.get(0));
    }

    public void testSetPropElemOnList() throws Throwable {
        final String op = "dyn:setProp|setElem";
        final ArrayListWithProperty list = new ArrayListWithProperty();
        invoke(op, list, "color", "foo1");
        assertSame("foo1", list.getColor());
        invokeFixedKey(op, list, "color", "foo2");
        assertSame("foo2", list.getColor());
        list.add("elem0");
        invoke(op, list, 0, "elem0-changed1");
        assertSame("elem0-changed1", list.get(0));
        invokeFixedKey(op, list, 0, "elem0-changed2");
        assertSame("elem0-changed2", list.get(0));
    }

    public void testGetElemPropMethodOnList() throws Throwable {
        final String op = "dyn:getElem|getProp|getMethod";
        final List<Object> list = new ArrayList<>();

        // As the key is fixed at link time, an IndexOutOfBoundsException is expected
        try {
            invokeFixedKey(op, list, 0);
            fail();
        } catch(IndexOutOfBoundsException e) {
        }
        try {
            invoke(op, list, 0);
            fail();
        } catch(IndexOutOfBoundsException e) {
        }


        // We can decide with 100% certainty that there's neither an element, property, nor method named "foo" at link
        // time.
        try {
            invokeFixedKey(op, list, "foo");
            fail();
        } catch(NoSuchDynamicMethodException e) {
            // This is expected
        }
        assertNull(invoke(op, list, "foo"));

        assertEquals(true, invokeBoth(op, list, "empty"));
        assertSame(getDynamicMethod(ArrayList.class, "size"), invokeBoth(op, list, "size"));
        list.add("bar");
        assertSame("bar", invokeBoth(op, list, 0));
    }

    public void testGetElemPropMethodOnArray() throws Throwable {
        final String op = "dyn:getElem|getProp|getMethod";
        final Object[] array = new Object[1];
        assertSame(1, invokeBoth(op, array, "length"));
        assertSame(getDynamicMethod(Object[].class, "hashCode"), invokeBoth(op, array, "hashCode"));
        assertNull(invokeBoth(op, array, 0));

        // We can decide with 100% certainty that there's neither an element, property, nor method named "foo" at link
        // time.
        try {
            invokeFixedKey(op, array, "foo");
            fail();
        } catch(NoSuchDynamicMethodException e) {
            // This is expected
        }
        assertNull(invoke(op, array, "foo"));

        array[0] = "bar";
        assertSame("bar", invokeBoth(op, array, 0));
    }

    public void testGetPropElemMethod() throws Throwable {
        final String op = "dyn:getProp|getElem|getMethod";
        final Map<String, Object> map = new HashMap<>();
        assertNull(invokeBoth(op, map, "foo"));
        assertEquals(true, invokeBoth(op, map, "empty"));
        assertSame(getDynamicMethod(HashMap.class, "size"), invokeBoth(op, map, "size"));
        map.put("foo", "bar");
        assertSame("bar", invokeBoth(op, map, "foo"));
        map.put("empty", "xyz");
        assertEquals(false, invokeBoth(op, map, "empty"));
        map.put("size", "abc");
        assertSame("abc", invokeBoth(op, map, "size"));
    }

    public void testGetMethodElemProp() throws Throwable {
        final String op = "dyn:getMethod|getProp|getElem";
        final Map<String, Object> map = new HashMap<>();
        assertNull(invokeBoth(op, map, "foo"));
        assertEquals(true, invokeBoth(op, map, "empty"));
        assertSame(getDynamicMethod(HashMap.class, "size"), invokeBoth(op, map, "size"));
        map.put("foo", "bar");
        assertSame("bar", invokeBoth(op, map, "foo"));
        map.put("empty", "xyz");
        assertEquals(false, invokeBoth(op, map, "empty"));
        map.put("size", "abc");
        assertSame(getDynamicMethod(HashMap.class, "size"), invokeBoth(op, map, "size"));
    }

    private static final DynamicLinker linker = new DynamicLinkerFactory().createLinker();

    private static Object invokeBoth(String operation, Object... args) throws Throwable {
        final Object o = invoke(operation, args);
        assertSame(o, invokeFixedKey(operation, args));
        return o;
    }

    private static Object invokeFixedKey(String operation, Object... args) throws Throwable {
        ArrayList<Object> argsList = new ArrayList<>(Arrays.asList(args));
        argsList.remove(1);
        return invoke(operation + ":" + args[1], argsList.toArray());
    }

    private static Object invoke(String operation, Object... args) throws Throwable {
        int argCount;
        if (operation.startsWith("dyn:get")) {
            argCount = 2;
        } else if(operation.startsWith("dyn:set")) {
            argCount = 3;
        } else {
            throw new AssertionError();
        }
        final int tokens = new StringTokenizer(operation, ":").countTokens();
        if(tokens == 3) {
            argCount -= 1;
        } else {
            assertEquals(2, tokens);
        }
        List<Class<?>> argTypes = new ArrayList<>(argCount);
        for(int i = 0; i < argCount; ++i) {
            argTypes.add(Object.class);
        }
        return linker.link(new MonomorphicCallSite(CallSiteDescriptorFactory.create(MethodHandles.publicLookup(),
                operation, MethodType.methodType(Object.class, argTypes)))).dynamicInvoker().invokeWithArguments(args);
    }

    private static DynamicMethod getDynamicMethod(Class<?> clazz, String name) {
        return ((BeanLinker)BeansLinker.getLinkerForClass(clazz)).getDynamicMethod(name);
    }
}
