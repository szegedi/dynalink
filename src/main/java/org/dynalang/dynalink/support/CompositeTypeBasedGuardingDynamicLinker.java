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

package org.dynalang.dynalink.support;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.dynalang.dynalink.linker.GuardedInvocation;
import org.dynalang.dynalink.linker.GuardingDynamicLinker;
import org.dynalang.dynalink.linker.LinkRequest;
import org.dynalang.dynalink.linker.LinkerServices;
import org.dynalang.dynalink.linker.TypeBasedGuardingDynamicLinker;

/**
 * A composite type-based guarding dynamic linker. When a receiver of a not yet seen class is encountered, all linkers
 * are queried sequentially on their {@link TypeBasedGuardingDynamicLinker#canLinkType(Class)} method. The linkers
 * returning true are then bound to the class, and next time a receiver of same type is encountered, the linking is
 * delegated to those linkers only, speeding up dispatch.
 *
 * @author Attila Szegedi
 */
public class CompositeTypeBasedGuardingDynamicLinker implements TypeBasedGuardingDynamicLinker, Serializable {
    private static final long serialVersionUID = 1L;

    // Using a separate static class instance so there's no strong reference from the class value back to the composite
    // linker.
    private static class ClassToLinker extends ClassValue<List<TypeBasedGuardingDynamicLinker>> {
        private static final List<TypeBasedGuardingDynamicLinker> NO_LINKER = Collections.emptyList();
        private final TypeBasedGuardingDynamicLinker[] linkers;
        private final List<TypeBasedGuardingDynamicLinker>[] singletonLinkers;

        @SuppressWarnings("unchecked")
        ClassToLinker(TypeBasedGuardingDynamicLinker[] linkers) {
            this.linkers = linkers;
            singletonLinkers = new List[linkers.length];
            for(int i = 0; i < linkers.length; ++i) {
                singletonLinkers[i] = Collections.singletonList(linkers[i]);
            }
        }

        @Override
        protected List<TypeBasedGuardingDynamicLinker> computeValue(Class<?> clazz) {
            List<TypeBasedGuardingDynamicLinker> list = NO_LINKER;
            for(int i = 0; i < linkers.length; ++i) {
                final TypeBasedGuardingDynamicLinker linker = linkers[i];
                if(linker.canLinkType(clazz)) {
                    switch(list.size()) {
                        case 0: {
                            list = singletonLinkers[i];
                            break;
                        }
                        case 1: {
                            list = new LinkedList<>(list);
                            // intentional fallthrough
                        }
                        default: {
                            list.add(linker);
                        }
                    }
                }
            }
            return list;
        }
    }

    private final ClassValue<List<TypeBasedGuardingDynamicLinker>> classToLinker;

    /**
     * Creates a new composite type-based linker.
     *
     * @param linkers the component linkers
     */
    public CompositeTypeBasedGuardingDynamicLinker(Iterable<? extends TypeBasedGuardingDynamicLinker> linkers) {
        final List<TypeBasedGuardingDynamicLinker> l = new LinkedList<TypeBasedGuardingDynamicLinker>();
        for(TypeBasedGuardingDynamicLinker linker: linkers) {
            l.add(linker);
        }
        this.classToLinker = new ClassToLinker(l.toArray(new TypeBasedGuardingDynamicLinker[l.size()]));
    }

    @Override
    public boolean canLinkType(Class<?> type) {
        return !classToLinker.get(type).isEmpty();
    }

    @Override
    public GuardedInvocation getGuardedInvocation(LinkRequest linkRequest, final LinkerServices linkerServices)
            throws Exception {
        final Object obj = linkRequest.getReceiver();
        if(obj == null) {
            return null;
        }
        for(TypeBasedGuardingDynamicLinker linker: classToLinker.get(obj.getClass())) {
            final GuardedInvocation invocation = linker.getGuardedInvocation(linkRequest, linkerServices);
            if(invocation != null) {
                return invocation;
            }
        }
        return null;
    }

    /**
     * Optimizes a list of type-based linkers. If a group of adjacent linkers in the list all implement
     * {@link TypeBasedGuardingDynamicLinker}, they will be replaced with a single instance of
     * {@link CompositeTypeBasedGuardingDynamicLinker} that contains them.
     *
     * @param linkers the list of linkers to optimize
     * @return the optimized list
     */
    public static List<GuardingDynamicLinker> optimize(Iterable<? extends GuardingDynamicLinker> linkers) {
        final List<GuardingDynamicLinker> llinkers = new LinkedList<GuardingDynamicLinker>();
        final List<TypeBasedGuardingDynamicLinker> tblinkers = new LinkedList<TypeBasedGuardingDynamicLinker>();
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
                llinkers.add(new CompositeTypeBasedGuardingDynamicLinker(tblinkers));
                tblinkers.clear();
                break;
            }
        }
    }
}