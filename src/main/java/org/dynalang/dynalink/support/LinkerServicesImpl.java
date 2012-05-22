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
package org.dynalang.dynalink.support;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

import org.dynalang.dynalink.linker.ConversionComparator.Comparison;
import org.dynalang.dynalink.linker.GuardedInvocation;
import org.dynalang.dynalink.linker.GuardingDynamicLinker;
import org.dynalang.dynalink.linker.LinkRequest;
import org.dynalang.dynalink.linker.LinkerServices;

/**
 * Default implementation of the {@link LinkerServices} interface.
 *
 * @author Attila Szegedi
 * @version $Id: $
 */
public class LinkerServicesImpl implements LinkerServices {

    private final TypeConverterFactory typeConverterFactory;
    private final GuardingDynamicLinker topLevelLinker;

    /**
     * Creates a new linker services object.
     *
     * @param typeConverterFactory the type converter factory exposed by the services.
     * @param topLevelLinker the top level linker used by the services.
     */
    public LinkerServicesImpl(final TypeConverterFactory typeConverterFactory,
            final GuardingDynamicLinker topLevelLinker) {
        this.typeConverterFactory = typeConverterFactory;
        this.topLevelLinker = topLevelLinker;
    }

    @Override
    public boolean canConvert(Class<?> from, Class<?> to) {
        return typeConverterFactory.canConvert(from, to);
    }

    @Override
    public MethodHandle asType(MethodHandle handle, MethodType fromType) {
        return typeConverterFactory.asType(handle, fromType);
    }

    @Override
    public Comparison compareConversion(Class<?> sourceType, Class<?> targetType1, Class<?> targetType2) {
        return typeConverterFactory.compareConversion(sourceType, targetType1, targetType2);
    }

    @Override
    public GuardedInvocation getGuardedInvocation(LinkRequest linkRequest) throws Exception {
        return topLevelLinker.getGuardedInvocation(linkRequest, this);
    }
}