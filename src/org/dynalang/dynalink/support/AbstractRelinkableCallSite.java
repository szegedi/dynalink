package org.dynalang.dynalink.support;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.lang.invoke.SwitchPoint;

import org.dynalang.dynalink.CallSiteDescriptor;
import org.dynalang.dynalink.GuardedInvocation;
import org.dynalang.dynalink.RelinkableCallSite;

/**
 * A basic implementation of the {@link RelinkableCallSite} as a
 * {@link MutableCallSite} subclass.
 * 
 * @author Attila Szegedi
 * @version $Id: $
 */
public abstract class AbstractRelinkableCallSite extends MutableCallSite
        implements RelinkableCallSite {
    private MethodHandle relink;
    private final CallSiteDescriptor callSiteDescriptor;

    /**
     * Creates a new relinkable call site.
     * 
     * @param name
     *            the name of the method at the call site
     * @param type
     *            the method type of the call site
     */
    protected AbstractRelinkableCallSite(String name, MethodType type) {
        super(type);
        callSiteDescriptor = new CallSiteDescriptor(name, type);
    }

    @Override
    public void setRelink(MethodHandle relink) {
        if(relink == null) {
            throw new IllegalArgumentException("relink == null");
        }
        if(this.relink != null) {
            throw new IllegalStateException("this.relink already set");
        }
        this.relink = relink;
        // Set it as the initial target
        setTarget(relink);
    }

    /**
     * Returns the relink method handle
     * 
     * @return the method handle for relinking this call site.
     */
    protected MethodHandle getRelink() {
        return relink;
    }

    @Override
    public CallSiteDescriptor getCallSiteDescriptor() {
        return callSiteDescriptor;
    }

    @Override
    public void setGuardedInvocation(GuardedInvocation guardedInvocation) {
        if(guardedInvocation == null) {
            throw new IllegalArgumentException("guardedInvocation == null");
        }
        final MethodHandle guard = guardedInvocation.getGuard();
        MethodHandle invocation = guardedInvocation.getInvocation();
        final SwitchPoint switchPoint = guardedInvocation.getSwitchPoint();
        if(switchPoint != null) {
            invocation = switchPoint.guardWithTest(invocation,
                    getSwitchPointFallback());
        }
        try {
            setTarget(guard == null ? invocation : MethodHandles.guardWithTest(
                    guard, invocation, getGuardFallback()));
        } catch(IllegalArgumentException e) {
            // Provide more information than the default JDK implementation
            throw new IllegalArgumentException("invocation and guard types "
                    + "do not match. invocation=" + invocation.type()
                    + " guard=" + guard.type(), e);
        }
    }

    /**
     * Implement this method to specify the fallback method handle when a guard
     * fails.
     * 
     * @return the fallback method handle
     */
    protected abstract MethodHandle getGuardFallback();

    /**
     * Implement this method to specify the fallback method handle when a method
     * handle is invalidated by a switch point.
     * 
     * @return the fallback method handle for switch point invalidation.
     */
    protected abstract MethodHandle getSwitchPointFallback();
}