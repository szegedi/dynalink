/*
   Copyright 2009-2013 Attila Szegedi

   Licensed under both the Apache License, Version 2.0 (the "Apache License")
   and the BSD License (the "BSD License"), with licensee being free to
   choose either of the two at their discretion.

   You may not use this file except in compliance with either the Apache
   License or the BSD License.

   If you choose to use this file in compliance with the Apache License, the
   following notice applies to you:

       You may obtain a copy of the Apache License at

           http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing, software
       distributed under the License is distributed on an "AS IS" BASIS,
       WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
       implied. See the License for the specific language governing
       permissions and limitations under the License.

   If you choose to use this file in compliance with the BSD License, the
   following notice applies to you:

       Redistribution and use in source and binary forms, with or without
       modification, are permitted provided that the following conditions are
       met:
       * Redistributions of source code must retain the above copyright
         notice, this list of conditions and the following disclaimer.
       * Redistributions in binary form must reproduce the above copyright
         notice, this list of conditions and the following disclaimer in the
         documentation and/or other materials provided with the distribution.
       * Neither the name of the copyright holder nor the names of
         contributors may be used to endorse or promote products derived from
         this software without specific prior written permission.

       THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
       IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
       TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
       PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL COPYRIGHT HOLDER
       BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
       CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
       SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
       BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
       WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
       OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
       ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package org.dynalang.dynalink.linker;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.SwitchPoint;
import java.lang.invoke.WrongMethodTypeException;
import java.util.List;
import org.dynalang.dynalink.CallSiteDescriptor;
import org.dynalang.dynalink.support.Guards;

/**
 * Represents a conditionally valid method handle. It is an immutable quadruplet of an invocation method handle, a guard
 * method handle that defines the applicability of the invocation handle, an array of switch points that can be used for
 * external invalidation of the invocation handle, and an exception class used for a catch guard. The invocation handle is
 * suitable for invocation if the guard handle returns true for its arguments, and as long as none of the switch points
 * are invalidated or the exception is thrown by the invocation. The guard, the switch points, and the exception class are all
 * optional.
 *
 * @author Attila Szegedi
 */
public class GuardedInvocation {
    private static final SwitchPoint[] NO_SWITCH_POINTS = new SwitchPoint[0];

    private final MethodHandle invocation;
    private final MethodHandle guard;
    private final Class<? extends Throwable> exception;
    private final SwitchPoint[] switchPoints;

    /**
     * Creates a new guarded invocation. This invocation is unconditional as it has no invalidations.
     *
     * @param invocation the method handle representing the invocation. Must not be null.
     * @throws NullPointerException if invocation is null.
     */
    public GuardedInvocation(final MethodHandle invocation) {
        this(invocation, null, NO_SWITCH_POINTS, null);
    }

    /**
     * Creates a new guarded invocation.
     *
     * @param invocation the method handle representing the invocation. Must not be null.
     * @param guard the method handle representing the guard. Must have the same method type as the invocation, except
     * it must return boolean. For some useful guards, check out the {@link Guards} class. It can be null to represent
     * an unconditional invocation, although that is unusual.
     * @throws NullPointerException if invocation is null.
     */
    public GuardedInvocation(final MethodHandle invocation, final MethodHandle guard) {
        this(invocation, guard, NO_SWITCH_POINTS, null);
    }

    /**
     * Creates a new guarded invocation.
     *
     * @param invocation the method handle representing the invocation. Must not be null.
     * @param switchPoint the optional switch point that can be used to invalidate this linkage.
     * @throws NullPointerException if invocation is null.
     */
    public GuardedInvocation(final MethodHandle invocation, final SwitchPoint switchPoint) {
        this(invocation, null, switchPoint, null);
    }

    /**
     * Creates a new guarded invocation.
     *
     * @param invocation the method handle representing the invocation. Must not be null.
     * @param guard the method handle representing the guard. Must have the same method type as the invocation, except
     * it must return boolean. For some useful guards, check out the {@link Guards} class. It can be null. If both it
     * and the switch point are null, this represents an unconditional invocation, which is legal but unusual.
     * @param switchPoint the optional switch point that can be used to invalidate this linkage.
     * @throws NullPointerException if invocation is null.
     */
    public GuardedInvocation(final MethodHandle invocation, final MethodHandle guard, final SwitchPoint switchPoint) {
        this(invocation, guard, switchPoint, null);
    }

    /**
     * Creates a new guarded invocation.
     *
     * @param invocation the method handle representing the invocation. Must not be null.
     * @param guard the method handle representing the guard. Must have the same method type as the invocation, except
     * it must return boolean. For some useful guards, check out the {@link Guards} class. It can be null. If both it
     * and the switch point are null, this represents an unconditional invocation, which is legal but unusual.
     * @param switchPoint the optional switch point that can be used to invalidate this linkage.
     * @param exception the optional exception type that is expected to be thrown by the invocation and that also
     * invalidates the linkage.
     * @throws NullPointerException if invocation is null.
     */
    public GuardedInvocation(final MethodHandle invocation, final MethodHandle guard, final SwitchPoint switchPoint, final Class<? extends Throwable> exception) {
        invocation.getClass(); // NPE check
        this.invocation = invocation;
        this.guard = guard;
        this.switchPoints = switchPoint == null ? NO_SWITCH_POINTS : new SwitchPoint[] { switchPoint };
        this.exception = exception;
    }

