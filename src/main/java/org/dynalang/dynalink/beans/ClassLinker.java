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
import java.lang.reflect.Constructor;

import org.dynalang.dynalink.linker.CallSiteDescriptor;
import org.dynalang.dynalink.linker.GuardedInvocation;
import org.dynalang.dynalink.linker.LinkRequest;
import org.dynalang.dynalink.linker.LinkerServices;
import org.dynalang.dynalink.support.Guards;
import org.dynalang.dynalink.support.Lookup;

/**
 * A linker for java.lang.Class objects. Provides a synthetic property "statics" that allows access to static fields and
 * methods on the class (respecting property getter/setter conventions), as well as the "dyn:new" operation for invoking
 * constructors. In this regard, a Class object acts as a constructor function for its instances.
 * methods on Class objects.
 * @author Attila Szegedi
 * @version $Id: $
 */
class ClassLinker extends BeanLinker {

    private static final ClassValue<ConstructorInfo> constructors = new ClassValue<ConstructorInfo>() {
        @Override
        protected ConstructorInfo computeValue(Class<?> type) {
            return new ConstructorInfo(type);
        }
    };

    private static final ClassValue<ClassStatics> statics = new ClassValue<ClassStatics>() {
        @Override
        protected ClassStatics computeValue(Class<?> type) {
            return new ClassStatics(type);
        }
    };

    ClassLinker() {
        super(Class.class);
        // Map class.statics to ClassLinker.statics.get(class)
        addPropertyGetter("statics", GET_STATICS, false);
    }

    private static final MethodHandle GET_STATICS = new Lookup(MethodHandles.lookup()).findStatic(ClassLinker.class,
            "getStatics", MethodType.methodType(ClassStatics.class, Class.class));

    @SuppressWarnings("unused")
    private static ClassStatics getStatics(Class<?> clazz) {
        return statics.get(clazz);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public GuardedInvocation getGuardedInvocation(LinkRequest request, LinkerServices linkerServices) {
        GuardedInvocation gi = super.getGuardedInvocation(request, linkerServices);
        if(gi != null) {
            return gi;
        }
        final LinkRequest ncrequest = request.withoutRuntimeContext();
        final CallSiteDescriptor callSiteDescriptor = ncrequest.getCallSiteDescriptor();
        final String op = callSiteDescriptor.getNameToken(1);
        if("new".equals(op)) {
            return constructors.get((Class)ncrequest.getArguments()[0]).getGuardedInvocation(callSiteDescriptor,
                    linkerServices);
        }
        return null;
    }

    private static class ConstructorInfo {
        private final MethodHandle constructorGuard;
        private final DynamicMethod constructor;

        ConstructorInfo(Class<?> clazz) {
            final Constructor<?>[] ctrs = clazz.getConstructors();
            final MethodHandle[] mhs = new MethodHandle[ctrs.length];
            for(int i = 0; i < ctrs.length; ++i) {
                mhs[i] = MethodHandles.dropArguments(Lookup.PUBLIC.unreflectConstructor(ctrs[i]), 0, Class.class);
            }
            constructor = AbstractJavaLinker.createDynamicMethod(mhs, clazz);
            constructorGuard = constructor == null ? null : Guards.getIdentityGuard(clazz);
        }

        GuardedInvocation getGuardedInvocation(CallSiteDescriptor callSiteDescriptor, LinkerServices linkerServices) {
            if(constructor == null) {
                return null;
            }
            return new GuardedInvocation(constructor.getInvocation(callSiteDescriptor, linkerServices), Guards.asType(
                    constructorGuard, callSiteDescriptor.getMethodType()));
        }
    }
}