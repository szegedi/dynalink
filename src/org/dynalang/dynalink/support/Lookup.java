package org.dynalang.dynalink.support;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.BootstrapMethodError;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * A wrapper around MethodHandles.Lookup; accounts for lack of findSpecial() in
 * the backport.
 * @author Attila Szegedi
 * @version $Id: $
 */
public class Lookup
{
    private final MethodHandles.Lookup lookup;
    
    /**
     * Creates a new instance, bound to an instance of 
     * {@link MethodHandles.Lookup}.
     * @param lookup the {@link MethodHandles.Lookup} it delegates to. 
     */
    public Lookup(MethodHandles.Lookup lookup) {
        this.lookup = lookup;
    }
    static {
      MethodHandles.lookup();
    }
    public static final Lookup PUBLIC = new Lookup(MethodHandles.publicLookup());
    /**
     * Performs a {@link MethodHandles.Lookup#unreflect(Method)}, converting 
     * any encountered {@link IllegalAccessException} into a 
     * {@link BootstrapMethodError}.
     * @param m the method to unreflect
     * @return the unreflected method handle.
     */
    public MethodHandle unreflect(Method m) {
        try {
            return lookup.unreflect(m);
        }
        catch(IllegalAccessException e) {
            throw new BootstrapMethodError("Failed to unreflect " + m, e);
        }
    }
    /**
     * Performs a findSpecial on the underlying lookup, except for the backport
     * where it rather uses unreflect.
     * @param declaringClass class declaring the method
     * @param name the name of the method
     * @param type the type of the method
     * @return a method handle for the method
     * @throws BootstrapMethodError if the method does not exist or is 
     * inaccessible.
     */
    public MethodHandle findSpecial(Class<?> declaringClass, String name, 
            MethodType type)
    {
        try {
            if(Backport.inUse) {
                final Method m = declaringClass.getDeclaredMethod(name, 
                    type.parameterArray());
                if(!Modifier.isPublic(declaringClass.getModifiers()) || 
                    !Modifier.isPublic(m.getModifiers()))
                {
                    m.setAccessible(true);
                }
                return unreflect(m);
            }
            else {
                return lookup.findSpecial(declaringClass, name, type, declaringClass);
            }
        } 
        catch (IllegalAccessException|NoSuchMethodException e) {
            throw new BootstrapMethodError("Failed to find special method " + 
                methodDescription(declaringClass, name, type), e);
        }
    }

    private static String methodDescription(Class<?> declaringClass, String name, MethodType type) {
        return declaringClass.getName() + "#" + name + type;
    }
    
    public MethodHandle findStatic(Class<?> declaringClass, String methodName, MethodType methodType) {
        try {
            return lookup.findStatic(declaringClass, methodName, methodType);
        }
        catch (IllegalAccessException|NoSuchMethodException e) {
            throw new BootstrapMethodError("Failed to find static method " + 
                methodDescription(declaringClass, methodName, methodType), e);
          }
    }

    public MethodHandle findVirtual(Class<?> declaringClass, String methodName, MethodType methodType) {
      try {
          return lookup.findVirtual(declaringClass, methodName, methodType);
      }
      catch (IllegalAccessException|NoSuchMethodException e) {
          throw new BootstrapMethodError("Failed to find virtual method " + 
              methodDescription(declaringClass, methodName, methodType), e);
        }
  }

}
