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

package org.dynalang.dynalink.linker;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;

import org.dynalang.dynalink.support.CallSiteDescriptorFactory;

/**
 * A descriptor of a call site. Used in place of passing a real call site to
 * guarding linkers so they aren't tempted to directly manipulate them; also it
 * carries the tokenized name of the method and the {@link Lookup} object
 * for the class in which the call site is in.
 *
 * @author Attila Szegedi
 * @version $Id: $
 */
public abstract class CallSiteDescriptor {

    /**
     * Protected constructor for subclasses.
     */
    protected CallSiteDescriptor() {
    }

    /**
     * Returns the number of tokens in the name of the method at the call site.
     * Method names are tokenized with the colon ":" character, i.e.
     * "dyn:getProp:color" would be the name used to describe a method that
     * retrieves the property named "color" on the object it is invoked on.
     * @return the number of tokens in the name of the method at the call site.
     */
    public abstract int getNameTokenCount();

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
    public abstract String getNameToken(int i);

    /**
     * Returns the name of the method at the call site. Note that the object
     * internally only stores the tokenized name, and has to reconstruct the
     * full name from tokens on each invocation.
     * @return the name of the method at the call site.
     */
    public abstract String getName();

    /**
     * The type of the method at the call site.
     *
     * @return type of the method at the call site.
     */
    public abstract MethodType getMethodType();

    /**
     * Returns the lookup passed to the bootstrap method.
     * @return the lookup passed to the bootstrap method.
     */
    public abstract Lookup getLookup();

    /**
     * Returns the number of additional static bootstrap arguments at the call
     * site.
     * @return the number of additional static bootstrap arguments at the call
     * site; it can be between 0 and 251.
     */
    public abstract int getAdditionalBootstrapArgumentCount();

    /**
     * Returns the <i>i<sup>th</sup></i> additional static bootstrap argument at
     * the call site.
     * @param i the index of the argument. Must be between 0 (inclusive) and
     * {@link #getAdditionalBootstrapArgumentCount()} (exclusive).
     * @throws IllegalArgumentException if the index is outside the allowed
     * range.
     * @return the <i>i<sup>th</sup></i> additional static bootstrap argument at
     * the call site.
     */
    public abstract Object getAdditionalBootstrapArgument(int i);

    /**
     * Creates a new call site descriptor from this descriptor, which is
     * identical to this, except it drops few of the parameters from the method
     * type.
     *
     * @param from the index of the first parameter to drop
     * @param to the index of the first parameter after "from" not to drop
     * @return a new call site descriptor with the parameter dropped.
     */
    public abstract CallSiteDescriptor dropParameterTypes(int from, int to);

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
    public abstract CallSiteDescriptor changeParameterType(int num, Class<?> newType);

    /**
     * Creates a new call site descriptor instance. The actual underlying class of the instance is
     * dependent on the passed arguments to be space efficient; i.e. if you don't use either a
     * non-public lookup or static bootstrap arguments, you'll get back an implementation that
     * doesn't waste space on storing them.
     * @param lookup the lookup that determines access rights at the call site. If your language
     * runtime doesn't have equivalents of Java access concepts, just use
     * {@link MethodHandles#publicLookup()}. Must not be null.
     * @param name the name of the method at the call site. Must not be null.
     * @param methodType the type of the method at the call site. Must not be null.
     * @param bootstrapArgs additional static bootstrap arguments. Can be null.
     * @return a new call site descriptor.
     */
    public static CallSiteDescriptor create(Lookup lookup, String name, MethodType methodType,
            Object... bootstrapArgs) {
        return CallSiteDescriptorFactory.create(lookup, name, methodType, bootstrapArgs);
    }
}