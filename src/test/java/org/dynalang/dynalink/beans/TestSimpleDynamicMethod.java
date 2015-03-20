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
import org.dynalang.dynalink.linker.ConversionComparator.Comparison;
import org.dynalang.dynalink.linker.GuardedInvocation;
import org.dynalang.dynalink.linker.LinkRequest;
import org.dynalang.dynalink.linker.LinkerServices;
import org.dynalang.dynalink.support.Lookup;
import org.dynalang.dynalink.support.TypeUtilities;

/**
 * Tests for the {@link SimpleDynamicMethod}.
 *
 * @author Attila Szegedi
 */
public class TestSimpleDynamicMethod extends TestCase {
    /**
     * Test that it returns null when tried to link with a call site that has less arguments than the method has fixed
     * arguments.
     */
    public void testLessArgsOnFixArgs() {
        assertNull(SingleDynamicMethod.getInvocation(getTest1XMethod(), MethodType.methodType(Void.TYPE,
                Object.class, int.class), new DefaultMockLinkerServices()));
    }

    public void testMoreArgsOnFixArgs() {
        assertNull(SingleDynamicMethod.getInvocation(getTest1XMethod(), MethodType.methodType(Void.TYPE,
                Object.class, int.class, int.class, int.class), new DefaultMockLinkerServices()));
    }

    private static MethodHandle getTest1XMethod() {
        return Lookup.PUBLIC.findVirtual(Test1.class, "x", MethodType.methodType(int.class, int.class, int.class));
    }

    public class Test1 {
        public int x(int y, int z) {
            return y + z;
        }

        public int xv(int y, int... z) {
            for(int zz: z) {
                y += zz;
            }
            return y;
        }

        public String sv(String y, String... z) {
            for(String zz: z) {
                y += zz;
            }
            return y;
        }
    }

    private abstract static class MockLinkerServices implements LinkerServices {
        @Override
        public boolean canConvert(Class<?> from, Class<?> to) {
            fail(); // Not supposed to be called
            return false;
        }

        @Override
        public GuardedInvocation getGuardedInvocation(LinkRequest lreq) throws Exception {
            fail(); // Not supposed to be called
            return null;
        }

        @Override
        public Comparison compareConversion(Class<?> sourceType, Class<?> targetType1, Class<?> targetType2) {
            fail(); // Not supposed to be called
            return null;
        }

        @Override
        public MethodHandle getTypeConverter(Class<?> sourceType, Class<?> targetType) {
            return null;
        }

        @Override
        public MethodHandle asTypeLosslessReturn(MethodHandle handle, MethodType fromType) {
            return Implementation.asTypeLosslessReturn(this, handle, fromType);
        }

        @Override
        public MethodHandle filterInternalObjects(MethodHandle target) {
            return target;
        }
    }

    private static class DefaultMockLinkerServices extends MockLinkerServices {
        @Override
        public MethodHandle asType(MethodHandle handle, MethodType fromType) {
            return handle.asType(fromType);
        }
    }

    public void testExactArgsOnFixArgs() {
        final MethodHandle mh = getTest1XMethod();
        final MethodType type = MethodType.methodType(int.class, Test1.class, int.class, int.class);

        final boolean[] converterInvoked = new boolean[1];

        LinkerServices ls = new MockLinkerServices() {
            @Override
            public MethodHandle asType(MethodHandle handle, MethodType fromType) {
                assertSame(handle, mh);
                assertEquals(type, fromType);
                converterInvoked[0] = true;
                return handle;
            }
        };
        // Make sure it didn't interfere - just returned the same method handle
        assertSame(mh, SingleDynamicMethod.getInvocation(mh, type, ls));
        assertTrue(converterInvoked[0]);
    }

    public void testVarArgsWithoutConversion() {
        final MethodHandle mh = getTest1XvMethod();
        final MethodType type = MethodType.methodType(int.class, Test1.class, int.class, int[].class);

        final boolean[] converterInvoked = new boolean[1];

        LinkerServices ls = new MockLinkerServices() {
            @Override
            public MethodHandle asType(MethodHandle handle, MethodType fromType) {
                assertEqualHandle(handle, mh);
                assertEquals(type, fromType);
                converterInvoked[0] = true;
                return handle;
            }
        };
        // Make sure it didn't interfere - just returned the same method handle
        assertEqualHandle(mh, SingleDynamicMethod.getInvocation(mh, type, ls));
        assertTrue(converterInvoked[0]);
    }

