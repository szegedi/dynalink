package org.dynalang.dynalink.support;

import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;

import org.dynalang.dynalink.CallSiteDescriptor;

/**
 * A call site descriptor that stores a specific {@link Lookup}. It does not, however, store static bootstrap arguments.
 * @author Attila Szegedi
 */
class LookupCallSiteDescriptor extends DefaultCallSiteDescriptor {
    private Lookup lookup;

    /**
     * Create a new call site descriptor from explicit information.
     * @param tokenizedName the name of the method
     * @param methodType the method type
     * @param lookup the lookup
     */
    LookupCallSiteDescriptor(String[] tokenizedName, MethodType methodType, Lookup lookup) {
        super(tokenizedName, methodType);
        this.lookup = lookup;
    }

    @Override
    public Lookup getLookup() {
        return lookup;
    }

    @Override
    public CallSiteDescriptor changeMethodType(MethodType newMethodType) {
        return new LookupCallSiteDescriptor(getTokenizedName(), newMethodType, lookup);
    }
}