    /**
     * Creates a new guarded invocation
     *
     * @param invocation the method handle representing the invocation. Must not be null.
     * @param guard the method handle representing the guard. Must have the same method type as the invocation, except
     * it must return boolean. For some useful guards, check out the {@link Guards} class. It can be null. If both it
     * and the switch point are null, this represents an unconditional invocation, which is legal but unusual.
     * @param switchPoints the optional switch points that can be used to invalidate this linkage.
     * @param exception the optional exception type that is expected to be thrown by the invocation and that also
     * invalidates the linkage.
     * @throws NullPointerException if invocation is null.
     */
    public GuardedInvocation(final MethodHandle invocation, final MethodHandle guard, final SwitchPoint[] switchPoints, final Class<? extends Throwable> exception) {
        invocation.getClass(); // NPE check
        this.invocation = invocation;
        this.guard = guard;
        this.switchPoints = switchPoints != null && switchPoints.length > 0 ? switchPoints.clone() : NO_SWITCH_POINTS;
        this.exception = exception;
    }

    /**
     * Returns the invocation method handle.
     *
     * @return the invocation method handle. It will never be null.
     */
    public MethodHandle getInvocation() {
        return invocation;
    }

    /**
     * Returns the guard method handle.
     *
     * @return the guard method handle. Can be null.
     */
    public MethodHandle getGuard() {
        return guard;
    }

    /**
     * Returns an array of switch points that can be used to invalidate the invocation handle. The returned
     * array is a copy of the internal array; changes to it won't be reflected in the invocation.
     *
     * @return an array of switch points that can be used to invalidate the invocation handle.
     */
    public SwitchPoint[] getSwitchPoints() {
        return switchPoints.clone();
    }

    /**
     * Returns the exception type that if thrown should be used to invalidate the linkage.
     *
     * @return the exception type that if thrown should be used to invalidate the linkage. Can be null.
     */
    public Class<? extends Throwable> getException() {
        return exception;
    }

