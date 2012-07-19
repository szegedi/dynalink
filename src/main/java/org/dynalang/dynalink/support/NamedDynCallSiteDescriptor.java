package org.dynalang.dynalink.support;

import java.lang.invoke.MethodType;

import org.dynalang.dynalink.CallSiteDescriptor;

class NamedDynCallSiteDescriptor extends UnnamedDynCallSiteDescriptor {
    private final String name;

    NamedDynCallSiteDescriptor(String op, String name, MethodType methodType) {
        super(op, methodType);
        this.name = name;
    }

    @Override
    public int getNameTokenCount() {
        return 3;
    }

    @Override
    public String getNameToken(int i) {
        switch(i) {
            case 0: return "dyn";
            case 1: return getOp();
            case 2: return name;
            default: throw new IndexOutOfBoundsException(String.valueOf(i));
        }
    }

    @Override
    public CallSiteDescriptor dropParameterTypes(int from, int to) {
        return CallSiteDescriptorFactory.getCanonicalPublicDescriptor(new NamedDynCallSiteDescriptor(getOp(), name,
                getMethodType().dropParameterTypes(from, to)));
    }

    @Override
    public CallSiteDescriptor changeParameterType(int num, Class<?> newType) {
        return CallSiteDescriptorFactory.getCanonicalPublicDescriptor(new NamedDynCallSiteDescriptor(getOp(), name,
                getMethodType().changeParameterType(num, newType)));
    }
}
