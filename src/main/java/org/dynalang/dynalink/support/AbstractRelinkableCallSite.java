package org.dynalang.dynalink.support;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MutableCallSite;

import org.dynalang.dynalink.CallSiteDescriptor;
import org.dynalang.dynalink.RelinkableCallSite;

/**
 * A basic implementation of the {@link RelinkableCallSite} as a {@link MutableCallSite} subclass.
 *
 * @author Attila Szegedi
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
    public void initialize(MethodHandle relinkAndInvoke) {
        setTarget(relinkAndInvoke);
    }
}