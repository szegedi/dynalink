/*
   Copyright 2009 Attila Szegedi

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
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import junit.framework.TestCase;

import org.dynalang.dynalink.CallSiteDescriptor;
import org.dynalang.dynalink.LinkerServices;
import org.dynalang.dynalink.support.Backport;
import org.dynalang.dynalink.support.Lookup;

/**
 * Tests for the {@link SimpleDynamicMethod}.
 * @author Attila Szegedi
 * @version $Id: $
 */
public class TestSimpleDynamicMethod extends TestCase
{
    /**
     * Test that it returns null when tried to link with a call site that has
     * less arguments than the method has fixed arguments.
     */
    public void testLessArgsOnFixArgs()
    {
        assertNull(new SimpleDynamicMethod(getTest1XMethod(), 
                false).getInvocation(new CallSiteDescriptor("", 
                        MethodType.methodType(Void.TYPE, Object.class, int.class)), 
                        null));
    }
    
    public void testMoreArgsOnFixArgs()
    {
        assertNull(new SimpleDynamicMethod(getTest1XMethod(), 
                false).getInvocation(new CallSiteDescriptor("", 
                        MethodType.methodType(Void.TYPE, Object.class, int.class, 
                                int.class, int.class)), null));
    }

    private static MethodHandle getTest1XMethod()
    {
        return Lookup.PUBLIC.findVirtual(Test1.class, "x", 
            MethodType.methodType(int.class, int.class, int.class));
    }

    public class Test1
    {
        public int x(int y, int z)
        {
            return y + z;
        }
        
        public int xv(int y, int... z)
        {
            for (int zz : z)
            {
                y += zz;
            }
            return y;
        }

        public String sv(String y, String... z)
        {
            for (String zz : z)
            {
                y += zz;
            }
            return y;
        }
    }
    
    public void testExactArgsOnFixArgs()
    {
        final MethodHandle mh = getTest1XMethod();
        final MethodType type = MethodType.methodType(int.class, Test1.class, 
                int.class, int.class);
        
        final boolean[] converterInvoked = new boolean[1];
        
        LinkerServices ls = new LinkerServices()
        {
            public boolean canConvert(Class<?> from, Class<?> to)
            {
                fail(); // Not supposed to be called
                return false;
            }

            public MethodHandle convertArguments(MethodHandle handle,
                    MethodType fromType)
            {
                assertSame(handle, mh);
                assertEquals(type, fromType);
                converterInvoked[0] = true;
                return handle;
            }
        };
        // Make sure it didn't interfere - just returned the same method handle
        assertSame(mh, new SimpleDynamicMethod(mh,false).getInvocation(
                new CallSiteDescriptor("", type), ls));
        assertTrue(converterInvoked[0]);
    }

    public void testVarArgsWithoutConversion()
    {
        final MethodHandle mh = getTest1XvMethod();
        final MethodType type = MethodType.methodType(int.class, Test1.class, 
                int.class, int[].class);
        
        final boolean[] converterInvoked = new boolean[1];
        
        LinkerServices ls = new LinkerServices()
        {
            public boolean canConvert(Class<?> from, Class<?> to)
            {
                fail(); // Not supposed to be called
                return false;
            }

            public MethodHandle convertArguments(MethodHandle handle,
                    MethodType fromType)
            {
                assertSame(handle, mh);
                assertEquals(type, fromType);
                converterInvoked[0] = true;
                return handle;
            }
        };
        // Make sure it didn't interfere - just returned the same method handle
        assertSame(mh, new SimpleDynamicMethod(mh, true).getInvocation(
                new CallSiteDescriptor("", type), ls));
        assertTrue(converterInvoked[0]);
    }

    private static MethodHandle getTest1XvMethod()
    {
        return Lookup.PUBLIC.findVirtual(
                Test1.class, "xv", MethodType.methodType(int.class, int.class, int[].class));
    }

    private static MethodHandle getTest1SvMethod()
    {
        return Lookup.PUBLIC.findVirtual(
                Test1.class, "sv", MethodType.methodType(String.class, String.class, String[].class));
    }

