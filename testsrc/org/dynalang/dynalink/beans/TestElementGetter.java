package org.dynalang.dynalink.beans;

import java.dyn.MethodHandle;
import java.dyn.MethodHandles;
import java.dyn.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import junit.framework.TestCase;

import org.dynalang.dynalink.DynamicLinkerFactory;

public class TestElementGetter extends TestCase
{
    public void testEarlyBoundPrimitiveArrayElementGetter() throws Throwable {
        final RelinkCountingCallSite cs = new RelinkCountingCallSite(
                "dyn:getElem", MethodType.methodType(Object.class, int[].class, 
                        int.class));
        new DynamicLinkerFactory().createLinker().link(cs);
        final MethodHandle invoker = MethodHandles.dynamicInvoker(cs);
        final int[] x = new int[] { 3, 2, 1 };
        assertEquals(3, invoker.invokeVarargs(x, 0)); 
        assertEquals(2, invoker.invokeVarargs(x, 1)); 
        assertEquals(1, invoker.invokeVarargs(x, 2));
        assertEquals(1, cs.getRelinkCount());
    }

    public void testEarlyBoundObjectArrayElementGetter() throws Throwable {
        final RelinkCountingCallSite cs = new RelinkCountingCallSite(
                "dyn:getElem", MethodType.methodType(Object.class, Object[].class, 
                        int.class));
        new DynamicLinkerFactory().createLinker().link(cs);
        final MethodHandle invoker = MethodHandles.dynamicInvoker(cs);
        final Integer[] x = new Integer[] { 3, 2, 1 };
        final String[] y = new String[] { "a", "b", "c" };
        assertEquals(3, invoker.invokeVarargs(x, 0)); 
        assertEquals(2, invoker.invokeVarargs(x, 1)); 
        assertEquals(1, invoker.invokeVarargs(x, 2));
        assertEquals("a", invoker.invokeVarargs(y, 0)); 
        assertEquals("b", invoker.invokeVarargs(y, 1)); 
        assertEquals("c", invoker.invokeVarargs(y, 2));
        // All are assignable from Object[]
        assertEquals(1, cs.getRelinkCount());
    }

    public void testEarlyBoundListElementGetter() throws Throwable {
        final RelinkCountingCallSite cs = new RelinkCountingCallSite(
                "dyn:getElem", MethodType.methodType(Object.class, List.class, 
                        int.class));
        new DynamicLinkerFactory().createLinker().link(cs);
        final MethodHandle invoker = MethodHandles.dynamicInvoker(cs);
        final List<Integer> x = new ArrayList<Integer>(Arrays.asList(new Integer[] { 3, 2, 1 }));
        final List<String> y = new LinkedList<String>(Arrays.asList(new String[] { "a", "b", "c" }));
        assertEquals(3, invoker.invokeVarargs(x, 0)); 
        assertEquals(2, invoker.invokeVarargs(x, 1)); 
        assertEquals(1, invoker.invokeVarargs(x, 2));
        assertEquals("a", invoker.invokeVarargs(y, 0)); 
        assertEquals("b", invoker.invokeVarargs(y, 1)); 
        assertEquals("c", invoker.invokeVarargs(y, 2));
        // All are assignable from List
        assertEquals(1, cs.getRelinkCount());
    }

    public void testEarlyBoundMapElementGetter() throws Throwable {
        final RelinkCountingCallSite cs = new RelinkCountingCallSite(
                "dyn:getElem", MethodType.methodType(Object.class, Map.class, 
                        Object.class));
        new DynamicLinkerFactory().createLinker().link(cs);
        final MethodHandle invoker = MethodHandles.dynamicInvoker(cs);
        final Map<Integer, Integer> x = new HashMap<Integer, Integer>();
        x.put(0, 3);
        x.put(1, 2);
        x.put(2, 1);
        final Map<String, String> y = new TreeMap<String, String>();
        y.put("a", "x");
        y.put("b", "y");
        y.put("c", "z");
        assertEquals(3, invoker.invokeVarargs(x, 0)); 
        assertEquals(2, invoker.invokeVarargs(x, 1)); 
        assertEquals(1, invoker.invokeVarargs(x, 2));
        assertEquals("x", invoker.invokeVarargs(y, "a")); 
        assertEquals("y", invoker.invokeVarargs(y, "b")); 
        assertEquals("z", invoker.invokeVarargs(y, "c"));
        // All are assignable from Map
        assertEquals(1, cs.getRelinkCount());
    }
    
    public void testLateBoundMapElementGetter() throws Throwable {
        final RelinkCountingCallSite cs = new RelinkCountingCallSite(
                "dyn:getElem", MethodType.methodType(Object.class, Object.class, 
                        Object.class));
        new DynamicLinkerFactory().createLinker().link(cs);
        final MethodHandle invoker = MethodHandles.dynamicInvoker(cs);
        final Map<Integer, Integer> x = new HashMap<Integer, Integer>();
        x.put(0, 3);
        x.put(1, 2);
        x.put(2, 1);
        final Map<String, String> y = new TreeMap<String, String>();
        y.put("a", "x");
        y.put("b", "y");
        y.put("c", "z");
        final List<String> z = new LinkedList<String>(Arrays.asList(new String[] { "a", "b", "c" }));
        assertEquals(3, invoker.invokeVarargs(x, 0)); 
        assertEquals(2, invoker.invokeVarargs(x, 1)); 
        assertEquals(1, invoker.invokeVarargs(x, 2));
        assertEquals("x", invoker.invokeVarargs(y, "a")); 
        assertEquals("y", invoker.invokeVarargs(y, "b")); 
        assertEquals("z", invoker.invokeVarargs(y, "c"));
        // All are assignable from Map
        assertEquals(1, cs.getRelinkCount());

        assertEquals("a", invoker.invokeVarargs(z, 0)); 
        assertEquals("b", invoker.invokeVarargs(z, 1)); 
        assertEquals("c", invoker.invokeVarargs(z, 2));
        // Had to relink from Map to List
        assertEquals(2, cs.getRelinkCount());
        
        final String[] w = new String[] { "a", "b", "c" };
        assertEquals("a", invoker.invokeVarargs(w, 0)); 
        assertEquals("b", invoker.invokeVarargs(w, 1)); 
        assertEquals("c", invoker.invokeVarargs(w, 2));
        // Had to relink from List to array
        assertEquals(3, cs.getRelinkCount());
    }
}
