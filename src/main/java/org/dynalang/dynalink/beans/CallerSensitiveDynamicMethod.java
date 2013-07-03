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
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.dynalang.dynalink.support.Lookup;

/**
 * A dynamic method bound to exactly one Java method or constructor that is caller sensitive. Since the target method is
 * caller sensitive, it doesn't cache a method handle but rather uses the passed lookup object in
 * {@link #getTarget(java.lang.invoke.MethodHandles.Lookup)} to unreflect a method handle from the reflective member on
 * every request.
 *
 * @author Attila Szegedi
 */
class CallerSensitiveDynamicMethod extends SingleDynamicMethod {
    // Typed as "AccessibleObject" as it can be either a method or a constructor.
    // If we were Java8-only, we could use java.lang.reflect.Executable
    private final AccessibleObject target;
    private final MethodType type;

    public CallerSensitiveDynamicMethod(AccessibleObject target) {
        super(getName(target));
        this.target = target;
        this.type = getMethodType(target);
    }

    private static String getName(AccessibleObject target) {
        final Member m = (Member)target;
        return getMethodNameWithSignature(getMethodType(target), getClassAndMethodName(m.getDeclaringClass(),
                m.getName()));
    }

    @Override
    MethodType getMethodType() {
        return type;
    }

    private static MethodType getMethodType(AccessibleObject ao) {
        final boolean isMethod = ao instanceof Method;
        final Class<?> rtype = isMethod ? ((Method)ao).getReturnType() : ((Constructor<?>)ao).getDeclaringClass();
        final Class<?>[] ptypes = isMethod ? ((Method)ao).getParameterTypes() : ((Constructor<?>)ao).getParameterTypes();
        final MethodType type = MethodType.methodType(rtype, ptypes);
        final Member m = (Member)ao;
        return type.insertParameterTypes(0,
                isMethod ?
                        Modifier.isStatic(m.getModifiers()) ?
                                Object.class :
                                m.getDeclaringClass() :
                        StaticClass.class);
    }

    @Override
    boolean isVarArgs() {
        return target instanceof Method ? ((Method)target).isVarArgs() : ((Constructor<?>)target).isVarArgs();
    }

    @Override
    MethodHandle getTarget(MethodHandles.Lookup lookup) {
        if(target instanceof Method) {
            final MethodHandle mh = Lookup.unreflect(lookup, (Method)target);
            if(Modifier.isStatic(((Member)target).getModifiers())) {
                return StaticClassIntrospector.editStaticMethodHandle(mh);
            }
            return mh;
        }
        return StaticClassIntrospector.editConstructorMethodHandle(Lookup.unreflectConstructor(lookup,
                (Constructor<?>)target));
    }
}
