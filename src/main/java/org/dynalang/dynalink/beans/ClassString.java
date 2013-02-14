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
import java.util.LinkedList;
import java.util.List;

import org.dynalang.dynalink.linker.LinkerServices;
import org.dynalang.dynalink.support.Guards;
import org.dynalang.dynalink.support.TypeUtilities;

/**
 *
 * @author Attila Szegedi
 */
final class ClassString {
    private final Class<?>[] classes;
    private int hashCode;

    ClassString(Class<?>[] classes) {
        this.classes = classes;
    }

    ClassString(MethodType type) {
        this(type.parameterArray());
    }

    Class<?>[] getClasses() {
        return classes;
    }

    @Override
    public boolean equals(Object other) {
        if(!(other instanceof ClassString)) {
            return false;
        }
        final Class<?>[] otherClasses = ((ClassString)other).classes;
        if(otherClasses.length != classes.length) {
            return false;
        }
        for(int i = 0; i < otherClasses.length; ++i) {
            if(otherClasses[i] != classes[i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        if(hashCode == 0) {
            int h = 0;
            for(int i = 0; i < classes.length; ++i) {
                h ^= classes[i].hashCode();
            }
            hashCode = h;
        }
        return hashCode;
    }

    boolean isVisibleFrom(final ClassLoader classLoader) {
        for(int i = 0; i < classes.length; ++i) {
            if(!Guards.canReferenceDirectly(classLoader, classes[i].getClassLoader())) {
                return false;
            }
        }
        return true;
    }

    List<MethodHandle> getMaximallySpecifics(List<MethodHandle> methods, LinkerServices linkerServices, boolean varArg) {
        return MaximallySpecific.getMaximallySpecificMethods(getApplicables(methods, linkerServices, varArg), varArg,
                classes, linkerServices);
    }

    /**
     * Returns all methods that are applicable to actual parameter classes represented by this ClassString object.
     */
    LinkedList<MethodHandle> getApplicables(List<MethodHandle> methods, LinkerServices linkerServices, boolean varArg) {
        final LinkedList<MethodHandle> list = new LinkedList<>();
        for(final MethodHandle member: methods) {
            if(isApplicable(member, linkerServices, varArg)) {
                list.add(member);
            }
        }
        return list;
    }

    /**
     * Returns true if the supplied method is applicable to actual parameter classes represented by this ClassString
     * object.
     *
     */
    private boolean isApplicable(MethodHandle method, LinkerServices linkerServices, boolean varArg) {
        final Class<?>[] formalTypes = method.type().parameterArray();
        final int cl = classes.length;
        final int fl = formalTypes.length - (varArg ? 1 : 0);
        if(varArg) {
            if(cl < fl) {
                return false;
            }
        } else {
            if(cl != fl) {
                return false;
            }
        }
        // Starting from 1 as we ignore the receiver type
        for(int i = 1; i < fl; ++i) {
            if(!canConvert(linkerServices, classes[i], formalTypes[i])) {
                return false;
            }
        }
        if(varArg) {
            final Class<?> varArgType = formalTypes[fl].getComponentType();
            for(int i = fl; i < cl; ++i) {
                if(!canConvert(linkerServices, classes[i], varArgType)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean canConvert(LinkerServices ls, Class<?> from, Class<?> to) {
        return ls == null ? TypeUtilities.isMethodInvocationConvertible(from, to) : ls.canConvert(from, to);
    }
}