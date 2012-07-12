/*
   Copyright 2009-2012 Attila Szegedi

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
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import org.dynalang.dynalink.linker.CallSiteDescriptor;
import org.dynalang.dynalink.linker.GuardedInvocation;
import org.dynalang.dynalink.linker.GuardingDynamicLinker;
import org.dynalang.dynalink.linker.LinkRequest;
import org.dynalang.dynalink.linker.LinkerServices;
import org.dynalang.dynalink.linker.TypeBasedGuardingDynamicLinker;
import org.dynalang.dynalink.support.Lookup;

/**
 * Provides a linker for the {@link StaticClass} objects.
 * @author Attila Szegedi
 * @version $Id: $
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
            addPropertyGetter("class", GET_CLASS, false);
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
                return new SimpleDynamicMethod(boundArrayCtor.asType(boundArrayCtor.type().changeReturnType(clazz)));
            }

            final Constructor<?>[] ctrs = clazz.getConstructors();
            final List<MethodHandle> mhs = new ArrayList<>(ctrs.length);
            for(int i = 0; i < ctrs.length; ++i) {
                mhs.add(MethodHandles.dropArguments(Lookup.PUBLIC.unreflectConstructor(ctrs[i]), 0, StaticClass.class));
            }
            return createDynamicMethod(mhs, clazz, "<init>");
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
            final String op = desc.getNameToken(1);
            if("new" == op && constructor != null) {
                return new GuardedInvocation(constructor.getInvocation(desc, linkerServices), getClassGuard(desc));
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

    private static final MethodHandle GET_CLASS = new Lookup(MethodHandles.lookup()).findVirtual(StaticClass.class,
            "getRepresentedClass", MethodType.methodType(Class.class));

    private static final MethodHandle IS_CLASS = new Lookup(MethodHandles.lookup()).findStatic(StaticClassLinker.class,
            "isClass", MethodType.methodType(Boolean.TYPE, Class.class, Object.class));


    private static final MethodHandle ARRAY_CTOR = Lookup.PUBLIC.findStatic(Array.class, "newInstance",
            MethodType.methodType(Object.class, Class.class, int.class));

    @SuppressWarnings("unused")
    private static boolean isClass(Class<?> clazz, Object obj) {
        return obj instanceof StaticClass && ((StaticClass)obj).getRepresentedClass() == clazz;
    }
}