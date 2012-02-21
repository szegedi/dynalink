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

package org.dynalang.dynalink.beans;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.LinkedList;
import java.util.List;

import org.dynalang.dynalink.support.Guards;
import org.dynalang.dynalink.support.TypeUtilities;

/**
 *
 * @author Attila Szegedi
 * @version $Id: $
 */
final class ClassString {
    private final Class<?>[] classes;
    private int hashCode;

    ClassString(Class<?>[] classes) {
        this.classes = classes;
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

    private static MaximallySpecific.TypeFunction<MethodHandle> TF =
            new MaximallySpecific.TypeFunction<MethodHandle>() {
                @Override
                public MethodType type(MethodHandle mh) {
                    return mh.type();
                };
            };

    MethodHandle getMostSpecific(List<MethodHandle> methods, boolean varArg) {
        final List<MethodHandle> maximals =
                MaximallySpecific.getMaximallySpecificMethods(getApplicables(methods, varArg), TF, varArg);
        switch(maximals.size()) {
            case 0: {
                return OverloadedMethod.NO_SUCH_METHOD;
            }
            case 1: {
                return maximals.get(0);
            }
            default: {
                return OverloadedMethod.AMBIGUOUS_METHOD;
            }
        }
    }

    /**
     * Returns all methods that are applicable to actual parameter classes represented by this ClassString object.
     */
    LinkedList<MethodHandle> getApplicables(List<MethodHandle> methods, boolean varArg) {
        final LinkedList<MethodHandle> list = new LinkedList<MethodHandle>();
        for(final MethodHandle member: methods) {
            if(isApplicable(member, varArg)) {
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
    private boolean isApplicable(MethodHandle method, boolean varArg) {
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
            if(!TypeUtilities.isMethodInvocationConvertible(classes[i], formalTypes[i])) {
                return false;
            }
        }
        if(varArg) {
            Class<?> varArgType = formalTypes[fl].getComponentType();
            for(int i = fl; i < cl; ++i) {
                if(!TypeUtilities.isMethodInvocationConvertible(classes[i], varArgType)) {
                    return false;
                }
            }
        }
        return true;
    }
}