package org.dynalang.dynalink.support;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;

import org.dynalang.dynalink.linker.CallSiteDescriptor;

/**
 * A default, fairly light implementation of a call site descriptor. It has
 * several limitations that still make it suitable for majority of usages: it
 * does not store {@link Lookup} objects but always returns the public lookup
 * from its {@link #getLookup()} method, and it does not support additional
 * static bootstrap arguments, so its {@link #getAdditionalBootstrapArgument(int)}
 * always returns 0. If you need to support either non-public lookup or
 * additional arguments, you can create your own subclass.
 * @author Attila Szegedi
 * @version $Id: $
 */
class DefaultCallSiteDescriptor extends CallSiteDescriptor {
    private static final char TOKEN_DELIMITER_CHAR = ':';

    private final String[] tokenizedName;
    private final MethodType methodType;

    DefaultCallSiteDescriptor(String[] tokenizedName, MethodType methodType) {
        this.tokenizedName = tokenizedName;
        this.methodType = methodType;
    }

    /**
     * Returns the number of tokens in the name of the method at the call site.
     * Method names are tokenized with the colon ":" character, i.e.
     * "dyn:getProp:color" would be the name used to describe a method that
     * retrieves the property named "color" on the object it is invoked on.
     * @return the number of tokens in the name of the method at the call site.
     */
    public int getNameTokenCount() {
        return tokenizedName.length;
    }

    /**
     * Returns the <i>i<sup>th</sup></i> token in the method name at the call
     * site. Method names are tokenized with the colon ":" character.
     * @param i the index of the token. Must be between 0 (inclusive) and
     * {@link #getNameTokenCount()} (exclusive)
     * @throws IllegalArgumentException if the index is outside the allowed
     * range.
     * @return the <i>i<sup>th</sup></i> token in the method name at the call
     * site.
     */
    public String getNameToken(int i) {
        try {
            return tokenizedName[i];
        }
        catch(ArrayIndexOutOfBoundsException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    String[] getTokenizedName() {
        return tokenizedName;
    }

    @Override
    public Lookup getLookup() {
        return MethodHandles.publicLookup();
    }

    @Override
    public Object getAdditionalBootstrapArgument(int i) {
        throw new IllegalArgumentException();
    }

    @Override
    public int getAdditionalBootstrapArgumentCount() {
        return 0;
    }

    /**
     * Returns the name of the method at the call site. Note that the object
     * internally only stores the tokenized name, and has to reconstruct the
     * full name from tokens on each invocation.
     * @return the name of the method at the call site.
     */
    public String getName() {
        final StringBuilder b = new StringBuilder(8*tokenizedName.length);
        b.append(tokenizedName[0]);
        for(int i = 1; i < tokenizedName.length; ++i) {
            b.append(TOKEN_DELIMITER_CHAR).append(tokenizedName[i]);
        }
        return b.toString();
    }

    /**
     * The type of the method at the call site.
     *
     * @return type of the method at the call site.
     */
    public MethodType getMethodType() {
        return methodType;
    }

    /**
     * Checks that the method type has exactly the desired number of arguments,
     * throws an exception if it doesn't.
     *
     * @param count the desired parameter count
     * @throws BootstrapMethodError if the parameter count doesn't match
     */
    public void assertParameterCount(int count) {
        if(methodType.parameterCount() != count) {
            throw new BootstrapMethodError(getName() + " must have exactly " +
                    count + " parameters");
        }
    }

    /**
     * Creates a new call site descriptor from this descriptor, which is
     * identical to this, except it drops few of the parameters from the method
     * type.
     *
     * @param from the index of the first parameter to drop
     * @param to the index of the first parameter after "from" not to drop
     * @return a new call site descriptor with the parameter dropped.
     */
    public CallSiteDescriptor dropParameterTypes(int from, int to) {
        return new DefaultCallSiteDescriptor(tokenizedName, methodType
                        .dropParameterTypes(from, to));
    }

    /**
     * Creates a new call site descriptor from this descriptor, which is
     * identical to this, except it changes the type of one of the parameters
     * in the method type.
     *
     * @param num the index of the parameter type to change
     * @param newType the new type for the parameter
     * @return a new call site descriptor, with the type of the parameter in the
     * method type changed.
     */
    public CallSiteDescriptor changeParameterType(int num, Class<?> newType) {
        return new DefaultCallSiteDescriptor(tokenizedName, methodType
                        .changeParameterType(num, newType));
    }

}