    private static void assertEqualHandle(MethodHandle m1, MethodHandle m2) {
        assertEquals(m1.type(), m2.type());
        assertEquals(m1.isVarargsCollector(), m2.isVarargsCollector());
    }

    private static MethodHandle getTest1XvMethod() {
        return Lookup.PUBLIC.findVirtual(Test1.class, "xv", MethodType.methodType(int.class, int.class, int[].class));
    }

    private static MethodHandle getTest1SvMethod() {
        return Lookup.PUBLIC.findVirtual(Test1.class, "sv",
                MethodType.methodType(String.class, String.class, String[].class));
    }

    public void testVarArgsWithFixArgsOnly() throws Throwable {
        final MethodHandle mh = getTest1XvMethod();
        final MethodType callSiteType = MethodType.methodType(int.class, Object.class, int.class);

        final boolean[] converterInvoked = new boolean[1];

        LinkerServices ls = new MockLinkerServices() {
            @Override
            public MethodHandle asType(MethodHandle handle, MethodType fromType) {
                assertNotSame(handle, mh);
                assertEquals(MethodType.methodType(int.class, Test1.class, int.class), handle.type());
                assertEquals(callSiteType, fromType);
                converterInvoked[0] = true;
                return handle;
            }
        };
        MethodHandle newHandle = SingleDynamicMethod.getInvocation(mh, callSiteType, ls);
        assertNotSame(newHandle, mh);
        assertTrue(converterInvoked[0]);
        assertEquals(1, newHandle.invokeWithArguments(new Test1(), 1));
    }

    public void testVarArgsWithPrimitiveConversion() throws Throwable {
        final MethodHandle mh = getTest1XvMethod();
        final MethodType callSiteType = MethodType.methodType(int.class, Object.class, int.class, int.class, int.class);
        final MethodType declaredType = MethodType.methodType(int.class, Test1.class, int.class, int.class, int.class);

        final boolean[] converterInvoked = new boolean[1];

        LinkerServices ls = new MockLinkerServices() {
            @Override
            public MethodHandle asType(MethodHandle handle, MethodType fromType) {
                assertNotSame(handle, mh);
                assertEquals(declaredType, handle.type());
                assertEquals(callSiteType, fromType);
                converterInvoked[0] = true;
                return handle;
            }
        };
        MethodHandle newHandle = SingleDynamicMethod.getInvocation(mh, callSiteType, ls);
        assertNotSame(newHandle, mh);
        assertTrue(converterInvoked[0]);
        assertEquals(6, newHandle.invokeWithArguments(new Test1(), 1, 2, 3));
    }

    public void testVarArgsWithSinglePrimitiveArgConversion() throws Throwable {
        final MethodHandle mh = getTest1XvMethod();
        final MethodType declaredType = MethodType.methodType(int.class, Test1.class, int.class, int.class);
        final MethodType callSiteType = MethodType.methodType(int.class, Object.class, int.class, int.class);

        final boolean[] converterInvoked = new boolean[1];

        LinkerServices ls = new MockLinkerServices() {
            @Override
            public boolean canConvert(Class<?> from, Class<?> to) {
                assertSame(int.class, from);
                assertSame(int[].class, to);
                return false;
            }

            @Override
            public MethodHandle asType(MethodHandle handle, MethodType fromType) {
                assertNotSame(handle, mh);
                assertEquals(declaredType, handle.type());
                assertEquals(callSiteType, fromType);
                converterInvoked[0] = true;
                return handle;
            }
        };
        MethodHandle newHandle = SingleDynamicMethod.getInvocation(mh, callSiteType, ls);
        assertNotSame(newHandle, mh);
        assertTrue(converterInvoked[0]);
        assertEquals(3, newHandle.invokeWithArguments(new Test1(), 1, 2));
    }

