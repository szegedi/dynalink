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

import org.dynalang.dynalink.linker.CallSiteDescriptor;
import org.dynalang.dynalink.linker.GuardedInvocation;
import org.dynalang.dynalink.linker.GuardingDynamicLinker;
import org.dynalang.dynalink.linker.LinkRequest;
import org.dynalang.dynalink.linker.LinkerServices;
import org.dynalang.dynalink.linker.TypeBasedGuardingDynamicLinker;
import org.dynalang.dynalink.support.LinkRequestImpl;
import org.dynalang.dynalink.support.Lookup;
import org.dynalang.dynalink.support.TypeUtilities;

/**
 * Provides a linker for the {@link ClassStatics} objects.
 * @author Attila Szegedi
 * @version $Id: $
 */
class ClassStaticsLinker implements TypeBasedGuardingDynamicLinker {
    private final ClassValue<GuardingDynamicLinker> linkers = new ClassValue<GuardingDynamicLinker>() {
        @Override
        protected GuardingDynamicLinker computeValue(Class<?> clazz) {
            return new SingleClassStaticsLinker(clazz);
        }
    };

    private static class SingleClassStaticsLinker extends AbstractJavaLinker {
        SingleClassStaticsLinker(Class<?> clazz) {
            super(clazz, IS_CLASS.bindTo(clazz));
            addPropertyGetter("class", GET_CLASS, false);
            Class<?> primitiveType = TypeUtilities.getPrimitiveType(clazz);
            if(primitiveType != null) {
                addPropertyGetter("TYPE", MethodHandles.dropArguments(MethodHandles.constant(Class.class,
                        primitiveType), 0, Object.class), false);
            }
        }

        @Override
        FacetIntrospector createFacetIntrospector() {
            return new ClassStaticsIntrospector(clazz);
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
            if("new" == op) {
                // Re-invoke the linker chain for the Class object
                final Object[] args = request.getArguments();
                args[0] = clazz;
                final LinkRequest classRequest = new LinkRequestImpl(
                        request.getCallSiteDescriptor().changeParameterType(0, Class.class), args);
                final GuardedInvocation classInvocation = linkerServices.getGuardedInvocation(classRequest);
                // If it found a constructor invocation, link that with a modified guard. To be completely honest, we'd
                if(classInvocation != null) {
                    return classInvocation.filterArguments(0, GET_CLASS_OBJ).asType(desc);
                }
            }
            return null;
        }
    }

    @Override
    public GuardedInvocation getGuardedInvocation(LinkRequest request, LinkerServices linkerServices) throws Exception {
        final Object receiver = request.getArguments()[0];
        if(receiver instanceof ClassStatics) {
            return linkers.get(((ClassStatics)receiver).getRepresentedClass()).getGuardedInvocation(request,
                    linkerServices);
        }
        return null;
    }

    @Override
    public boolean canLinkType(Class<?> type) {
        return type == ClassStatics.class;
    }

    private static final MethodHandle GET_CLASS = new Lookup(MethodHandles.lookup()).findVirtual(ClassStatics.class,
            "getRepresentedClass", MethodType.methodType(Class.class));

    private static final MethodHandle IS_CLASS = new Lookup(MethodHandles.lookup()).findStatic(ClassStaticsLinker.class,
            "isClass", MethodType.methodType(Boolean.TYPE, Class.class, Object.class));

    @SuppressWarnings("unused")
    private static Class<?> getRepresentedClass(Object obj) {
        return obj instanceof ClassStatics ? ((ClassStatics)obj).getRepresentedClass() : null;
    }

    private static final MethodHandle GET_CLASS_OBJ = new Lookup(MethodHandles.lookup()).findStatic(
            ClassStaticsLinker.class, "getRepresentedClass", MethodType.methodType(Class.class, Object.class));

    @SuppressWarnings("unused")
    private static boolean isClass(Class<?> clazz, Object obj) {
        return obj instanceof ClassStatics && ((ClassStatics)obj).getRepresentedClass() == clazz;
    }

}