    public void testVarArgsWithFixArgsOnly() throws Throwable
    {
        final MethodHandle mh = getTest1XvMethod();
        final MethodType callSiteType = MethodType.methodType(int.class, 
                Object.class, int.class);
        
        final boolean[] converterInvoked = new boolean[1];
        
        LinkerServices ls = new LinkerServices()
        {
            public boolean canConvert(Class<?> from, Class<?> to)
            {
                fail(); // Not supposed to be called
                return false;
            }

            public MethodHandle convertArguments(MethodHandle handle,
                    MethodType fromType)
            {
                assertNotSame(handle, mh);
                assertEquals(MethodType.methodType(int.class, Test1.class, 
                        int.class), handle.type());
                assertEquals(callSiteType, fromType);
                converterInvoked[0] = true;
                return handle;
            }
        };
        MethodHandle newHandle = new SimpleDynamicMethod(mh, true).getInvocation(
                new CallSiteDescriptor("", callSiteType), ls);
        assertNotSame(newHandle, mh);
        assertTrue(converterInvoked[0]);
        assertEquals(1, newHandle.invokeWithArguments(new Test1(), 1));
    }

    public void testVarArgsWithPrimitiveConversion() throws Throwable
    {
        final MethodHandle mh = getTest1XvMethod();
        final MethodType callSiteType = MethodType.methodType(int.class, 
                Object.class, int.class, int.class, int.class);
        final MethodType declaredType = MethodType.methodType(int.class, 
                Test1.class, int.class, int.class, int.class);
        
        final boolean[] converterInvoked = new boolean[1];
        
        LinkerServices ls = new LinkerServices()
        {
            public boolean canConvert(Class<?> from, Class<?> to)
            {
                fail(); // Not supposed to be called
                return false;
            }

            public MethodHandle convertArguments(MethodHandle handle,
                    MethodType fromType)
            {
                assertNotSame(handle, mh);
                assertEquals(declaredType, handle.type());
                assertEquals(callSiteType, fromType);
                converterInvoked[0] = true;
                return handle;
            }
        };
        MethodHandle newHandle = new SimpleDynamicMethod(mh, true).getInvocation(
                new CallSiteDescriptor("", callSiteType), ls);
        assertNotSame(newHandle, mh);
        assertTrue(converterInvoked[0]);
        assertEquals(6, newHandle.invokeWithArguments(new Test1(), 1, 2, 3));
    }

    public void testVarArgsWithSinglePrimitiveArgConversion() throws Throwable
    {
        final MethodHandle mh = getTest1XvMethod();
        final MethodType declaredType = MethodType.methodType(int.class, 
                Test1.class, int.class, int.class);
        final MethodType callSiteType = MethodType.methodType(int.class, 
                Object.class, int.class, int.class);
        
        final boolean[] converterInvoked = new boolean[1];
        
        LinkerServices ls = new LinkerServices()
        {
            public boolean canConvert(Class<?> from, Class<?> to)
            {
                assertSame(int.class, from);
                assertSame(int[].class, to);
                return false;
            }

            public MethodHandle convertArguments(MethodHandle handle,
                    MethodType fromType)
            {
                assertNotSame(handle, mh);
                assertEquals(declaredType, handle.type());
                assertEquals(callSiteType, fromType);
                converterInvoked[0] = true;
                return handle;
            }
        };
        MethodHandle newHandle = new SimpleDynamicMethod(mh, true).getInvocation(
                new CallSiteDescriptor("", callSiteType), ls);
        assertNotSame(newHandle, mh);
        assertTrue(converterInvoked[0]);
        assertEquals(3, newHandle.invokeWithArguments(new Test1(), 1, 2));
    }

    public void testVarArgsWithSingleStringArgConversion() throws Throwable
    {
        final MethodHandle mh = getTest1SvMethod();
        final MethodType callSiteType = MethodType.methodType(String.class, 
                Object.class, String.class, String.class);
        
        final boolean[] converterInvoked = new boolean[1];
        
        LinkerServices ls = new LinkerServices()
        {
            public boolean canConvert(Class<?> from, Class<?> to)
            {
                assertSame(String.class, from);
                assertSame(String[].class, to);
                return false;
            }

            public MethodHandle convertArguments(MethodHandle handle,
                    MethodType fromType)
            {
                assertNotSame(handle, mh);
                assertEquals(MethodType.methodType(String.class, Test1.class, 
                        String.class, String.class), handle.type());
                assertEquals(callSiteType, fromType);
                converterInvoked[0] = true;
                return handle;
            }
        };
        MethodHandle newHandle = new SimpleDynamicMethod(mh, true).getInvocation(
                new CallSiteDescriptor("", callSiteType), ls);
        assertNotSame(newHandle, mh);
        assertTrue(converterInvoked[0]);
        assertEquals("ab", newHandle.invokeWithArguments(new Test1(), "a", "b"));
    }

