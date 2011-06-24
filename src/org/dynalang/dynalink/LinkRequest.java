package org.dynalang.dynalink;

/**
 * Represents a request to link a particular invocation at a particular call
 * site. Instances of these requests are being passed to
 * {@link GuardingDynamicLinker}.
 * @author Attila Szegedi
 * @version $Id: $
 */
public interface LinkRequest {
    /**
     * Returns the call site descriptor for the call site being linked.
     * @return the call site descriptor for the call site being linked.
     */
    public CallSiteDescriptor getCallSiteDescriptor();

    /**
     * Returns the arguments for the invocation being linked.
     * @return the arguments for the invocation being linked.
     */
    public Object[] getArguments();

    /**
     * Returns a non-native link request. Some language runtimes will include
     * runtime-specific context parameters in their call sites as the last few
     * arguments. If a linker does not recognize such contexts at all, or does
     * not recognize the call site as one with its own context, it can ask for
     * the alternative link request with context parameters and arguments
     * removed, and link against it instead.
     * @return the non-native request. If the call site for this request does
     * not have any language runtime specific context parameters, the same link
     * request is returned.
     */
    public LinkRequest asNonNative();
}