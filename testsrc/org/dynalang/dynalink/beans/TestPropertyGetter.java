package org.dynalang.dynalink.beans;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

import junit.framework.TestCase;

import org.dynalang.dynalink.DynamicLinkerFactory;

/**
 * @author Attila Szegedi
 * @version $Id: $
 */
public class TestPropertyGetter extends TestCase
{
    public void testFixedNamePropertyGetter() throws Throwable
    {
        final RelinkCountingCallSite callSite = new RelinkCountingCallSite(
                "dyn:getProp:foo", MethodType.methodType(Object.class, Object.class));
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
    }

    public void testVariableNamePropertyGetter() throws Throwable
    {
        final RelinkCountingCallSite callSite = new RelinkCountingCallSite(
                "dyn:getProp", MethodType.methodType(Object.class, Object.class,
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

    public static class T1
    {
        private Object foo;

        public void setFoo(String foo)
        {
            this.foo = foo;
        }

        public Object getFoo()
        {
            return foo;
        }
    }

    public static class T2
    {
        private Object foo;
        private Object bar;

        public void setFoo(Object foo)
        {
            this.foo = foo;
        }

        public Object getFoo()
        {
            return foo;
        }

        public void setBar(Object bar)
        {
            this.bar = bar;
        }

        public Object getBar()
        {
            return bar;
        }
    }

    public static class T3 extends T1
    {
        private Object bar;

        public void setBar(Object bar)
        {
            this.bar = bar;
        }

        public Object getBar()
        {
            return bar;
        }
    }
}
