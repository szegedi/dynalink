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

package org.dynalang.dynalink.support;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

import org.dynalang.dynalink.GuardedInvocation;
import org.dynalang.dynalink.GuardingDynamicLinker;
import org.dynalang.dynalink.LinkRequest;
import org.dynalang.dynalink.LinkerServices;
import org.dynalang.dynalink.TypeBasedGuardingDynamicLinker;

/**
 * A composite type-based guarding dynamic linker. When a receiver of a not yet
 * seen class is encountered, all linkers are invoked sequentially until one
 * returns a value other than null. This linker is then bound to the class, and
 * next time a receiver of same type is encountered, the linking is delegated to
 * that linker first, speeding up dispatch.
 *
 * @author Attila Szegedi
 * @version $Id: $
 */
public class CompositeTypeBasedGuardingDynamicLinker implements
        TypeBasedGuardingDynamicLinker, Serializable {
    private static final long serialVersionUID = 1L;

    // Using a separate static class instance so there's no strong reference
    // from the class value back to the composite linker.
    private static class ClassToLinker extends
            ClassValue<TypeBasedGuardingDynamicLinker> {
        private final TypeBasedGuardingDynamicLinker[] linkers;

        ClassToLinker(TypeBasedGuardingDynamicLinker[] linkers) {
            this.linkers = linkers;
        }

        protected TypeBasedGuardingDynamicLinker computeValue(Class<?> clazz) {
            for(TypeBasedGuardingDynamicLinker linker: linkers) {
                if(linker.canLinkType(clazz)) {
                    return linker;
                }
            }
            return BottomGuardingDynamicLinker.INSTANCE;
        }
    }

    private final ClassValue<TypeBasedGuardingDynamicLinker> classToLinker;

    /**
     * Creates a new composite type-based linker.
     *
     * @param linkers the component linkers
     */
    public CompositeTypeBasedGuardingDynamicLinker(
            Iterable<? extends TypeBasedGuardingDynamicLinker> linkers) {
        final List<TypeBasedGuardingDynamicLinker> l =
                new LinkedList<TypeBasedGuardingDynamicLinker>();
        for(TypeBasedGuardingDynamicLinker resolver: linkers) {
            l.add(resolver);
        }
        this.classToLinker =
                new ClassToLinker(l
                        .toArray(new TypeBasedGuardingDynamicLinker[l.size()]));
    }

    public boolean canLinkType(Class<?> type) {
        return classToLinker.get(type) != BottomGuardingDynamicLinker.INSTANCE;
    }

    public GuardedInvocation getGuardedInvocation(LinkRequest linkRequest,
            final LinkerServices linkerServices) throws Exception {
        final Object[] arguments = linkRequest.getArguments();
        if(arguments.length == 0) {
            return null;
        }
        final Object obj = arguments[0];
        if(obj == null) {
            return null;
        }
        return classToLinker.get(obj.getClass()).getGuardedInvocation(
                linkRequest, linkerServices);
    }

    /**
     * Optimizes a list of type-based linkers. If a group of adjacent linkers in
     * the list all implement {@link TypeBasedGuardingDynamicLinker}, they will
     * be replaced with a single instance of
     * {@link CompositeTypeBasedGuardingDynamicLinker} that contains them.
     *
     * @param linkers the list of linkers to optimize
     * @return the optimized list
     */
    public static List<GuardingDynamicLinker> optimize(
            Iterable<? extends GuardingDynamicLinker> linkers) {
        final List<GuardingDynamicLinker> llinkers =
                new LinkedList<GuardingDynamicLinker>();
        final List<TypeBasedGuardingDynamicLinker> tblinkers =
                new LinkedList<TypeBasedGuardingDynamicLinker>();
        for(GuardingDynamicLinker linker: linkers) {
            if(linker instanceof TypeBasedGuardingDynamicLinker) {
                tblinkers.add((TypeBasedGuardingDynamicLinker)linker);
            } else {
                addTypeBased(llinkers, tblinkers);
                llinkers.add(linker);
            }
        }
        addTypeBased(llinkers, tblinkers);
        return llinkers;
    }

    private static void addTypeBased(List<GuardingDynamicLinker> llinkers,
            List<TypeBasedGuardingDynamicLinker> tblinkers) {
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
                llinkers.add(new CompositeTypeBasedGuardingDynamicLinker(
                        tblinkers));
                tblinkers.clear();
                break;
            }
        }
    }
}