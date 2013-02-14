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

package org.dynalang.dynalink.support;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A dual map that can either strongly or weakly reference a given class depending on whether the class is visible from
 * a class loader or not.
 *
 * @author Attila Szegedi
 * @param <T> the type of the values in the map
 */
public abstract class ClassMap<T> {
    private final ConcurrentMap<Class<?>, T> map = new ConcurrentHashMap<>();
    private final Map<Class<?>, Reference<T>> weakMap = new WeakHashMap<>();
    private final ClassLoader classLoader;

    /**
     * Creates a new class map. It will use strong references for all keys and values where the key is a class visible
     * from the class loader, and will use weak keys and soft values for all other classes.
     *
     * @param classLoader the classloader that determines strong referenceability.
     */
    protected ClassMap(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * Compute the value associated with the given class. It is possible that the method will be invoked several times
     * (or even concurrently) for the same class parameter.
     *
     * @param clazz the class to compute the value for
     * @return the return value. Must not be null.
     */
    protected abstract T computeValue(Class<?> clazz);

    /**
     * Returns the class loader that governs the strong referenceability of this class map.
     *
     * @return the class loader that governs the strong referenceability of this class map.
     */
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    /**
     * Returns the value associated with the class
     *
     * @param clazz the class
     * @return the value associated with the class
     */
    public T get(Class<?> clazz) {
        // Check in fastest first - objects we're allowed to strongly reference
        final T v = map.get(clazz);
        if(v != null) {
            return v;
        }
        // Check objects we're not allowed to strongly reference
        Reference<T> ref;
        synchronized(weakMap) {
            ref = weakMap.get(clazz);
        }
        if(ref != null) {
            final T refv = ref.get();
            if(refv != null) {
                return refv;
            }
        }
        // Not found in either place; create a new value
        final T newV = computeValue(clazz);
        assert newV != null;
        // If allowed to strongly reference, put it in the fast map
        if(Guards.canReferenceDirectly(classLoader, clazz.getClassLoader())) {
            final T oldV = map.putIfAbsent(clazz, newV);
            return oldV != null ? oldV : newV;
        }
        // Otherwise, put it into the weak map
        synchronized(weakMap) {
            ref = weakMap.get(clazz);
            if(ref != null) {
                final T oldV = ref.get();
                if(oldV != null) {
                    return oldV;
                }
            }
            weakMap.put(clazz, new SoftReference<>(newV));
            return newV;
        }
    }
}
