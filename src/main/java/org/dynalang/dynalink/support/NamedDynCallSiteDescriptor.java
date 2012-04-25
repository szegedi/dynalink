package org.dynalang.dynalink.support;

import java.lang.invoke.MethodType;

import org.dynalang.dynalink.linker.CallSiteDescriptor;

class NamedDynCallSiteDescriptor extends AbstractCallSiteDescriptor {
    private final MethodType methodType;
    private final String op;
    private final String name;

    NamedDynCallSiteDescriptor(String op, String name, MethodType methodType) {
        this.op = op;
        this.name = name;
        this.methodType = methodType;
    }

    @Override
    public int getNameTokenCount() {
        return 3;
    }

    @Override
    public String getNameToken(int i) {
        switch(i) {
            case 0: return "dyn";
            case 1: return op;
            case 2: return name;
            default: throw new IndexOutOfBoundsException(String.valueOf(i));
        }
    }

    @Override
    public MethodType getMethodType() {
        return methodType;
    }

    @Override
    public CallSiteDescriptor dropParameterTypes(int from, int to) {
        return CallSiteDescriptorFactory.getCanonicalPublicDescriptor(new NamedDynCallSiteDescriptor(op, name,
                methodType.dropParameterTypes(from, to)));
    }

    @Override
    public CallSiteDescriptor changeParameterType(int num, Class<?> newType) {
        return CallSiteDescriptorFactory.getCanonicalPublicDescriptor(new NamedDynCallSiteDescriptor(op, name,
                methodType.changeParameterType(num, newType)));
    }
}
