package org.dynalang.dynalink.support;

import org.dynalang.dynalink.CallSiteDescriptor;
import org.dynalang.dynalink.LinkRequest;

/**
 * A link request implementation for call sites that pass language runtime
 * specific context arguments on the stack.
 * @author Attila Szegedi
 * @version $Id: $
 */
public class RuntimeContextLinkRequestImpl extends LinkRequestImpl {

    private final int runtimeContextArgCount;
    private LinkRequestImpl contextStrippedRequest;

    /**
     * Creates a new link request.
     * @param callSiteDescriptor the descriptor for the call site being linked
     * @param arguments the arguments for the invocation
     * @param runtimeContextArgCount the number of the trailing arguments on the
     * stack that represent the language runtime specific context arguments.
     */
    public RuntimeContextLinkRequestImpl(CallSiteDescriptor callSiteDescriptor,
        Object[] arguments, int runtimeContextArgCount) {
      super(callSiteDescriptor, arguments);
      this.runtimeContextArgCount = runtimeContextArgCount;
    }

    @Override
    public LinkRequest withoutRuntimeContext() {
        if(contextStrippedRequest == null) {
            contextStrippedRequest = new LinkRequestImpl(
                getCallSiteDescriptor().dropArguments(runtimeContextArgCount),
                getTruncatedArguments());
        }
        return contextStrippedRequest;
    }

    private Object[] getTruncatedArguments() {
      final Object[] args = getArguments();
      final Object[] newargs = new Object[args.length - runtimeContextArgCount];
      System.arraycopy(args, 0, newargs, 0, newargs.length);
      return newargs;
    }
}