package org.dynalang.dynalink.support;

import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;

import org.dynalang.dynalink.CallSiteDescriptor;

/**
 * A default, fairly light implementation of a call site descriptor used for describing non-standard operations. It does
 * not store {@link Lookup} objects but always returns the public lookup from its {@link #getLookup()} method. If you
 * need to support non-public lookup, you can use {@link LookupCallSiteDescriptor}.
 * @author Attila Szegedi
 */
class DefaultCallSiteDescriptor extends AbstractCallSiteDescriptor {

    private final String[] tokenizedName;
    private final MethodType methodType;

    DefaultCallSiteDescriptor(String[] tokenizedName, MethodType methodType) {
        this.tokenizedName = tokenizedName;
        this.methodType = methodType;
    }

    @Override
    public int getNameTokenCount() {
        return tokenizedName.length;
    }

    @Override
    public String getNameToken(int i) {
        try {
            return tokenizedName[i];
        } catch(ArrayIndexOutOfBoundsException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    String[] getTokenizedName() {
        return tokenizedName;
    }

    @Override
    public MethodType getMethodType() {
        return methodType;
    }

    @Override
    public CallSiteDescriptor changeMethodType(MethodType newMethodType) {
        return CallSiteDescriptorFactory.getCanonicalPublicDescriptor(new DefaultCallSiteDescriptor(tokenizedName,
                newMethodType));
    }
}