    /**
     * Returns true if and only if this guarded invocation has at least one invalidated switch point.
     * @return true if and only if this guarded invocation has at least one invalidated switch point.
     */
    public boolean hasBeenInvalidated() {
        for (final SwitchPoint sp : switchPoints) {
            if (sp.hasBeenInvalidated()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Asserts that the invocation is of the specified type, and the guard (if present) is of the specified type with a
     * boolean return type.
     *
     * @param type the asserted type
     * @throws WrongMethodTypeException if the invocation and the guard are not of the expected method type.
     */
    public void assertType(final MethodType type) {
        assertType(invocation, type);
        if(guard != null) {
            assertType(guard, type.changeReturnType(Boolean.TYPE));
        }
    }

    /**
     * Creates a new guarded invocation with different methods, preserving the switch point.
     *
     * @param newInvocation the new invocation
     * @param newGuard the new guard
     * @return a new guarded invocation with the replaced methods and the same switch point as this invocation.
     */
    public GuardedInvocation replaceMethods(final MethodHandle newInvocation, final MethodHandle newGuard) {
        return new GuardedInvocation(newInvocation, newGuard, switchPoints, exception);
    }

    private GuardedInvocation replaceMethodsOrThis(final MethodHandle newInvocation, final MethodHandle newGuard) {
        if(newInvocation == invocation && newGuard == guard) {
            return this;
        }
        return replaceMethods(newInvocation, newGuard);
    }

    /**
     * Returns a new guarded invocation that has the passed switch point added to the end of the array of this
     * invocation's switch points.
     * @param newSwitchPoint new switch point, or null for no-op.
     * @return new guarded invocation with the extra switch point (or this invocation if null was passed).
     */
    public GuardedInvocation addSwitchPoint(final SwitchPoint newSwitchPoint) {
        if (newSwitchPoint == null) {
            return this;
        }

        final SwitchPoint[] newSwitchPoints;
        final int l = switchPoints.length;
        newSwitchPoints = new SwitchPoint[l + 1];
        System.arraycopy(switchPoints, 0, newSwitchPoints, 0, l);
        newSwitchPoints[l] = newSwitchPoint;

        return new GuardedInvocation(invocation, guard, newSwitchPoints, exception);
    }

    /**
     * Changes the type of the invocation, as if {@link MethodHandle#asType(MethodType)} was applied to its invocation
     * and its guard, if it has one (with return type changed to boolean, and parameter count potentially truncated for
     * the guard). If the invocation already is of the required type, returns this object.
     * @param newType the new type of the invocation.
     * @return a guarded invocation with the new type applied to it.
     */
    public GuardedInvocation asType(final MethodType newType) {
        return replaceMethodsOrThis(invocation.asType(newType), guard == null ? null : Guards.asType(guard, newType));
    }

    /**
     * Changes the type of the invocation, as if {@link LinkerServices#asType(MethodHandle, MethodType)} was applied to
     * its invocation and its guard, if it has one (with return type changed to boolean, and parameter count potentially
     * truncated for the guard). If the invocation already is of the required type, returns this object.
     * @param linkerServices the linker services to use for the conversion
     * @param newType the new type of the invocation.
     * @return a guarded invocation with the new type applied to it.
     */
    public GuardedInvocation asType(final LinkerServices linkerServices, final MethodType newType) {
        return replaceMethodsOrThis(linkerServices.asType(invocation, newType), guard == null ? null :
            Guards.asType(linkerServices, guard, newType));
    }

    /**
     * Changes the type of the invocation, as if {@link LinkerServices#asTypeLosslessReturn(MethodHandle, MethodType)} was
     * applied to its invocation and {@link LinkerServices#asType(MethodHandle, MethodType)} applied to its guard, if it
     * has one (with return type changed to boolean, and parameter count potentially truncated for the guard). If the
     * invocation doesn't change its type, returns this object.
     * @param linkerServices the linker services to use for the conversion
     * @param newType the new type of the invocation.
     * @return a guarded invocation with the new type applied to it.
     */
    public GuardedInvocation asTypeSafeReturn(final LinkerServices linkerServices, final MethodType newType) {
        return replaceMethodsOrThis(linkerServices.asTypeLosslessReturn(invocation, newType), guard == null ? null :
            Guards.asType(linkerServices, guard, newType));
    }

    /**
     * Changes the type of the invocation, as if {@link MethodHandle#asType(MethodType)} was applied to its invocation
     * and its guard, if it has one (with return type changed to boolean for guard). If the invocation already is of the
     * required type, returns this object.
     * @param desc a call descriptor whose method type is adapted.
     * @return a guarded invocation with the new type applied to it.
     */
    public GuardedInvocation asType(final CallSiteDescriptor desc) {
        return asType(desc.getMethodType());
    }

    /**
     * Applies argument filters to both the invocation and the guard (if there is one).
     * @param pos the position of the first argumen being filtered
     * @param filters the argument filters
     * @return a filtered invocation
     */
    public GuardedInvocation filterArguments(final int pos, final MethodHandle... filters) {
        return replaceMethods(MethodHandles.filterArguments(invocation, pos, filters), guard == null ? null :
            MethodHandles.filterArguments(guard, pos, filters));
    }

    /**
     * Makes an invocation that drops arguments in both the invocation and the guard (if there is one).
     * @param pos the position of the first argument being dropped
     * @param valueTypes the types of the values being dropped
     * @return an invocation that drops arguments
     */
    public GuardedInvocation dropArguments(final int pos, final List<Class<?>> valueTypes) {
        return replaceMethods(MethodHandles.dropArguments(invocation, pos, valueTypes), guard == null ? null :
            MethodHandles.dropArguments(guard, pos, valueTypes));
    }

    /**
     * Makes an invocation that drops arguments in both the invocation and the guard (if there is one).
     * @param pos the position of the first argument being dropped
     * @param valueTypes the types of the values being dropped
     * @return an invocation that drops arguments
     */
    public GuardedInvocation dropArguments(final int pos, final Class<?>... valueTypes) {
        return replaceMethods(MethodHandles.dropArguments(invocation, pos, valueTypes), guard == null ? null :
            MethodHandles.dropArguments(guard, pos, valueTypes));
    }


    /**
     * Composes the invocation, switchpoint, and the guard into a composite method handle that knows how to fall back.
     * @param fallback the fallback method handle in case switchpoint is invalidated or guard returns false.
     * @return a composite method handle.
     */
    public MethodHandle compose(final MethodHandle fallback) {
        return compose(fallback, fallback, fallback);
    }

    /**
     * Composes the invocation, switchpoint, and the guard into a composite method handle that knows how to fall back.
     * @param switchpointFallback the fallback method handle in case switchpoint is invalidated.
     * @param guardFallback the fallback method handle in case guard returns false.
     * @param catchFallback the fallback method in case the exception handler triggers
     * @return a composite method handle.
     */
    public MethodHandle compose(final MethodHandle guardFallback, final MethodHandle switchpointFallback, final MethodHandle catchFallback) {
        final MethodHandle guarded =
                guard == null ? invocation : MethodHandles.guardWithTest(guard, invocation, guardFallback);

        final MethodHandle catchGuarded = exception == null ? guarded : MethodHandles.catchException(guarded, exception,
                MethodHandles.dropArguments(catchFallback, 0, exception));

        MethodHandle spGuarded = catchGuarded;
        for (final SwitchPoint sp : switchPoints) {
            spGuarded = sp.guardWithTest(spGuarded, switchpointFallback);
        }
        return spGuarded;
    }

    private static void assertType(final MethodHandle mh, final MethodType type) {
        if(!mh.type().equals(type)) {
            throw new WrongMethodTypeException("Expected type: " + type + " actual type: " + mh.type());
        }
    }
}
