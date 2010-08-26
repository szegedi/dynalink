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

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

import org.dynalang.dynalink.CallSiteDescriptor;
import org.dynalang.dynalink.GuardedInvocation;
import org.dynalang.dynalink.GuardingDynamicLinker;
import org.dynalang.dynalink.LinkerServices;
import org.dynalang.dynalink.TypeBasedGuardingDynamicLinker;

/**
 * A composite type-based guarding dynamic linker. When a receiver of a not yet 
 * seen class is encountered, all linkers are invoked sequentially until one 
 * returns a value other than null. This linker is then bound to the class, and
 * next time a receiver of same type is encountered, the linking is delegated 
 * to that linker first, speeding up dispatch. Note that an instance of this 
 * class is also bound to a class loader, and will only cache lookup 
 * information for classes visible to the class loader, in order to not create 
 * strong references to classes in foreign class loaders. (NOTE: might explore 
 * <a href="http://dow.ngra.de/2009/06/15/classloaderlocal-how-to-avoid-classloader-leaks-on-application-redeploy">
 * Jevgeni Kabanov's ClassLoaderLocal</a> in future to lift the limitation.
 * @author Attila Szegedi
 * @version $Id: $
 */
public class CompositeTypeBasedGuardingDynamicLinker 
implements TypeBasedGuardingDynamicLinker, Serializable {
    private static final long serialVersionUID = 1L;

    private final TypeBasedGuardingDynamicLinker[] linkers;
    private final ClassMap<TypeBasedGuardingDynamicLinker> classToLinker;

    /**
     * Creates a new composite type-based linker.
     * @param linkers the component linkers
     * @param classLoader the class loader determining which classes are safe
     * to cache information for
     */
    public CompositeTypeBasedGuardingDynamicLinker(
            Iterable<? extends TypeBasedGuardingDynamicLinker> linkers,
            ClassLoader classLoader) {
        final List<TypeBasedGuardingDynamicLinker> l = 
            new LinkedList<TypeBasedGuardingDynamicLinker>();
        for (TypeBasedGuardingDynamicLinker resolver : linkers) {
            l.add(resolver);
        }
        this.linkers = l.toArray(new TypeBasedGuardingDynamicLinker[l.size()]);
        this.classToLinker = new ClassMap<TypeBasedGuardingDynamicLinker>(classLoader);
    }

    public GuardedInvocation getGuardedInvocation(
            final CallSiteDescriptor callSiteDescriptor,
            final LinkerServices linkerServices, final Object... arguments) 
    throws Exception
    {
        if(arguments.length == 0) {
            return null;
        }
        final Class<?> clazz = arguments[0].getClass();
        final TypeBasedGuardingDynamicLinker linker = classToLinker.get(clazz);
        if(linker != null) {
            final GuardedInvocation gi = linker.getGuardedInvocation(
                    callSiteDescriptor, linkerServices, arguments);
            if(gi != null) {
                return gi;
            }
        }
        for (final TypeBasedGuardingDynamicLinker newLinker : linkers) {
            final GuardedInvocation gi = newLinker.getGuardedInvocation(
                    callSiteDescriptor, linkerServices, arguments);
            if(gi != null) {
                classToLinker.put(clazz, newLinker);
                return gi;
            }
        }
        // Remember we can't resolve it.
        classToLinker.put(clazz, BottomGuardingDynamicLinker.INSTANCE);
        return null;
    }

    /**
     * Optimizes a list of type-based linkers. If a group of adjacent linkers 
     * in the list all implement {@link TypeBasedGuardingDynamicLinker}, they 
     * will be replaced with a single instance of 
     * {@link CompositeTypeBasedGuardingDynamicLinker} that contains them.
     * @param linkers the list of linkers to optimize
     * @param classLoader the class loader 
     * @return the optimized list
     */
    public static List<GuardingDynamicLinker> optimize(
            Iterable<? extends GuardingDynamicLinker> linkers, 
            ClassLoader classLoader) {
        final List<GuardingDynamicLinker> llinkers = 
            new LinkedList<GuardingDynamicLinker>();
        final List<TypeBasedGuardingDynamicLinker> tblinkers = 
            new LinkedList<TypeBasedGuardingDynamicLinker>();
        for (GuardingDynamicLinker linker : linkers) {
            if(linker instanceof TypeBasedGuardingDynamicLinker) {
                tblinkers.add((TypeBasedGuardingDynamicLinker)linker);
            }
            else {
                addTypeBased(llinkers, tblinkers, classLoader);
                llinkers.add(linker);
            }
        }
        addTypeBased(llinkers, tblinkers, classLoader);
        return llinkers;
    }
    
    private static void addTypeBased(List<GuardingDynamicLinker> llinkers, 
            List<TypeBasedGuardingDynamicLinker> tblinkers, 
            ClassLoader classLoader)
    {
        switch(tblinkers.size()) {
            case 0: {
                break;
            }
            case 1: {
                llinkers.addAll(tblinkers);
                tblinkers.clear();
                break;
            }
            default: {
                llinkers.add(new CompositeTypeBasedGuardingDynamicLinker
                        (tblinkers, classLoader));
                tblinkers.clear();
                break;
            }
        }
    }
}