    public void testVarArgsWithStringConversion() throws Throwable
    {
        final MethodHandle mh = getTest1SvMethod();
        final MethodType callSiteType = MethodType.methodType(String.class, 
                Object.class, String.class, String.class, String.class);
        
        final boolean[] converterInvoked = new boolean[1];
        
        LinkerServices ls = new LinkerServices()
        {
            public boolean canConvert(Class<?> from, Class<?> to)
            {
                fail(); // Not supposed to be called
                return false;
            }

            public MethodHandle convertArguments(MethodHandle handle,
                    MethodType fromType)
            {
                assertNotSame(handle, mh);
                assertEquals(MethodType.methodType(String.class, Test1.class, 
                        String.class, String.class, String.class), handle.type());
                assertEquals(callSiteType, fromType);
                converterInvoked[0] = true;
                return handle;
            }
        };
        MethodHandle newHandle = new SimpleDynamicMethod(mh, true).getInvocation(
                new CallSiteDescriptor("", callSiteType), ls);
        assertNotSame(newHandle, mh);
        assertTrue(converterInvoked[0]);
        assertEquals("abc", newHandle.invokeWithArguments(new Test1(), "a", "b", "c"));
        
    }

    public void testVarArgsWithSinglePrimitiveArgRuntimeConversion() throws Throwable
    {
        final MethodHandle mh = getTest1XvMethod();
        final MethodType methodType = MethodType.methodType(int.class, Test1.class, 
                int.class, int[].class);
        final MethodType callSiteType = MethodType.methodType(int.class, 
                Object.class, Object.class, Object.class);
        
        final int[] converterInvoked = new int[1];
        
        LinkerServices ls = new LinkerServices()
        {
            public boolean canConvert(Class<?> from, Class<?> to)
            {
                assertSame(Object.class, from);
                assertSame(int[].class, to);
                return true;
            }

            public MethodHandle convertArguments(MethodHandle handle,
                    MethodType fromType)
            {
                assertEquals(callSiteType, fromType);
                int c = ++converterInvoked[0];
                switch(c)
                {
                    case 1:
                    {
                        assertSame(handle, mh);
                        break;
                    }
                    case 2:
                    {
                        assertNotSame(handle, mh);
                        assertEquals(MethodType.methodType(int.class, 
                                Test1.class, int.class, int.class), 
                                handle.type());
                        break;
                    }
                    default:
                    {
                        fail();
                        break;
                    }
                }
                return MethodHandles.convertArguments(handle, fromType);
            }
        };
        MethodHandle newHandle = new SimpleDynamicMethod(mh, true).getInvocation(
                new CallSiteDescriptor("", callSiteType), ls);
        assertNotSame(newHandle, mh);
        assertEquals(2, converterInvoked[0]);
        assertEquals(3, newHandle.invokeWithArguments(new Test1(), 1, 2));
        assertEquals(6, newHandle.invokeWithArguments(new Test1(), 1, new int[] { 2, 3 }));
    }

    public void testVarArgsWithSingleStringArgRuntimeConversion() throws Throwable
    {
        final MethodHandle mh = getTest1SvMethod();
        final MethodType methodType = MethodType.methodType(String.class, Test1.class, 
                String.class, String[].class);
        final MethodType callSiteType = MethodType.methodType(String.class, 
                Object.class, Object.class, Object.class);
        
        final int[] converterInvoked = new int[1];
        
        LinkerServices ls = new LinkerServices()
        {
            public boolean canConvert(Class<?> from, Class<?> to)
            {
                assertSame(Object.class, from);
                assertSame(String[].class, to);
                return true;
            }

            public MethodHandle convertArguments(MethodHandle handle,
                    MethodType fromType)
            {
                assertEquals(callSiteType, fromType);
                int c = ++converterInvoked[0];
                switch(c)
                {
                    case 1:
                    {
                        assertSame(handle, mh);
                        break;
                    }
                    case 2:
                    {
                        assertNotSame(handle, mh);
                        assertEquals(MethodType.methodType(String.class, 
                                Test1.class, String.class, String.class), 
                                handle.type());
                        break;
                    }
                    default:
                    {
                        fail();
                        break;
                    }
                }
                return MethodHandles.convertArguments(handle, fromType);
            }
        };
        MethodHandle newHandle = new SimpleDynamicMethod(mh, true).getInvocation(
                new CallSiteDescriptor("", callSiteType), ls);
        assertNotSame(newHandle, mh);
        assertEquals(2, converterInvoked[0]);
        assertEquals("ab", newHandle.invokeWithArguments(new Test1(), "a", "b"));
        assertEquals("abc", newHandle.invokeWithArguments(new Test1(), "a", new String[] { "b", "c" }));
    }
}