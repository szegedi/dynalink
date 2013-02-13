/*
   Copyright 2009-2013 Attila Szegedi

   Licensed under either the Apache License, Version 2.0 (the "Apache
   License") or the BSD License (the "BSD License"), with licensee
   being free to choose either of the two at their discretion.

   You may not use this file except in compliance with either the Apache
   License or the BSD License.

   A copy of the BSD License is available in the root directory of the
   source distribution of the project under the file name
   "Dynalink-License-BSD.txt".

   A copy of the Apache License is available in the root directory of the
   source distribution of the project under the file name
   "Dynalink-License-Apache-2.0.txt". Alternatively, you may obtain a
   copy of the Apache License at <http://www.apache.org/licenses/LICENSE-2.0>

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See your chosen License for the specific language governing permissions
   and limitations under that License.
*/

package org.dynalang.dynalink.beans;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;

class StaticClassIntrospector extends FacetIntrospector {
    StaticClassIntrospector(Class<?> clazz) {
        super(clazz, false);
    }

    @Override
    Map<String, MethodHandle> getInnerClassGetters() {
        final Map<String, MethodHandle> map = new HashMap<>();
        for(Class<?> innerClass: membersLookup.getInnerClasses()) {
            map.put(innerClass.getSimpleName(), editMethodHandle(MethodHandles.constant(StaticClass.class,
                    StaticClass.forClass(innerClass))));
        }
        return map;
    }

    @Override
    MethodHandle editMethodHandle(MethodHandle mh) {
        MethodHandle newHandle = MethodHandles.dropArguments(mh, 0, Object.class);
        // NOTE: this is a workaround for the fact that dropArguments doesn't preserve vararg collector state.
        if(mh.isVarargsCollector() && !newHandle.isVarargsCollector()) {
            final MethodType type = mh.type();
            newHandle = newHandle.asVarargsCollector(type.parameterType(type.parameterCount() - 1));
        }
        return newHandle;
    }
}
