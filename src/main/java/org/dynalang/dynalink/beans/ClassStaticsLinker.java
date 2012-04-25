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

import org.dynalang.dynalink.linker.CallSiteDescriptor;
import org.dynalang.dynalink.linker.GuardedInvocation;
import org.dynalang.dynalink.linker.GuardingDynamicLinker;
import org.dynalang.dynalink.linker.LinkRequest;
import org.dynalang.dynalink.linker.LinkerServices;
import org.dynalang.dynalink.linker.TypeBasedGuardingDynamicLinker;

/**
 * Provides a linker for the {@link ClassStatics} objects.
 * @author Attila Szegedi
 * @version $Id: $
 */
class ClassStaticsLinker implements TypeBasedGuardingDynamicLinker {
    private static final ClassValue<GuardingDynamicLinker> linkers = new ClassValue<GuardingDynamicLinker>() {
        @Override
        protected GuardingDynamicLinker computeValue(Class<?> clazz) {
            return new SingleClassStaticsLinker(clazz);
        }
    };

    private static class SingleClassStaticsLinker extends AbstractJavaLinker {
        SingleClassStaticsLinker(Class<?> clazz) {
            super(clazz, ClassStatics.getIsClass(clazz));
            addPropertyGetter("class", ClassStatics.GET_CLASS, false);
        }

        @Override
        FacetIntrospector createFacetIntrospector() {
            return new ClassStaticsIntrospector(clazz);
        }

        @Override
        public GuardedInvocation getGuardedInvocation(LinkRequest request, LinkerServices linkerServices) {
            final GuardedInvocation gi = super.getGuardedInvocation(request, linkerServices);
            if(gi != null) {
                return gi;
            }
            final LinkRequest ncrequest = request.withoutRuntimeContext();
            final CallSiteDescriptor callSiteDescriptor = ncrequest.getCallSiteDescriptor();
            final String op = callSiteDescriptor.getNameToken(1);
            if("new" == op) {
                final DynamicMethod ctor = ClassLinker.getConstructor(clazz);
                if(ctor != null) {
                    return new GuardedInvocation(ctor.getInvocation(callSiteDescriptor, linkerServices),
                            getClassGuard(callSiteDescriptor));
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
}