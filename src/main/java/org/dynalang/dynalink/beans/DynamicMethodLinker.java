package org.dynalang.dynalink.beans;

import org.dynalang.dynalink.CallSiteDescriptor;
import org.dynalang.dynalink.linker.GuardedInvocation;
import org.dynalang.dynalink.linker.LinkRequest;
import org.dynalang.dynalink.linker.LinkerServices;
import org.dynalang.dynalink.linker.TypeBasedGuardingDynamicLinker;
import org.dynalang.dynalink.support.Guards;

/**
 * Simple linker that implements the "dyn:call" operation for {@link DynamicMethod} objects - the objects returned by
 * "dyn:getMethod" from {@link AbstractJavaLinker}.
 */
class DynamicMethodLinker implements TypeBasedGuardingDynamicLinker {
    @Override
    public boolean canLinkType(Class<?> type) {
        return DynamicMethod.class.isAssignableFrom(type);
    };

    @Override
    public GuardedInvocation getGuardedInvocation(LinkRequest linkRequest, LinkerServices linkerServices) {
        final Object receiver = linkRequest.getReceiver();
        if(!(receiver instanceof DynamicMethod)) {
            return null;
        }
        final CallSiteDescriptor desc = linkRequest.getCallSiteDescriptor();
        if(desc.getNameTokenCount() == 2 && desc.getNameToken(CallSiteDescriptor.SCHEME) == "dyn" &&
                desc.getNameToken(CallSiteDescriptor.OPERATOR) == "call") {
            return new GuardedInvocation(((DynamicMethod)receiver).getInvocation(desc.getMethodType(), linkerServices),
                    Guards.getIdentityGuard(receiver));
        }
        return null;
    }
}
