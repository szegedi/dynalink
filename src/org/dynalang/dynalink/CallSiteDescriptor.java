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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    private final List<String> tokenizedName;
    private final String name;
    private final MethodType methodType;
    private final Lookup lookup;

    /**
     * Create a new call site descriptor from explicit information.
     * @param lookup the lookup for the class at the call site
     * @param name the name of the method
     * @param methodType the method type
     */
    public CallSiteDescriptor(Lookup lookup, String name, MethodType methodType) {
        this(lookup, name, tokenizeName(name), methodType);
    }

    private CallSiteDescriptor(Lookup lookup, String name,
            List<String> tokenizedName, MethodType methodType) {
        this.name = name;
        this.tokenizedName = Collections.unmodifiableList(tokenizedName);
        this.methodType = methodType;
        this.lookup = lookup;
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
     * Returns the tokenized name of the method at the call site.
     *
     * @return name of the method at the call site. Returned is an unmodifiable
     * list of interned strings representing the name tokenized at colon ":"
     * characters.
     */
    public List<String> getTokenizedName() {
        return tokenizedName;
    }

    /**
     * Returns the untokenized name of the method at the call site.
     *
     * @return name of the method at the call site.
     */
    public String getName() {
        return name;
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

    private static List<String> tokenizeName(String name) {
        final ArrayList<String> lname = new ArrayList<String>(3);
        final StringTokenizer tok = new StringTokenizer(name, ":");
        while(tok.hasMoreTokens()) {
            lname.add(tok.nextToken().intern());
        }
        lname.trimToSize();
        return lname;
    }

    /**
     * Creates a new call site descriptor from this descriptor, which is
     * identical to this, except it drops few of the arguments from the method
     * type
     *
     * @param from the index of the first arguments to drop
     * @param to the index of the first arguments after "from" not to drop
     * @return a new call site descriptor with the arguments dropped.
     */
    public CallSiteDescriptor dropParameterTypes(int from, int to) {
        return new CallSiteDescriptor(lookup, name, tokenizedName, methodType
                        .dropParameterTypes(from, to));
    }

    public CallSiteDescriptor changeParameterType(int num, Class<?> newType) {
        return new CallSiteDescriptor(lookup, name, tokenizedName, methodType
                        .changeParameterType(num, newType));
    }
}