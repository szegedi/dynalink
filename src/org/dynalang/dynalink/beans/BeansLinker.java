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

import java.beans.IntrospectionException;
import java.lang.reflect.UndeclaredThrowableException;

import org.dynalang.dynalink.CallSiteDescriptor;
import org.dynalang.dynalink.DynamicLinkerFactory;
import org.dynalang.dynalink.GuardedInvocation;
import org.dynalang.dynalink.GuardingDynamicLinker;
import org.dynalang.dynalink.LinkRequest;
import org.dynalang.dynalink.LinkerServices;

/**
 * A linker for POJOs. Normally used as the ultimate fallback linker by the
 * {@link DynamicLinkerFactory}.
 *
 * @author Attila Szegedi
 * @version $Id: $
 */
public class BeansLinker implements GuardingDynamicLinker {
    private static final ClassValue<BeanLinker> linkers =
            new ClassValue<BeanLinker>() {
                @Override
                protected BeanLinker computeValue(Class<?> clazz) {
                    try {
                        return new BeanLinker(clazz);
                    } catch(IntrospectionException e) {
                        throw new UndeclaredThrowableException(e);
                    }
                }
            };

    /**
     * Creates a new POJO linker.
     */
    public BeansLinker() {
    }

    public GuardedInvocation getGuardedInvocation(LinkRequest request,
            final LinkerServices linkerServices) throws Exception {
        final Object[] arguments = request.getArguments();
        if(arguments == null || arguments.length == 0) {
            // Can't handle static calls; must have a receiver
            return null;
        }

        final CallSiteDescriptor callSiteDescriptor =
            request.getCallSiteDescriptor();
        final int l = callSiteDescriptor.getNameTokenCount();
        // All names conforming to the dynalang MOP should have at least two
        // tokens, the first one being "dyn"
        if(l < 2 || !"dyn".equals(callSiteDescriptor.getNameToken(0))) {
            return null;
        }

        final Object receiver = arguments[0];
        if(receiver == null) {
            // Can't operate on null
            return null;
        }

        return linkers.get(receiver.getClass()).getGuardedInvocation(request,
                linkerServices);
    }
}