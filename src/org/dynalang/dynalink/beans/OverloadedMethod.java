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

import java.dyn.MethodHandle;
import java.dyn.MethodHandles;
import java.dyn.MethodType;
import java.dyn.NoAccessException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.dynalang.dynalink.LinkerServices;
import org.dynalang.dynalink.beans.support.TypeUtilities;

/**
 * Represents a subset of overloaded methods for a certain method name on a
 * certain class. It can be either a fixarg or a vararg subset depending on the
 * subclass. The method is for a fixed number of arguments though (as it is
 * generated for a concrete call site). As such, all methods in the subset can
 * be invoked with the specified number of arguments (exactly matching for
 * fixargs, or having less than or equal fixed arguments, for varargs).
 * @author Attila Szegedi
 * @version $Id: $
 */
class OverloadedMethod {
    static final MethodHandle NO_SUCH_METHOD = getPlaceholder("_noSuchMethod");
    static final MethodHandle AMBIGUOUS_METHOD = getPlaceholder("_ambiguousMethod");

    // This contains either declared types of method arguments, or their
    // superclasses/interfaces, so we can strongly reference them. This is the
    // method type with most general types of arguments common to every method,
    // per argument. It is used for initial conversion.
    private MethodType commonMethodType;
    private final MethodType commonMethodTypeWithException;
    private final Map<ClassString, MethodHandle> argTypesToMethods = 
        new ConcurrentHashMap<ClassString, MethodHandle>();
    private final boolean varArg;
    
    private final List<MethodHandle> methods = new LinkedList<MethodHandle>();

    public OverloadedMethod(List<MethodHandle> methodHandles, int argNum,
            boolean varArg) {
        this.varArg = varArg;
        for (MethodHandle method : methodHandles) {
            methods.add(method);
            MethodType methodType = method.type();
            if(varArg) {
                final int fixArgs = methodType.parameterCount() - 1;
                final Class<?> varArgType = methodType.parameterType(
                        fixArgs).getComponentType();
                methodType = methodType.changeParameterType(fixArgs, varArgType);
                for(int i = fixArgs + 1; i < argNum; ++i) {
                    methodType = methodType.insertParameterTypes(i, varArgType);
                }
            }
            if(commonMethodType == null) {
                commonMethodType = methodType;
            }
            else {
                for(int i = 0; i < argNum; ++i) {
                    final Class<?> oldType = commonMethodType.parameterType(i);
                    final Class<?> newType = 
                        TypeUtilities.getMostSpecificCommonType(oldType, 
                                methodType.parameterType(i));
                    if(oldType != newType) {
                        commonMethodType = 
                            commonMethodType.changeParameterType(i, newType);
                    }
                }
                final Class<?> oldRetType = commonMethodType.returnType();
                final Class<?> newRetType = 
                    TypeUtilities.getMostSpecificCommonType(oldRetType, 
                            methodType.returnType());
                if(oldRetType != newRetType) {
                    commonMethodType = 
                        commonMethodType.changeReturnType(newRetType);
                }
            }
        }
        commonMethodTypeWithException = commonMethodType.insertParameterTypes(
                0, NoSuchMethodException.class);
    }
    
    public MethodHandle getFixArgsInvocation(LinkerServices linkerServices, 
            MethodType callSiteType)
    {
        return getFixArgInvocation(INVOKE_FIXARGS, linkerServices, callSiteType);
    }

    public MethodHandle getFixArgsFirstInvocation(
            LinkerServices linkerServices, MethodType callSiteType) 
    {
        return getFixArgInvocation(INVOKE_FIXARGS_FIRST, linkerServices, 
                callSiteType);
    }
    
    public MethodHandle getVarArgsInvocation(LinkerServices linkerServices, 
            MethodType callSiteType)
    {
        final MethodHandle bound = MethodHandles.insertArguments(
                INVOKE_VARARGS, 0, this, callSiteType);
        final MethodHandle collecting = MethodHandles.collectArguments(bound, 
                commonMethodTypeWithException);
        final MethodHandle converting = linkerServices.convertArguments(
                collecting, callSiteType.insertParameterTypes(0, 
                        NoSuchMethodException.class));
        return converting;
    }

