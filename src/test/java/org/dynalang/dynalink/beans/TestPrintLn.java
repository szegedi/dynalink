package org.dynalang.dynalink.beans;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.LinkedList;

import org.dynalang.dynalink.CallSiteDescriptor;
import org.dynalang.dynalink.linker.GuardingTypeConverterFactory;
import org.dynalang.dynalink.linker.LinkRequest;
import org.dynalang.dynalink.linker.LinkerServices;
import org.dynalang.dynalink.support.CallSiteDescriptorFactory;
import org.dynalang.dynalink.support.LinkRequestImpl;
import org.dynalang.dynalink.support.LinkerServicesImpl;
import org.dynalang.dynalink.support.TypeConverterFactory;

import junit.framework.TestCase;

public class TestPrintLn extends TestCase {
    /**
     * Regression test for a bug with selecting PrintStream.println() overloads.
     */
    public void testPrintLn() throws Throwable {
        final BeansLinker linker = new BeansLinker();
        final LinkerServices linkerServices =
                new LinkerServicesImpl(new TypeConverterFactory(new LinkedList<GuardingTypeConverterFactory>()), linker);
        final Object out = System.out;
        final CallSiteDescriptor desc = CallSiteDescriptorFactory.create(MethodHandles.publicLookup(),
                "dyn:callPropWithThis:println", MethodType.methodType(Object.class, Object.class, Object.class));
        final LinkRequest req = new LinkRequestImpl(desc, out, "helloWorld");
        linker.getGuardedInvocation(req, linkerServices).getInvocation().invokeWithArguments(out, "helloWorld");
    }
}
