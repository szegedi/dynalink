package org.dynalang.dynalink.support;

import org.dynalang.dynalink.CallSiteDescriptor;
import org.dynalang.dynalink.LinkRequest;

/**
 * Default implementation of the {@link LinkRequest}, representing a link
 * request to a call site that passes no language runtime specific native
 * context arguments on the stack.
 * @author Attila Szegedi
 * @version $Id: $
 */
public class LinkRequestImpl implements LinkRequest {

    private final CallSiteDescriptor callSiteDescriptor;
    private final Object[] arguments;

    /**
     * Creates a new link request.
     * @param callSiteDescriptor the descriptor for the call site being linked
     * @param arguments the arguments for the invocation
     */
    public LinkRequestImpl(CallSiteDescriptor callSiteDescriptor, Object[] arguments) {
        this.callSiteDescriptor = callSiteDescriptor;
        this.arguments = arguments;
    }

    @Override
    public Object[] getArguments() {
        return arguments;
    }

    @Override
    public CallSiteDescriptor getCallSiteDescriptor() {
        return callSiteDescriptor;
    }

    @Override
    public LinkRequest asNonNative() {
        return this;
    }
}