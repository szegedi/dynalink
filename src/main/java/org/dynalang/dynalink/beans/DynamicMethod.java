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

package org.dynalang.dynalink.beans;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.StringTokenizer;

import org.dynalang.dynalink.linker.LinkerServices;

/**
 * Represents a single dynamic method. A "dynamic" method can be bound to a single Java method, or can be bound to all
 * overloaded methods of the same name on a class. Getting an invocation of a dynamic method bound to multiple
 * overloaded methods will perform overload resolution (actually, it will perform partial overloaded resolution at link
 * time, but if that fails to identify exactly one target method, it will generate a method handle that will perform the
 * rest of the overload resolution at invocation time for actual argument types).
 *
 * @author Attila Szegedi
 */
abstract class DynamicMethod {
    private final String name;

    DynamicMethod(String name) {
        this.name = name;
    }

    String getName() {
        return name;
    }

    /**
     * Creates an invocation for the dynamic method. If the method is overloaded, it will perform overloaded method
     * resolution based on the specified method type. The resulting resolution can either identify a single method to be
     * invoked among the overloads, or it can identify multiple ones. In the latter case, the returned method handle
     * will perform further overload resolution among these candidates at every invocation. If the method to be invoked
     * is a variable arguments (vararg) method, it will pack the extra arguments in an array before the invocation of
     * the underlying method if it is not already done.
     *
     * @param callSiteType the method type at a call site
     * @param linkerServices linker services. Used for language-specific type conversions.
     * @return an invocation suitable for calling the method from the specified call site.
     */
    abstract MethodHandle getInvocation(MethodType callSiteType, LinkerServices linkerServices);

    /**
     * Returns a simple dynamic method representing a single underlying Java method (possibly selected among several
     * overloads) with formal parameter types exactly matching the passed signature.
     * @param paramTypes the comma-separated list of requested parameter type names. The names will match both
     * qualified and unqualified type names.
     * @return a simple dynamic method representing a single underlying Java method, or null if none of the Java methods
     * behind this dynamic method exactly match the requested parameter types.
     */
    abstract SimpleDynamicMethod getMethodForExactParamTypes(String paramTypes);

    /**
     * True if this dynamic method already contains a method handle with an identical signature as the passed in method
     * handle.
     * @param mh the method handle to check
     * @return true if it already contains an equivalent method handle.
     */
    abstract boolean contains(MethodHandle mh);

    static boolean typeMatchesDescription(String paramTypes, MethodType type) {
        final StringTokenizer tok = new StringTokenizer(paramTypes, ", ");
        for(int i = 1; i < type.parameterCount(); ++i) { // i = 1 as we ignore the receiver
            if(!(tok.hasMoreTokens() && typeNameMatches(tok.nextToken(), type.parameterType(i)))) {
                return false;
            }
        }
        return !tok.hasMoreTokens();
    }

    private static boolean typeNameMatches(String typeName, Class<?> type) {
        final int lastDot = typeName.lastIndexOf('.');
        final String fullTypeName = type.getCanonicalName();
        return lastDot != -1 && fullTypeName.endsWith(typeName.substring(lastDot)) || typeName.equals(fullTypeName);
    }

    static String getClassAndMethodName(Class<?> clazz, String name) {
        final String clazzName = clazz.getCanonicalName();
        return (clazzName == null ? clazz.getName() : clazzName) + "." + name;
    }

    @Override
    public String toString() {
        return "[" + getClass().getName() + " " + getName() + "]";
    }
}