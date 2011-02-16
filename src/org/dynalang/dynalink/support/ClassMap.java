/*
   Copyright 2009 Attila Szegedi

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

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A dual map that can either strongly or weakly reference a given class 
 * depending on whether the class is visible from a class loader or not. 
 * @author Attila Szegedi
 * @version $Id: $
 * @param <T> the type of the values in the map
 */
public abstract class ClassMap<T> {
    private final ConcurrentMap<Class<?>, T> map = new ConcurrentHashMap<Class<?>, T>();
    private final Map<Class<?>, Reference<T>> weakMap = new WeakHashMap<Class<?>, Reference<T>>();
    private final ClassLoader classLoader;
   
    /**
     * Creates a new class map. It will use strong references for all keys and
     * values where the key is a class visible from the class loader, and will
     * use weak keys and soft values for all other classes.
     * @param classLoader the classloader that determines strong 
     * referenceability.
     */
    protected ClassMap(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }


    /**
     * Compute the value associated with the given class. It is possible that
     * the method will be invoked several times (or even concurrently) for the
     * same class parameter.
     * @param clazz the class to compute the value for
     * @return the return value. Must not be null.
     */
    protected abstract T computeValue(Class<?> clazz);

    /**
     * Returns the class loader that governs the strong referenceability of 
     * this class map.
     * @return the class loader that governs the strong referenceability of 
     * this class map.
     */
    public ClassLoader getClassLoader() {
        return classLoader;
    }
    
    /**
     * Returns the value associated with the class
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
            weakMap.put(clazz, new SoftReference<T>(newV));
            return newV;
        }
    }
}