    public void testVarArgsWithSingleStringArgConversion() throws Throwable {
        final MethodHandle mh = getTest1SvMethod();
        final MethodType callSiteType = MethodType.methodType(String.class, Object.class, String.class, String.class);

        final boolean[] converterInvoked = new boolean[1];

        LinkerServices ls = new MockLinkerServices() {
            @Override
            public boolean canConvert(Class<?> from, Class<?> to) {
                assertSame(String.class, from);
                assertSame(String[].class, to);
                return false;
            }

            @Override
            public MethodHandle asType(MethodHandle handle, MethodType fromType) {
                assertNotSame(handle, mh);
                assertEquals(MethodType.methodType(String.class, Test1.class, String.class, String.class),
                        handle.type());
                assertEquals(callSiteType, fromType);
                converterInvoked[0] = true;
                return handle;
            }
        };
        MethodHandle newHandle = SingleDynamicMethod.getInvocation(mh, callSiteType, ls);
        assertNotSame(newHandle, mh);
        assertTrue(converterInvoked[0]);
        assertEquals("ab", newHandle.invokeWithArguments(new Test1(), "a", "b"));
    }

    public void testVarArgsWithStringConversion() throws Throwable {
        final MethodHandle mh = getTest1SvMethod();
        final MethodType callSiteType =
                MethodType.methodType(String.class, Object.class, String.class, String.class, String.class);

        final boolean[] converterInvoked = new boolean[1];

        LinkerServices ls = new MockLinkerServices() {
            @Override
            public MethodHandle asType(MethodHandle handle, MethodType fromType) {
                assertNotSame(handle, mh);
                assertEquals(
                        MethodType.methodType(String.class, Test1.class, String.class, String.class, String.class),
                        handle.type());
                assertEquals(callSiteType, fromType);
                converterInvoked[0] = true;
                return handle;
            }
        };
        MethodHandle newHandle = SingleDynamicMethod.getInvocation(mh, callSiteType, ls);
        assertNotSame(newHandle, mh);
        assertTrue(converterInvoked[0]);
        assertEquals("abc", newHandle.invokeWithArguments(new Test1(), "a", "b", "c"));

    }

    public void testVarArgsWithSinglePrimitiveArgRuntimeConversion() throws Throwable {
        final MethodHandle mh = getTest1XvMethod();
        final MethodType callSiteType = MethodType.methodType(int.class, Object.class, Object.class, Object.class);

        LinkerServices ls = new MockLinkerServices() {
            @Override
            public boolean canConvert(Class<?> from, Class<?> to) {
                return TypeUtilities.isMethodInvocationConvertible(from, to);
            }

            @Override
            public MethodHandle asType(MethodHandle handle, MethodType fromType) {
                return handle.asType(fromType);
            }
        };
        MethodHandle newHandle = SingleDynamicMethod.getInvocation(mh, callSiteType, ls);
        assertNotSame(newHandle, mh);
        assertEquals(3, newHandle.invokeWithArguments(new Test1(), 1, 2));
        assertEquals(6, newHandle.invokeWithArguments(new Test1(), 1, new int[] { 2, 3 }));
    }

    public void testVarArgsWithSingleStringArgRuntimeConversion() throws Throwable {
        final MethodHandle mh = getTest1SvMethod();
        final MethodType callSiteType = MethodType.methodType(String.class, Object.class, Object.class, Object.class);

        LinkerServices ls = new MockLinkerServices() {
            @Override
            public boolean canConvert(Class<?> from, Class<?> to) {
                return TypeUtilities.isMethodInvocationConvertible(from, to);
            }

            @Override
            public MethodHandle asType(MethodHandle handle, MethodType fromType) {
                return handle.asType(fromType);
            }
        };
        MethodHandle newHandle = SingleDynamicMethod.getInvocation(mh, callSiteType, ls);
        assertNotSame(newHandle, mh);
        assertEquals("ab", newHandle.invokeWithArguments(new Test1(), "a", "b"));
        assertEquals("abc", newHandle.invokeWithArguments(new Test1(), "a", new String[] { "b", "c" }));
    }
}
