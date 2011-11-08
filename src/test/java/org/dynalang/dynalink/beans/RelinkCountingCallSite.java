package org.dynalang.dynalink.beans;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.dynalang.dynalink.MonomorphicCallSite;
import org.dynalang.dynalink.linker.CallSiteDescriptor;
import org.dynalang.dynalink.linker.GuardedInvocation;

public class RelinkCountingCallSite extends MonomorphicCallSite {
    private int relinkCount;

    public RelinkCountingCallSite(String name, MethodType type) {
        super(CallSiteDescriptor.create(MethodHandles.publicLookup(), name, type));
    }

    @Override
    public void setGuardedInvocation(GuardedInvocation guardedInvocation) {
        super.setGuardedInvocation(guardedInvocation);
        ++relinkCount;
    }

    public int getRelinkCount() {
        return relinkCount;
    }
}
