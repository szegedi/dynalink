package org.dynalang.dynalink;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicReference;

import org.dynalang.dynalink.linker.CallSiteDescriptor;
import org.dynalang.dynalink.linker.GuardedInvocation;
import org.dynalang.dynalink.support.AbstractRelinkableCallSite;

/**
 * A relinkable call site that maintains a chain of linked method handles (up to at most 8 in the default
 * implementation) cascading from one to the other through
 * {@link MethodHandles#guardWithTest(MethodHandle, MethodHandle, MethodHandle)}. When it has to link a new method
 * handle and the length of the chain is already at the maximum, it will throw away the oldest method handle.
 * Switchpoint-invalidated handles in the chain are removed fairly eagerly (on each linking request, and whenever a
 * switchpoint-protected method handle falls back during chain traversal on invocation). There is currently no profiling
 * attached to the handles in the chain, so they are never reordered based on usage; the most recently linked method
 * handle is always at the start of the chain.
 */
public class ChainedCallSite extends AbstractRelinkableCallSite {
    private final AtomicReference<LinkedList<GuardedInvocation>> invocations = new AtomicReference<>();

    /**
     * Creates a new chained call site.
     * @param descriptor the descriptor for the call site.
     */
    public ChainedCallSite(CallSiteDescriptor descriptor) {
        super(descriptor);
    }

    /**
     * The maximum number of method handles in the chain. Defaults to 8. You can override it in a subclass if you need
     * to change the value. If your override returns a value less than 1, the code will break.
     * @return the maximum number of method handles in the chain.
     */
    @SuppressWarnings("static-method")
    protected int getMaxChainLength() {
        return 8;
    }

    @Override
    public void setGuardedInvocation(GuardedInvocation invocation, MethodHandle relink) {
        setGuardedInvocationInternal(invocation, relink);
    }

    private MethodHandle setGuardedInvocationInternal(GuardedInvocation invocation, MethodHandle relink) {
        final LinkedList<GuardedInvocation> currentInvocations = invocations.get();
        @SuppressWarnings("unchecked")
        final LinkedList<GuardedInvocation> newInvocations =
            currentInvocations == null ? new LinkedList() : (LinkedList)currentInvocations.clone();

        // First, prune the chain of invalidated switchpoints.
        for(Iterator<GuardedInvocation> it = newInvocations.iterator(); it.hasNext();) {
            if(it.next().hasBeenInvalidated()) {
                it.remove();
            }
        }

        // prune() is allowed to invoke this method with invocation == null meaning we're just pruning the chain and not
        // adding any new invocations to it.
        if(invocation != null) {
            // Remove oldest entry if we're at max length
            if(newInvocations.size() == getMaxChainLength()) {
                newInvocations.removeFirst();
            }
            newInvocations.addLast(invocation);
        }

        // prune-and-invoke is used as the fallback for invalidated switchpoints. If a switchpoint gets invalidated, we
        // rebuild the chain and get rid of all invalidated switchpoints instead of letting them linger.
        final MethodHandle pruneAndInvoke = makePruneAndInvokeMethod(relink);

        // Fold the new chain
        MethodHandle target = relink;
        for(GuardedInvocation inv: newInvocations) {
            target = inv.compose(pruneAndInvoke, target);
        }

        // If nobody else updated the call site while we were rebuilding the chain, set the target to our chain. In case
        // we lost the race for multithreaded update, just do nothing. Either the other thread installed the same thing
        // we wanted to install, or otherwise, we'll be asked to relink again.
        if(invocations.compareAndSet(currentInvocations, newInvocations)) {
            setTarget(target);
        }
        return target;
    }

    /**
     * Creates a method that rebuilds our call chain, pruning it of any invalidated switchpoints, and then invokes that
     * chain.
     * @param relink the ultimate fallback for the chain (the {@code DynamicLinker}'s relink).
     * @return a method handle for prune-and-invoke
     */
    private MethodHandle makePruneAndInvokeMethod(MethodHandle relink) {
        // Bind prune to (this, relink)
        final MethodHandle boundPrune = MethodHandles.insertArguments(PRUNE, 0, this, relink);
        // Make it ignore all incoming arguments
        final MethodHandle ignoreArgsPrune = MethodHandles.dropArguments(boundPrune, 0, type().parameterList());
        // Invoke prune, then invoke the call site target with original arguments
        return MethodHandles.foldArguments(MethodHandles.exactInvoker(type()), ignoreArgsPrune);
    }

    @SuppressWarnings("unused")
    private MethodHandle prune(MethodHandle relink) {
        return setGuardedInvocationInternal(null, relink);
    }

    private static final MethodHandle PRUNE;
    static {
        try {
            PRUNE = MethodHandles.lookup().findSpecial(ChainedCallSite.class, "prune", MethodType.methodType(
                    MethodHandle.class, MethodHandle.class), ChainedCallSite.class);
        // NOTE: using two catch blocks so we don't introduce a reference to 1.7 ReflectiveOperationException, allowing
        // Dynalink to be used on 1.6 JVMs with Remi's backport library.
        } catch(IllegalAccessException e) {
            throw new AssertionError(e.getMessage(), e); // Can not happen
        } catch(NoSuchMethodException e) {
            throw new AssertionError(e.getMessage(), e); // Can not happen
        }
    }
}