/*
   Copyright 2009 Attila Szegedi

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package org.dynalang.dynalink;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.invoke.SwitchPoint;
import java.lang.invoke.WrongMethodTypeException;

import org.dynalang.dynalink.support.Guards;

/**
 * A triple of an invocation method handle, a guard method handle that
 * defines the validity of the invocation, and a switchpoint that can be used
 * for external invalidation of the linkage. The method handle is suitable for
 * invocation at a particular call site for particular arguments, and might be
 * used for subsequent invocations as long as the guard condition is fulfilled.
 * If the guard condition fails or the switchpoint is invalidated, the runtime
 * will relink the call site. Both the guard and the switchpoint are optional,
 * neither, one, or both can be present.
 * @author Attila Szegedi
 * @version $Id: $
 */
public class GuardedInvocation
{
    private final MethodHandle invocation;
    private final MethodHandle guard;
    private final SwitchPoint switchPoint;

    /**
     * Creates a new guarded invocation.
     * @param invocation the method handle representing the invocation. Must
     * not be null.
     * @param guard the method handle representing the guard. Must have the
     * same method type as the invocation, except it must return boolean. For
     * some useful guards, check out the {@link Guards} class. It can be null
     * to represent an unconditional invocation, although this is not really
     * expected to ever be the case.
     * @throws IllegalArgumentException if invocation is null.
     */
    public GuardedInvocation(MethodHandle invocation, MethodHandle guard) {
      this(invocation, guard, null);
    }

    /**
     * Creates a new guarded invocation.
     * @param invocation the method handle representing the invocation. Must
     * not be null.
     * @param guard the method handle representing the guard. Must have the
     * same method type as the invocation, except it must return boolean. For
     * some useful guards, check out the {@link Guards} class. It can be null
     * to represent an unconditional invocation, although this is not really
     * expected to ever be the case.
     * @param switchPoint the optional switch point that can be used to
     * invalidate this linkage.
     * @throws IllegalArgumentException if invocation is null.
     */
    public GuardedInvocation(MethodHandle invocation, MethodHandle guard,
        SwitchPoint switchPoint) {
        if(invocation == null) {
            throw new IllegalArgumentException("invocation == null");
        }
        this.invocation = invocation;
        this.guard = guard;
        this.switchPoint = switchPoint;
    }

    /**
     * Returns the invocation method handle.
     * @return the invocation method handle. It will never be null.
     */
    public MethodHandle getInvocation() {
        return invocation;
    }

    /**
     * Returns the guard method handle.
     * @return the guard method handle. Can be null to signify an unconditional
     * invocation.
     */
    public MethodHandle getGuard() {
        return guard;
    }

    /**
     * Returns the switch point that can be used to invalidate this linkage.
     * @return the switch point that can be used to invalidate this linkage. Can
     * be null.
     */
    public SwitchPoint getSwitchPoint() {
        return switchPoint;
    }

    /**
     * Asserts that the invocation is of the specified type, and the guard (if
     * present) is of the specified type with a boolean return type.
     * @param type the asserted type
     * @throws WrongMethodTypeException if the invocation and the guard are not
     * of the expected method type.
     */
    public void assertType(MethodType type) {
        assertType(invocation, type);
        assertType(guard, type.changeReturnType(Boolean.TYPE));
    }

    /**
     * Creates a new guarded invocation with different methods, preserving the
     * switch point.
     * @param invocation the new invocation
     * @param guard the new guard
     * @return a new guarded invocation with the replaced methods and the same
     * switch point as this invocation.
     */
    public GuardedInvocation replaceMethods(MethodHandle invocation,
        MethodHandle guard) {
        return new GuardedInvocation(invocation, guard, switchPoint);
    }

    private static void assertType(MethodHandle mh, MethodType type) {
        if(!mh.type().equals(type)) {
            throw new WrongMethodTypeException("Expected type: " + type +
                    " actual type: " + mh.type());
        }
    }
}