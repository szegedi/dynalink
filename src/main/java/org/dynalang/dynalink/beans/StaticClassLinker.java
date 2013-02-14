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
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import org.dynalang.dynalink.CallSiteDescriptor;
import org.dynalang.dynalink.beans.GuardedInvocationComponent.ValidationType;
import org.dynalang.dynalink.linker.GuardedInvocation;
import org.dynalang.dynalink.linker.GuardingDynamicLinker;
import org.dynalang.dynalink.linker.LinkRequest;
import org.dynalang.dynalink.linker.LinkerServices;
import org.dynalang.dynalink.linker.TypeBasedGuardingDynamicLinker;
import org.dynalang.dynalink.support.Lookup;

/**
 * Provides a linker for the {@link StaticClass} objects.
 * @author Attila Szegedi
 */
class StaticClassLinker implements TypeBasedGuardingDynamicLinker {
    private final ClassValue<GuardingDynamicLinker> linkers = new ClassValue<GuardingDynamicLinker>() {
        @Override
        protected GuardingDynamicLinker computeValue(Class<?> clazz) {
            return new SingleClassStaticsLinker(clazz);
        }
    };

    private static class SingleClassStaticsLinker extends AbstractJavaLinker {
        private final DynamicMethod constructor;

        SingleClassStaticsLinker(Class<?> clazz) {
            super(clazz, IS_CLASS.bindTo(clazz));
            // Map "staticClassObject.class" to StaticClass.getRepresentedClass(). Some adventurous soul could subclass
            // StaticClass, so we use INSTANCE_OF validation instead of EXACT_CLASS.
            setPropertyGetter("class", GET_CLASS, ValidationType.INSTANCE_OF);
            constructor = createConstructorMethod(clazz);
        }

        /**
         * Creates a dynamic method containing all overloads of a class' public constructor
         * @param clazz the target class
         * @return a dynamic method containing all overloads of a class' public constructor. If the class has no public
         * constructors, returns null.
         */
        private static DynamicMethod createConstructorMethod(Class<?> clazz) {
            if(clazz.isArray()) {
                final MethodHandle boundArrayCtor = ARRAY_CTOR.bindTo(clazz.getComponentType());
                return new SimpleDynamicMethod(drop(boundArrayCtor.asType(boundArrayCtor.type().changeReturnType(
                        clazz))), clazz, "<init>");
            }

            final Constructor<?>[] ctrs = clazz.getConstructors();
            final List<MethodHandle> mhs = new ArrayList<>(ctrs.length);
            for(int i = 0; i < ctrs.length; ++i) {
                mhs.add(drop(Lookup.PUBLIC.unreflectConstructor(ctrs[i])));
            }
            return createDynamicMethod(mhs, clazz, "<init>");
        }

        private static MethodHandle drop(MethodHandle mh) {
            return MethodHandles.dropArguments(mh, 0, StaticClass.class);
        }

        @Override
        FacetIntrospector createFacetIntrospector() {
            return new StaticClassIntrospector(clazz);
        }

        @Override
        public GuardedInvocation getGuardedInvocation(LinkRequest request, LinkerServices linkerServices)
                throws Exception {
            final GuardedInvocation gi = super.getGuardedInvocation(request, linkerServices);
            if(gi != null) {
                return gi;
            }
            final CallSiteDescriptor desc = request.getCallSiteDescriptor();
            final String op = desc.getNameToken(CallSiteDescriptor.OPERATOR);
            final MethodType methodType = desc.getMethodType();
            if("new" == op && constructor != null) {
                final MethodHandle ctorInvocation = constructor.getInvocation(methodType, linkerServices);
                if(ctorInvocation != null) {
                    return new GuardedInvocation(ctorInvocation, getClassGuard(methodType));
                }
            }
            return null;
        }
    }

    @Override
    public GuardedInvocation getGuardedInvocation(LinkRequest request, LinkerServices linkerServices) throws Exception {
        final Object receiver = request.getReceiver();
        if(receiver instanceof StaticClass) {
            return linkers.get(((StaticClass)receiver).getRepresentedClass()).getGuardedInvocation(request,
                    linkerServices);
        }
        return null;
    }

    @Override
    public boolean canLinkType(Class<?> type) {
        return type == StaticClass.class;
    }

    /*private*/ static final MethodHandle GET_CLASS = new Lookup(MethodHandles.lookup()).findVirtual(StaticClass.class,
            "getRepresentedClass", MethodType.methodType(Class.class));

    /*private*/ static final MethodHandle IS_CLASS = new Lookup(MethodHandles.lookup()).findStatic(StaticClassLinker.class,
            "isClass", MethodType.methodType(Boolean.TYPE, Class.class, Object.class));

    /*private*/ static final MethodHandle ARRAY_CTOR = Lookup.PUBLIC.findStatic(Array.class, "newInstance",
            MethodType.methodType(Object.class, Class.class, int.class));

    @SuppressWarnings("unused")
    private static boolean isClass(Class<?> clazz, Object obj) {
        return obj instanceof StaticClass && ((StaticClass)obj).getRepresentedClass() == clazz;
    }
}