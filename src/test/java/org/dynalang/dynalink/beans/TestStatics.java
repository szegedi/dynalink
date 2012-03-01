package org.dynalang.dynalink.beans;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import junit.framework.TestCase;

import org.dynalang.dynalink.DynamicLinker;
import org.dynalang.dynalink.DynamicLinkerFactory;
import org.dynalang.dynalink.MonomorphicCallSite;
import org.dynalang.dynalink.linker.CallSiteDescriptor;

public class TestStatics extends TestCase {
    private static final DynamicLinker linker = new DynamicLinkerFactory().createLinker();

    public void testFieldGetterSetter() throws Throwable {
        final Object statics = getProperty("statics", T1.class);
        setProperty("foo", statics, "fooValue");
        assertEquals("fooValue", getProperty("foo", statics));
    }

    public void testPropertyGetterSetter() throws Throwable {
        final Object statics = getProperty("statics", T2.class);
        setProperty("foo", statics, "fooValue");
        assertTrue(T2.invokedSetFooString);
        assertEquals("fooValue", getProperty("foo", statics));
        setProperty("foo", statics, 31);
        assertTrue(T2.invokedSetFooInt);
        assertEquals("1f", getProperty("foo", statics));
        getInvoker("dyn:callPropWithThis:setFoo", Void.TYPE, Object.class, Object.class).invoke(statics, "blah");
        assertEquals("blah", getProperty("foo", statics));
    }

    private static void setProperty(String name, Object obj, Object value) throws Throwable {
        getInvoker("dyn:setProp:" + name, Void.TYPE, Object.class, Object.class).invoke(obj, value);
    }

    private static Object getProperty(String name, Object obj) throws Throwable {
        return getInvoker("dyn:getProp:" + name, Object.class, Object.class).invoke(obj);
    }

    private static MethodHandle getInvoker(String name, Class<?> retType, Class<?>... paramTypes) {
        final MonomorphicCallSite cs = new MonomorphicCallSite(CallSiteDescriptor.create(MethodHandles.publicLookup(),
                name, MethodType.methodType(retType, paramTypes)));
        linker.link(cs);
        return cs.dynamicInvoker();
    }

    public static class T1 {
        public static String foo;
    }

    public static class T2 {
        public static String foo;
        private static boolean invokedGetFoo = false;
        private static boolean invokedSetFooString = false;
        private static boolean invokedSetFooInt = false;

        public static String getFoo() {
            invokedGetFoo = true;
            return foo;
        }

        public static void setFoo(String foo) {
            invokedSetFooString = true;
            T2.foo = foo;
        }

        public static void setFoo(int foo) {
            invokedSetFooInt = true;
            T2.foo = Integer.toHexString(foo);
        }
    }
}