    private MethodHandle getFixArgInvocation(MethodHandle invoker, 
            LinkerServices linkerServices, MethodType callSiteType) {
        final MethodHandle bound = MethodHandles.insertArguments(invoker, 0, 
                this, callSiteType);
        final MethodHandle collecting = MethodHandles.collectArguments(bound, 
                commonMethodType);
        final MethodHandle converting = linkerServices.convertArguments(
                collecting, callSiteType);
        return converting;
    }
    
    private static final MethodHandle INVOKE_FIXARGS = 
        getFixArgsInvocationMethod("_invokeFixArgs");
    private static final MethodHandle INVOKE_FIXARGS_FIRST = 
        getFixArgsInvocationMethod("_invokeFixArgsFirst"); 
    private static final MethodHandle INVOKE_VARARGS = 
        MethodHandles.lookup().findSpecial(OverloadedMethod.class, 
                "_invokeVarArgs", MethodType.methodType(Object.class, 
                        MethodType.class, NoSuchMethodException.class,  
                        Object[].class), OverloadedMethod.class); 
    
    private static MethodHandle getFixArgsInvocationMethod(String name) {
        return MethodHandles.lookup().findSpecial(OverloadedMethod.class, name, 
                MethodType.methodType(Object.class, MethodType.class, 
                        Object[].class), OverloadedMethod.class);
    }

    public Object _invokeFixArgs(MethodType callSiteType, Object... args) 
    throws Throwable
    {
        final MethodHandle method = getInvocationForArgs(callSiteType, args);
        if(method == NO_SUCH_METHOD) {
            throw new NoAccessException("None of the methods " + methods + 
                    " matches arguments");
        }
        return method.invokeVarargs(args); 
    }

    private static final NoSuchMethodException NO_SUCH_FIX_ARGS_METHOD = 
        new NoSuchMethodException();
    
    public Object _invokeFixArgsFirst(MethodType callSiteType, Object... args) 
    throws Throwable 
    {
        final MethodHandle method = getInvocationForArgs(callSiteType, args);
        if(method == NO_SUCH_METHOD) {
            throw NO_SUCH_FIX_ARGS_METHOD;
        }
        return method.invokeVarargs(args); 
    }

    public Object _invokeVarArgs(MethodType callSiteType, 
            NoSuchMethodException e, Object... args) throws Throwable {
        if(e != NO_SUCH_FIX_ARGS_METHOD) {
            throw e;
        }
        final MethodHandle method = getInvocationForArgs(callSiteType, args);
        if(method == NO_SUCH_METHOD) {
            throw new NoAccessException("None of the methods " + methods + 
                " matches arguments");
        }
        return MethodHandles.collectArguments(method, 
                callSiteType).invokeVarargs(args); 
        
    }

    MethodHandle getInvocationForArgs(MethodType callSiteType, Object... args) {
        Class<?>[] argTypes = new Class[args.length];
        for(int i = 0; i < argTypes.length; ++i) {
            Object arg = args[i];
            argTypes[i] = arg == null ? callSiteType.parameterType(i) : 
                arg.getClass();
        }
        final ClassString classString = new ClassString(argTypes);
        MethodHandle method = argTypesToMethods.get(classString);
        if(method == null) {
            method = classString.getMostSpecific(methods, varArg);
            // TODO: pass a class loader here to avoid keeping references to
            // unrelated classes.
            // if(classString.isVisibleFrom(classLoader))
            {
                argTypesToMethods.put(classString, method);
            }
        }
        if(method == AMBIGUOUS_METHOD) {
            throw new NoAccessException("Can't unambiguously select one of " + 
                    methods);
        }
	return method;
    }

    public static final void _noSuchMethod() {
        throw new UnsupportedOperationException("Not intended to be called");
    }

    public static final void _ambiguousMethod() {
        throw new UnsupportedOperationException("Not intended to be called");
    }

    private static final MethodHandle getPlaceholder(String name) {
        return MethodHandles.lookup().findStatic(OverloadedMethod.class, name, 
                MethodType.methodType(Void.TYPE));
    }
}