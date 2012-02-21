package org.dynalang.dynalink.support;

import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;

import org.dynalang.dynalink.linker.CallSiteDescriptor;

/**
 * A call site descriptor that stores static bootstrap arguments. It does not, however, store {@link Lookup} objects but
 * always returns the public lookup from its {@link #getLookup()} method.
 * @author Attila Szegedi
 * @version $Id: $
 */
class ArgsCallSiteDescriptor extends DefaultCallSiteDescriptor {
    private Object[] args;

    /**
     * Create a new call site descriptor from explicit information.
     * @param tokenizedName the name of the method
     * @param methodType the method type
     * @param args the additional bootstrap arguments
     */
    ArgsCallSiteDescriptor(String[] tokenizedName, MethodType methodType, Object[] args) {
        super(tokenizedName, methodType);
        this.args = args;
    }

    @Override
    public Object getAdditionalBootstrapArgument(int i) {
        try {
            return args[i];
        } catch(ArrayIndexOutOfBoundsException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public int getAdditionalBootstrapArgumentCount() {
        return args.length;
    }

    Object[] getArgs() {
        return args;
    }

    @Override
    public CallSiteDescriptor dropParameterTypes(int from, int to) {
        return new ArgsCallSiteDescriptor(getTokenizedName(), getMethodType().dropParameterTypes(from, to), args);
    }

    @Override
    public CallSiteDescriptor changeParameterType(int num, Class<?> newType) {
        return new ArgsCallSiteDescriptor(getTokenizedName(), getMethodType().changeParameterType(num, newType), args);
    }
}
