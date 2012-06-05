package org.dynalang.dynalink.support;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MutableCallSite;

import org.dynalang.dynalink.RelinkableCallSite;
import org.dynalang.dynalink.linker.CallSiteDescriptor;

/**
 * A basic implementation of the {@link RelinkableCallSite} as a {@link MutableCallSite} subclass.
 *
 * @author Attila Szegedi
 * @version $Id: $
 */
public abstract class AbstractRelinkableCallSite extends MutableCallSite implements RelinkableCallSite {
    private final CallSiteDescriptor descriptor;

    /**
     * Creates a new relinkable call site.
     * @param descriptor the descriptor for this call site
     */
    protected AbstractRelinkableCallSite(CallSiteDescriptor descriptor) {
        super(descriptor.getMethodType());
        this.descriptor = descriptor;
    }

    @Override
    public CallSiteDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public void setRelinkAndInvoke(MethodHandle relinkAndInvoke) {
        setTarget(relinkAndInvoke);
    }
}