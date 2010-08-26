package org.dynalang.dynalink.beans.support;

import java.dyn.MethodHandle;

/**
 * Method handle with a varArg flag.
 * @author Attila Szegedi
 * @version $Id: $
 */
public class MethodHandleEx
{
    final MethodHandle methodHandle;
    final boolean varArgs;
    
    /**
     * Constructs a new MethodHandleEx
     * @param methodHandle the wrapped method handle
     * @param varArgs whether the method is a variable arity method.
     */
    public MethodHandleEx(MethodHandle methodHandle, boolean varArgs) {
        this.methodHandle = methodHandle;
        this.varArgs = varArgs;
    }
    
    /**
     * Returns the method handle.
     * @return the method handle.
     */
    public MethodHandle getMethodHandle() {
        return methodHandle;
    }
    
    /**
     * Returns true if the method represented by the method handle is a 
     * variable arity method.
     * @return true if the method represented by the method handle is a 
     * variable arity method.
     */
    public boolean isVarArgs() {
        return varArgs;
    }
    
    @Override
    public String toString() {
        return "[" + methodHandle.toString() + ";varArgs=" + varArgs + "]";
    }
}