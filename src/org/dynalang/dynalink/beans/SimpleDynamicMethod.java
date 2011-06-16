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
package org.dynalang.dynalink.beans;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;

import org.dynalang.dynalink.CallSiteDescriptor;
import org.dynalang.dynalink.LinkerServices;
import org.dynalang.dynalink.support.Guards;

/**
 * A dynamic method bound to exactly one, non-overloaded Java method. Handles
 * varargs.
 * @author Attila Szegedi
 * @version $Id: $
 */
public class SimpleDynamicMethod implements DynamicMethod
{
    private final MethodHandle target;

    /**
     * Creates a new simple dynamic method.
     * @param target the target method handle
     */
    public SimpleDynamicMethod(MethodHandle target) {
        this.target = target;
    }

    /**
     * Returns the target of this dynamic method
     * @return the target of this dynamic method
     */
    public MethodHandle getTarget() {
        return target;
    }

    public MethodHandle getInvocation(
            final CallSiteDescriptor callSiteDescriptor,
            final LinkerServices linkerServices)
    {
        final MethodType methodType = target.type();
        final int paramsLen = methodType.parameterCount();
        final boolean varArgs = target.isVarargsCollector();
        final MethodHandle fixTarget = varArgs ? target.asFixedArity() : target;
        final int fixParamsLen = varArgs ? paramsLen - 1 : paramsLen;
        final MethodType callSiteType = callSiteDescriptor.getMethodType();
        final int argsLen = callSiteType.parameterCount();
        if(argsLen < fixParamsLen) {
            // Less actual arguments than number of fixed declared arguments;
            // can't invoke.
            return null;
        }
        // Method handle of the same number of arguments as the call site type
        if(argsLen == fixParamsLen) {
            // Method handle that matches the number of actual arguments as the
            // number of fixed arguments
            final MethodHandle matchedMethod;
            if(varArgs) {
                // If vararg, add a zero-length array of the expected type as
                // the last argument to signify no variable arguments. TODO:
                // check whether collectArguments() would handle this too.
                matchedMethod = MethodHandles.insertArguments(fixTarget,
                        fixParamsLen, Array.newInstance(
                                methodType.parameterType(
                                        fixParamsLen).getComponentType(), 0));
            }
            else {
                // Otherwise, just use the method
                matchedMethod = fixTarget;
            }
            return createConvertingInvocation(matchedMethod, linkerServices,
                    callSiteType);
        }

        // What's below only works for varargs
        if(!varArgs) {
            return null;
        }

        final Class<?> varArgType = methodType.parameterType(fixParamsLen);
        // Handle a somewhat sinister corner case: caller passes exactly one
        // argument in the vararg position, and we must handle both a prepacked
        // vararg array as well as a genuine 1-long vararg sequence.
        if(argsLen == paramsLen) {
            final Class<?> callSiteLastArgType = callSiteType.parameterType(
                    fixParamsLen);
            if(varArgType.isAssignableFrom(callSiteLastArgType))
            {
                // Call site signature guarantees we'll always be passed a
                // single compatible array; just link directly to the method.
                return createConvertingInvocation(fixTarget, linkerServices,
                        callSiteType);
            }
            else if(!linkerServices.canConvert(callSiteLastArgType, varArgType)) {
                // Call site signature guarantees the argument can definitely
                // not be an array (i.e. it is primitive); link immediately to
                // a vararg-packing method handle.
                return createConvertingInvocation(collectArguments(fixTarget,
                        argsLen), linkerServices, callSiteType);
            }
            else {
                // Call site signature makes no guarantees that the single
                // argument in the vararg position will be compatible across
                // all invocations. Need to insert an appropriate guard and
                // fall back to generic vararg method when it is not.
                return MethodHandles.guardWithTest(Guards.isInstance(
                        varArgType, fixParamsLen, callSiteType),
                        createConvertingInvocation(fixTarget, linkerServices,
                                callSiteType), createConvertingInvocation(
                                    collectArguments(fixTarget, argsLen),
                                    linkerServices, callSiteType));
            }
        }
        else {
            // Remaining case: more than one vararg.
            return createConvertingInvocation(collectArguments(fixTarget, argsLen),
                    linkerServices, callSiteType);
        }
    }


    /**
     * Creates a method handle out of the original target that will collect
     * the varargs for the exact component type of the varArg array. Note that
     * this will nicely trigger language-specific type converters for exactly
     * those varargs for which it is necessary when later passed to
     * linkerServices.convertArguments().
     * @param target the original method handle
     * @param parameterCount the total number of arguments in the new method handle
     * @return a collecting method handle
     */
    static MethodHandle collectArguments(MethodHandle target, final int parameterCount)
    {
        final MethodType methodType = target.type();
        final int fixParamsLen = methodType.parameterCount() - 1;
        final Class<?> arrayType = methodType.parameterType(
            fixParamsLen);
        return target.asCollector(arrayType, parameterCount -
            fixParamsLen);
    }

    private static MethodHandle createConvertingInvocation(
            final MethodHandle sizedMethod,
            final LinkerServices linkerServices, final MethodType callSiteType)
    {
        return linkerServices.convertArguments(sizedMethod, callSiteType);
    }
}