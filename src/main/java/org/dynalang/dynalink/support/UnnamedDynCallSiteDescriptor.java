package org.dynalang.dynalink.support;

import java.lang.invoke.MethodType;

import org.dynalang.dynalink.CallSiteDescriptor;

class UnnamedDynCallSiteDescriptor extends AbstractCallSiteDescriptor {
    private final MethodType methodType;
    private final String op;

    UnnamedDynCallSiteDescriptor(String op, MethodType methodType) {
        this.op = op;
        this.methodType = methodType;
    }

    @Override
    public int getNameTokenCount() {
        return 2;
    }

    String getOp() {
        return op;
    }

    @Override
    public String getNameToken(int i) {
        switch(i) {
            case 0: return "dyn";
            case 1: return op;
            default: throw new IndexOutOfBoundsException(String.valueOf(i));
        }
    }

    @Override
    public MethodType getMethodType() {
        return methodType;
    }

    @Override
    public CallSiteDescriptor dropParameterTypes(int from, int to) {
        return CallSiteDescriptorFactory.getCanonicalPublicDescriptor(new UnnamedDynCallSiteDescriptor(op,
                methodType.dropParameterTypes(from, to)));
    }

    @Override
    public CallSiteDescriptor changeParameterType(int num, Class<?> newType) {
        return CallSiteDescriptorFactory.getCanonicalPublicDescriptor(new UnnamedDynCallSiteDescriptor(op,
                methodType.changeParameterType(num, newType)));
    }
}
