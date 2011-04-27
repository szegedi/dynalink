package org.dynalang.dynalink.support;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodType;

import org.dynalang.dynalink.DynamicLinker;
import org.dynalang.dynalink.DynamicLinkerFactory;
import org.dynalang.dynalink.MonomorphicCallSite;
import org.dynalang.dynalink.RelinkableCallSite;

/**
 * A convenience default bootstrapper that exposes a static bootstrap method to
 * which language runtimes that need the very default behaviour can use with
 * minimal setup. When first referenced, it will create a 
 * @author Attila Szegedi
 * @version $Id: $
 */
public class DefaultBootstrapper {
    private static final DynamicLinker dynamicLinker = 
        new DynamicLinkerFactory().createLinker();
    
    public static CallSite bootstrap(Object caller, String name, 
            MethodType type)
    {
        final RelinkableCallSite callSite = new MonomorphicCallSite(name, type);
        dynamicLinker.link(callSite);
        return callSite;
    }
}
