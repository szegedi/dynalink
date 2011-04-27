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
import java.lang.BootstrapMethodError;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.dynalang.dynalink.CallSiteDescriptor;
import org.dynalang.dynalink.LinkerServices;
import org.dynalang.dynalink.beans.support.ApplicableOverloadedMethods;
import org.dynalang.dynalink.beans.support.MethodHandleEx;
import org.dynalang.dynalink.beans.support.TypeUtilities;
import org.dynalang.dynalink.beans.support.ApplicableOverloadedMethods.ApplicabilityTest;

/**
 * Represents an overloaded method.
 * @author Attila Szegedi
 * @version $Id: $
 */
public class OverloadedDynamicMethod implements DynamicMethod
{
    /**
     * Holds a list of all methods.
     */
    private final LinkedList<MethodHandleEx> methods = 
        new LinkedList<MethodHandleEx>();
    private final ClassLoader classLoader;

    /**
     * Creates a new overloaded dynamic method.
     * @param clazz the class this method belongs to
     */
    public OverloadedDynamicMethod(Class<?> clazz) {
        this.classLoader = clazz.getClassLoader();
    }

    public MethodHandle getInvocation(
            final CallSiteDescriptor callSiteDescriptor,
            final LinkerServices linkerServices)
    {
        final MethodType callSiteType = callSiteDescriptor.getMethodType();

        // First, find all methods applicable to the call site by subtyping
        // (JLS 15.12.2.2)
        final ApplicableOverloadedMethods subtypingApplicables = 
            getApplicables(callSiteType, 
                    ApplicableOverloadedMethods.APPLICABLE_BY_SUBTYPING);
        // Next, find all methods applicable by method invocation conversion to
        // the call site (JLS 15.12.2.3).
        final ApplicableOverloadedMethods methodInvocationApplicables = 
            getApplicables(callSiteType, 
                    ApplicableOverloadedMethods.APPLICABLE_BY_METHOD_INVOCATION_CONVERSION);
        // Finally, find all methods applicable by variable arity invocation.
        // (JLS 15.12.2.4).
        final ApplicableOverloadedMethods variableArityApplicables = 
            getApplicables(callSiteType, 
                    ApplicableOverloadedMethods.APPLICABLE_BY_VARIABLE_ARITY);

        // Find the methods that are maximally specific based on the call site
        // signature
        List<MethodHandleEx> maximallySpecifics = 
            subtypingApplicables.findMaximallySpecificMethods();
        if(maximallySpecifics.isEmpty()) {
            maximallySpecifics = 
                methodInvocationApplicables.findMaximallySpecificMethods();
            if(maximallySpecifics.isEmpty()) {
                maximallySpecifics = 
                    variableArityApplicables.findMaximallySpecificMethods();
            }
        }
 
        // Now, get a list of the rest of the methods; those that are *not* 
        // applicable to the call site signature based on JLS rules. As 
        // paradoxical as that might sound, we have to consider these for 
        // dynamic invocation, as they
        // might match more concrete types passed in invocations. That's why we
        // provisionally call them "invokable".
        final List<MethodHandleEx> invokables = (List)methods.clone();
        invokables.removeAll(subtypingApplicables.getMethods());
        invokables.removeAll(methodInvocationApplicables.getMethods());
        invokables.removeAll(variableArityApplicables.getMethods());
        for(final Iterator<MethodHandleEx> it = invokables.iterator(); it.hasNext();) {
            final MethodHandleEx m = it.next();
            if(!isApplicableDynamically(linkerServices, callSiteType, m)) {
                it.remove();
            }
        }
        
        // If no additional methods can apply at run time, and there's more 
        // than one maximally specific method based on call site signature, 
        // that is a link-time ambiguity.
        if(invokables.isEmpty() && maximallySpecifics.size() > 1) {
            throw new BootstrapMethodError("Can't choose among " + 
                    maximallySpecifics + " for argument types " + callSiteType);
        }
        
        // Merge them all.
        invokables.addAll(maximallySpecifics);
        switch(invokables.size()) {
            case 0: {
                // No overloads can ever match the call site type
                return null;
            }
            case 1: {
                // Very lucky, we ended up with a single candidate method 
                // handle based on the call site signature; we can link it very
                // simply by delegating to a SimpleDynamicMethod.
                final MethodHandleEx mh = invokables.iterator().next();
                return new SimpleDynamicMethod(mh.getMethodHandle(), 
                        mh.isVarArgs()).getInvocation(callSiteDescriptor, 
                                linkerServices);
            }
        }
        
        // We have more than one candidate. We have no choice but to link to a
        // method that resolves overloads on every invocation (alternatively,
        // we could opportunistically link the one method that resolves for the
        // current arguments, but we'd need to install a fairly complex guard
        // for that and when it'd fail, we'd go back all the way to candidate
        // selection.
        final List<MethodHandle> fixArgMethods = new LinkedList<MethodHandle>();
        final List<MethodHandle> varArgMethods = new LinkedList<MethodHandle>();
        for (MethodHandleEx m : invokables) {
            final MethodHandle mh = m.getMethodHandle();  
            fixArgMethods.add(mh);
            if(m.isVarArgs()) {
                varArgMethods.add(mh);
            }
        }
        final int paramCount = callSiteType.parameterCount();
        final OverloadedMethod fixArgsMethod = new OverloadedMethod(
                fixArgMethods, paramCount, false, classLoader);
        final OverloadedMethod varArgsMethod = varArgMethods.isEmpty() ? null : 
            new OverloadedMethod(varArgMethods, paramCount, true, classLoader);
        if(varArgsMethod == null) {
            return fixArgsMethod.getFixArgsInvocation(linkerServices, callSiteType);
        }
        else {
            // We're using catchException because it is actually surprisingly
            // effective - we'll be throwing a shared instance of the exception
            // for signaling there's no appropriate fixarg method. We could've
            // used foldArguments() too, except then the vararg invocation 
            // would also always be invoked, causing unnecessary conversions on
            // its arguments.
            // TODO: use dropArguments() once bug with catchException failing
            // with non-boot-classpath exception classes is fixed...
            return MethodHandles.catchException(
                    fixArgsMethod.getFixArgsFirstInvocation(linkerServices, 
                            callSiteType), NoSuchMethodException.class,
                    varArgsMethod.getVarArgsInvocation(linkerServices, 
                            callSiteType)); 
        }
    }
    
