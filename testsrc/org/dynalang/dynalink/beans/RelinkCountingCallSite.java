package org.dynalang.dynalink.beans;

import java.lang.invoke.MethodType;

import org.dynalang.dynalink.GuardedInvocation;
import org.dynalang.dynalink.MonomorphicCallSite;

public class RelinkCountingCallSite extends MonomorphicCallSite {
    private int relinkCount;

    public RelinkCountingCallSite(String name, MethodType type) {
        super(name, type);
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
