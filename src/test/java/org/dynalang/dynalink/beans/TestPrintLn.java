package org.dynalang.dynalink.beans;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.dynalang.dynalink.CallSiteDescriptor;
import org.dynalang.dynalink.LinkerServicesFactory;
import org.dynalang.dynalink.linker.LinkRequest;
import org.dynalang.dynalink.linker.LinkerServices;
import org.dynalang.dynalink.support.CallSiteDescriptorFactory;
import org.dynalang.dynalink.support.LinkRequestImpl;

import junit.framework.TestCase;

public class TestPrintLn extends TestCase {
    /**
     * Regression test for a bug with selecting PrintStream.println() overloads.
     */
    public void testPrintLn() throws Throwable {
        final BeansLinker linker = new BeansLinker();
        final LinkerServices linkerServices = LinkerServicesFactory.getLinkerServices(linker);
        final Object out = System.out;
        final CallSiteDescriptor desc = CallSiteDescriptorFactory.create(MethodHandles.publicLookup(),
                "dyn:callMethod:println", MethodType.methodType(Object.class, Object.class, Object.class));
        final LinkRequest req = new LinkRequestImpl(desc, false, out, "helloWorld");
        linker.getGuardedInvocation(req, linkerServices).getInvocation().invokeWithArguments(out, "helloWorld");
    }
}
