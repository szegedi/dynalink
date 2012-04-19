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
    public void setRelink(MethodHandle relink) {
        if(relink == null) {
            throw new IllegalArgumentException("relink == null");
        }
        if(getTarget() != null) {
            throw new IllegalStateException("target already set");
        }
        // Set it as the initial target
        setTarget(relink);
    }

    @Override
    public CallSiteDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public CallSiteDescriptor getCallSiteDescriptor() {
        return getDescriptor();
    }
}