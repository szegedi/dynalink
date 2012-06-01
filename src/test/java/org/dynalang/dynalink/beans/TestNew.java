package org.dynalang.dynalink.beans;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.dynalang.dynalink.DynamicLinker;
import org.dynalang.dynalink.DynamicLinkerFactory;
import org.dynalang.dynalink.MonomorphicCallSite;
import org.dynalang.dynalink.support.CallSiteDescriptorFactory;

import junit.framework.TestCase;

public class TestNew extends TestCase {
    private static final DynamicLinker linker = new DynamicLinkerFactory().createLinker();

    public void testAll() throws Throwable {
        test("a", "b", "String,String", ctor("a", "b"));
        test("a", null, "String", ctor("a"));
        test("a", "1f", "String,int", ctor("a", 31));
    }

    private static void test(String foo, String bar, String ctor, T1 obj) {
        assertEquals(foo, obj.foo);
        assertEquals(bar, obj.bar);
        assertEquals(ctor, obj.ctor);
    }

    private static T1 ctor(Object arg1) throws Throwable {
        return T1.class.cast(getInvoker("dyn:new", T1.class, StaticClass.class, Object.class).invoke(
                StaticClass.forClass(T1.class), arg1));
    }

    private static T1 ctor(Object arg1, Object arg2) throws Throwable {
        return T1.class.cast(getInvoker("dyn:new", T1.class, StaticClass.class, Object.class, Object.class).invoke(
                StaticClass.forClass(T1.class), arg1, arg2));
    }

    private static MethodHandle getInvoker(String name, Class<?> retType, Class<?>... paramTypes) {
        final MonomorphicCallSite cs = new MonomorphicCallSite(CallSiteDescriptorFactory.create(
                MethodHandles.publicLookup(), name, MethodType.methodType(retType, paramTypes)));
        linker.link(cs);
        return cs.dynamicInvoker();
    }

    public static class T1 {
        public final String foo;
        public final String bar;
        public final String ctor;

        public T1(String foo, String bar, String ctor) {
            this.foo = foo;
            this.bar = bar;
            this.ctor = ctor;
        }

        public T1(String foo, String bar) {
            this(foo, bar, "String,String");
        }

        public T1(String foo, int bar) {
            this(foo, Integer.toHexString(bar), "String,int");
        }

        public T1(String foo) {
            this(foo, null, "String");
        }
    }
}
