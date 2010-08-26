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
public class ClassMap<T> {
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
    public ClassMap(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }
    
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
        // Check in fastest first - linkers we're allowed to strongly reference
        final T v = map.get(clazz);
        if(v != null) {
            return v;
        }
        // Check in linkers we aren't allowed to strongly reference
        synchronized(weakMap) {
            Reference<T> ref = weakMap.get(clazz);
            if(ref != null) {
                return ref.get();
            }
        }
        return null;
    }
    
    /**
     * Associates a value with the class
     * @param clazz the class
     * @param v the value to associate with the class
     */
    public void put(Class<?> clazz, T v) {
        if(Guards.canReferenceDirectly(classLoader, clazz.getClassLoader())) {
            map.put(clazz, v);
        }
        else {
            synchronized(weakMap) {
                weakMap.put(clazz, new SoftReference<T>(v));
            }
        }
    }
}
