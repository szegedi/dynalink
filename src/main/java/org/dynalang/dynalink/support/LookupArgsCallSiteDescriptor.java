package org.dynalang.dynalink.support;

import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;

import org.dynalang.dynalink.linker.CallSiteDescriptor;

/**
 * A call site descriptor that stores both a specific {@link Lookup} and static bootstrap arguments.
 * @author Attila Szegedi
 * @version $Id: $
 */
class LookupArgsCallSiteDescriptor extends ArgsCallSiteDescriptor {
    private Lookup lookup;
    /**
     * Create a new call site descriptor from explicit information.
     * @param tokenizedName the name of the method
     * @param methodType the method type
     * @param lookup the lookup
     * @param args additional bootstrap arguments
     */
    LookupArgsCallSiteDescriptor(String[] tokenizedName, MethodType methodType, Lookup lookup,
            Object[] args) {
        super(tokenizedName, methodType, args);
        this.lookup = lookup;
    }

    @Override
    public Lookup getLookup() {
        return lookup;
    }

    @Override
    public CallSiteDescriptor dropParameterTypes(int from, int to) {
        return new LookupArgsCallSiteDescriptor(getTokenizedName(),
                getMethodType().dropParameterTypes(from, to), lookup, getArgs());
    }

    @Override
    public CallSiteDescriptor changeParameterType(int num, Class<?> newType) {
        return new LookupArgsCallSiteDescriptor(getTokenizedName(),
                getMethodType().changeParameterType(num, newType), lookup, getArgs());
    }
}