    private static boolean isApplicableDynamically(
            LinkerServices linkerServices, MethodType callSiteType, 
            MethodHandleEx m) {
        final MethodType methodType = m.getMethodHandle().type();
        final boolean varArgs = m.isVarArgs();
        final int fixedArgLen = methodType.parameterCount() - (varArgs ? 1 : 0);
        final int callSiteArgLen = callSiteType.parameterCount();
        if(callSiteArgLen < fixedArgLen) {
            return false;
        }
        // Starting from 1, as receiver type doesn't participate 
        for(int i = 1; i < fixedArgLen; ++i) {
            if(!isApplicableDynamically(linkerServices, 
                    callSiteType.parameterType(i), methodType.parameterType(i)))
            {
                return false;
            }
        }
        if(varArgs) {
            final Class<?> varArgArrayType = methodType.parameterType(
                    fixedArgLen); 
            final Class<?> varArgType = varArgArrayType.getComponentType();
            if(fixedArgLen == callSiteArgLen - 1) {
                final Class<?> callSiteArgType = callSiteType.parameterType(fixedArgLen);
                // Exactly one vararg; check both exact matching and component
                // matching.
                return isApplicableDynamically(linkerServices, callSiteArgType, 
                        varArgArrayType) || isApplicableDynamically(
                                linkerServices, callSiteArgType, varArgType);
            }
            else {
                for(int i = fixedArgLen; i < callSiteArgLen; ++i) {
                    if(!isApplicableDynamically(linkerServices, 
                            callSiteType.parameterType(i), varArgType))
                    {
                        return false;
                    }
                }
                return true;
            }
        }
        else {
            return true;
        }
    }

    private static boolean isApplicableDynamically(
            LinkerServices linkerServices, Class<?> callSiteType,
            Class<?> methodType)
    {
        return TypeUtilities.isPotentiallyConvertible(callSiteType, methodType)
            || linkerServices.canConvert(callSiteType, methodType);
    }

    private ApplicableOverloadedMethods getApplicables(MethodType callSiteType,
            ApplicabilityTest test)
    {
        return new ApplicableOverloadedMethods(methods, callSiteType, test);
    }

    /**
     * Add a method identified by a {@link SimpleDynamicMethod} to this 
     * overloaded method's set.
     * @param method the method to add.
     */
    public void addMethod(SimpleDynamicMethod method) {
        addMethod(method.getTarget(), method.isVarArgs());
    }
    
    /**
     * Add a method to this overloaded method's set.
     * @param method a method to add
     * @param varArgs if true, the method is variable arguments
     */
    public void addMethod(final MethodHandle method, final boolean varArgs) {
        methods.add(new MethodHandleEx(method, varArgs));
    }
    
}