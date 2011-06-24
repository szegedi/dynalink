package org.dynalang.dynalink.support;

import org.dynalang.dynalink.CallSiteDescriptor;
import org.dynalang.dynalink.LinkRequest;

/**
 * A link request implementation for call sites that pass language runtime
 * specific native context arguments on the stack.
 * @author Attila Szegedi
 * @version $Id: $
 */
public class NativeContextLinkRequestImpl extends LinkRequestImpl {

    private final int nativeContextArgCount;
    private LinkRequestImpl nonNativeRequest;

    /**
     * Creates a new link request.
     * @param callSiteDescriptor the descriptor for the call site being linked
     * @param arguments the arguments for the invocation
     * @param nativeContextArgCount the number of the trailing arguments on the
     * stack that represent the language runtime specific context arguments.
     */
    public NativeContextLinkRequestImpl(CallSiteDescriptor callSiteDescriptor,
        Object[] arguments, int nativeContextArgCount) {
      super(callSiteDescriptor, arguments);
      this.nativeContextArgCount = nativeContextArgCount;
    }

    @Override
    public LinkRequest getNonNativeRequest() {
        if(nonNativeRequest == null) {
            nonNativeRequest = new LinkRequestImpl(
                getCallSiteDescriptor().dropArguments(nativeContextArgCount),
                getTruncatedArguments());
        }
        return nonNativeRequest;
    }

    private Object[] getTruncatedArguments() {
      final Object[] args = getArguments();
      final Object[] newargs = new Object[args.length - nativeContextArgCount];
      System.arraycopy(args, 0, newargs, 0, newargs.length);
      return newargs;
    }
}