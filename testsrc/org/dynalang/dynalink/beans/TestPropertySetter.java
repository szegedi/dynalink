package org.dynalang.dynalink.beans;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import junit.framework.TestCase;

import org.dynalang.dynalink.DynamicLinkerFactory;

public class TestPropertySetter extends TestCase
{
    public void testFixedNamePropertySetter() throws Throwable
    {
        final RelinkCountingCallSite callSite = new RelinkCountingCallSite(
                "dyn:setProp:foo", MethodType.methodType(Void.TYPE, Object.class,
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

    public void testVariableNamePropertySetter() throws Throwable
    {
        final RelinkCountingCallSite callSite = new RelinkCountingCallSite(
                "dyn:setProp", MethodType.methodType(Void.TYPE, Object.class,
                        String.class, Object.class));
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

    public static class T1
    {
        private Object foo;

        public void setFoo(String foo)
        {
            this.foo = foo;
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

        public void setBar(Object... args)
        {
            this.bar = args[0];
        }

        public void setBar(int x)
        {
            this.bar = x;
        }
    }
}
