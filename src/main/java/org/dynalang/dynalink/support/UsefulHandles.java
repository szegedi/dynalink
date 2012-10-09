package org.dynalang.dynalink.support;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

/**
 * Collection of generally useful method handles.
 */
public class UsefulHandles {
    /**
     * An {@code (Object)Z} method handle that returns {@code true} for any argument it receives. Basically a
     * {@code f(x) := true} constant function.
     */
    public static final MethodHandle RETURN_TRUE_DROP_ARG = createReturnBooleanDropArg(true);
    /**
     * An {@code (Object)Z} method handle that returns {@code false} for any argument it receives. Basically a
     * {@code f(x) := false} constant function.
     */
    public static final MethodHandle RETURN_FALSE_DROP_ARG = createReturnBooleanDropArg(false);

    /**
     * Returns an {@code (Object)Z} method handle that returns either {@code true} or {@code false} for any argument it
     * is invoked for. Basically a {@code f(x) := b} constant function.
     * @param b the boolean value to return
     * @return a method handle that returns a constant boolean for any argument it is invoked with.
     */
    public static MethodHandle returnBooleanDropArg(boolean b) {
        return b ? RETURN_TRUE_DROP_ARG : RETURN_FALSE_DROP_ARG;
    }

    @SuppressWarnings("boxing")
    private static MethodHandle createReturnBooleanDropArg(boolean b) {
        return MethodHandles.dropArguments(MethodHandles.constant(boolean.class, b), 0, Object.class);
    }
}
