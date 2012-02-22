package org.dynalang.dynalink.beans;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import junit.framework.TestCase;

import org.dynalang.dynalink.DynamicLinkerFactory;

public class TestElementSetter extends TestCase {
    public void testEarlyBoundPrimitiveArrayElementSetter() throws Throwable {
        final RelinkCountingCallSite cs = new RelinkCountingCallSite("dyn:setElem", MethodType.methodType(Void.TYPE,
                int[].class, int.class, int.class));
        new DynamicLinkerFactory().createLinker().link(cs);
        final MethodHandle invoker = cs.dynamicInvoker();
        final int[] x = new int[] { 0, 0, 0 };
        invoker.invokeWithArguments(x, 0, 3);
        invoker.invokeWithArguments(x, 1, 2);
        invoker.invokeWithArguments(x, 2, 1);
        assertTrue(Arrays.equals(new int[] { 3, 2, 1}, x));
    }

    public void testEarlyBoundObjectArrayElementSetter() throws Throwable {
        final RelinkCountingCallSite cs = new RelinkCountingCallSite("dyn:setElem", MethodType.methodType(Void.TYPE,
                Object[].class, int.class, Object.class));
        new DynamicLinkerFactory().createLinker().link(cs);
        final MethodHandle invoker = cs.dynamicInvoker();
        final Integer[] x = new Integer[] { 0, 0, 0 };
        final String[] y = new String[] { "x", "y", "z" };
        invoker.invokeWithArguments(x, 0, 3);
        invoker.invokeWithArguments(x, 1, 2);
        invoker.invokeWithArguments(x, 2, 1);
        invoker.invokeWithArguments(y, 0, "a");
        invoker.invokeWithArguments(y, 1, "b");
        invoker.invokeWithArguments(y, 2, "c");
        assertTrue(Arrays.equals(new Integer[] { 3, 2, 1}, x));
        assertTrue(Arrays.equals(new String[] { "a", "b", "c"}, y));
        // All are assignable from Object[]
        assertEquals(1, cs.getRelinkCount());
    }

    public void testEarlyBoundListElementSetter() throws Throwable {
        final RelinkCountingCallSite cs = new RelinkCountingCallSite("dyn:setElem", MethodType.methodType(Void.TYPE,
                List.class, int.class, Object.class));
        new DynamicLinkerFactory().createLinker().link(cs);
        final MethodHandle invoker = cs.dynamicInvoker();
        final List<Integer> x = Arrays.asList(new Integer[] { 0, 0, 0 });
        final List<String> y = Arrays.asList(new String[] { "x", "y", "z" });
        invoker.invokeWithArguments(x, 0, 3);
        invoker.invokeWithArguments(x, 1, 2);
        invoker.invokeWithArguments(x, 2, 1);
        invoker.invokeWithArguments(y, 0, "a");
        invoker.invokeWithArguments(y, 1, "b");
        invoker.invokeWithArguments(y, 2, "c");
        assertTrue(Arrays.equals(new Integer[] { 3, 2, 1}, x.toArray()));
        assertTrue(Arrays.equals(new String[] { "a", "b", "c"}, y.toArray()));
        // All are assignable from List
        assertEquals(1, cs.getRelinkCount());
    }

    public void testEarlyBoundMapElementSetter() throws Throwable {
        final RelinkCountingCallSite cs = new RelinkCountingCallSite("dyn:setElem", MethodType.methodType(Void.TYPE,
                Map.class, Object.class, Object.class));
        new DynamicLinkerFactory().createLinker().link(cs);
        final MethodHandle invoker = cs.dynamicInvoker();
        final Map<Integer, Integer> x = new HashMap<Integer, Integer>();
        final Map<String, String> y = new TreeMap<String, String>();
        invoker.invokeWithArguments(x, 0, 3);
        invoker.invokeWithArguments(x, 1, 2);
        invoker.invokeWithArguments(x, 2, 1);
        invoker.invokeWithArguments(y, "a", "x");
        invoker.invokeWithArguments(y, "b", "y");
        invoker.invokeWithArguments(y, "c", "z");
        // TODO: use Java 7 Map literals once Eclipse learns them
        final Map<Integer, Integer> x1 = new HashMap<Integer, Integer>();
        x1.put(0, 3);
        x1.put(1, 2);
        x1.put(2, 1);
        final Map<String, String> y1 = new TreeMap<String, String>();
        y1.put("a", "x");
        y1.put("b", "y");
        y1.put("c", "z");
        assertEquals(x1, x);
        assertEquals(y1, y);
        // All are assignable from Map
        assertEquals(1, cs.getRelinkCount());
    }

    public void testLateBoundMapElementSetter() throws Throwable {
        final RelinkCountingCallSite cs =
                new RelinkCountingCallSite("dyn:setElem", MethodType.methodType(Void.TYPE, Object.class, Object.class,
                        Object.class));
        new DynamicLinkerFactory().createLinker().link(cs);
        final MethodHandle invoker = cs.dynamicInvoker();
        final Map<Integer, Integer> x = new HashMap<Integer, Integer>();
        final Map<String, String> y = new TreeMap<String, String>();
        invoker.invokeWithArguments(x, 0, 3);
        invoker.invokeWithArguments(x, 1, 2);
        invoker.invokeWithArguments(x, 2, 1);
        invoker.invokeWithArguments(y, "a", "x");
        invoker.invokeWithArguments(y, "b", "y");
        invoker.invokeWithArguments(y, "c", "z");
        // TODO: use Java 7 Map literals once Eclipse learns them
        final Map<Integer, Integer> x1 = new HashMap<Integer, Integer>();
        x1.put(0, 3);
        x1.put(1, 2);
        x1.put(2, 1);
        final Map<String, String> y1 = new TreeMap<String, String>();
        y1.put("a", "x");
        y1.put("b", "y");
        y1.put("c", "z");
        assertEquals(x1, x);
        assertEquals(y1, y);
        // All are assignable from Map
        assertEquals(1, cs.getRelinkCount());

        final List<String> z = Arrays.asList(new String[] { "x", "y", "z" });
        invoker.invokeWithArguments(z, 0, "a");
        invoker.invokeWithArguments(z, 1, "b");
        invoker.invokeWithArguments(z, 2, "c");

        assertTrue(Arrays.equals(new String[] { "a", "b", "c"}, z.toArray()));
        // Had to relink from Map to List
        assertEquals(2, cs.getRelinkCount());

        final String[] w = new String[] { "x", "y", "z" };
        invoker.invokeWithArguments(w, 0, "a");
        invoker.invokeWithArguments(w, 1, "b");
        invoker.invokeWithArguments(w, 2, "c");
        assertTrue(Arrays.equals(new String[] { "a", "b", "c"}, w));
        // Had to relink from List to array
        assertEquals(3, cs.getRelinkCount());
    }
}