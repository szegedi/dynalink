/*
   Copyright 2009-2011 Attila Szegedi

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

import java.lang.invoke.MethodType;
import java.lang.invoke.MethodHandles.Lookup;
import java.util.StringTokenizer;

/**
 * A descriptor of a call site. Used in place of passing a real call site to
 * guarding linkers so they aren't tempted to do nasty things to it; also it
 * carries the tokenized name of the method, which is not available in the call
 * site object itself.
 *
 * @author Attila Szegedi
 * @version $Id: $
 */
public class CallSiteDescriptor {
    private final String[] tokenizedName;
    private final MethodType methodType;
    private final Lookup lookup;

    /**
     * Create a new call site descriptor from explicit information.
     * @param lookup the lookup for the class at the call site
     * @param name the name of the method
     * @param methodType the method type
     */
    public CallSiteDescriptor(Lookup lookup, String name, MethodType methodType) {
        this(lookup, tokenizeName(name), methodType);
    }

    private CallSiteDescriptor(Lookup lookup, String[] tokenizedName,
            MethodType methodType) {
        this.tokenizedName = tokenizedName;
        this.methodType = methodType;
        this.lookup = lookup;
    }

    /**
     * Returns the number of tokens in the name of the method at the call site.
     * Method names are tokenized with the colon ":" character.
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
            throw new BootstrapMethodError(tokenizedName
                    + " must have exactly " + count + " parameters");
        }
    }

    private static String[] tokenizeName(String name) {
        final StringTokenizer tok = new StringTokenizer(name, ":");
        final String[] tokens = new String[tok.countTokens()];
        for(int i = 0; i < tokens.length; ++i) {
            tokens[i] = tok.nextToken().intern();
        }
        return tokens;
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
        return new CallSiteDescriptor(lookup, tokenizedName, methodType
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
        return new CallSiteDescriptor(lookup, tokenizedName, methodType
                        .changeParameterType(num, newType));
    }
}