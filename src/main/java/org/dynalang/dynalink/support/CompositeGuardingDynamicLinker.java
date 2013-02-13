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
import java.util.LinkedList;
import java.util.List;

import org.dynalang.dynalink.linker.GuardedInvocation;
import org.dynalang.dynalink.linker.GuardingDynamicLinker;
import org.dynalang.dynalink.linker.LinkRequest;
import org.dynalang.dynalink.linker.LinkerServices;

/**
 * A {@link GuardingDynamicLinker} that delegates sequentially to a list of other guarding dynamic linkers. The first
 * value returned from a component linker other than null is returned. If no component linker returns an invocation,
 * null is returned.
 *
 * @author Attila Szegedi
 */
public class CompositeGuardingDynamicLinker implements GuardingDynamicLinker, Serializable {

    private static final long serialVersionUID = 1L;

    private final GuardingDynamicLinker[] linkers;

    /**
     * Creates a new composite linker.
     *
     * @param linkers a list of component linkers.
     */
    public CompositeGuardingDynamicLinker(Iterable<? extends GuardingDynamicLinker> linkers) {
        final List<GuardingDynamicLinker> l = new LinkedList<GuardingDynamicLinker>();
        for(GuardingDynamicLinker linker: linkers) {
            l.add(linker);
        }
        this.linkers = l.toArray(new GuardingDynamicLinker[l.size()]);
    }

    @Override
    public GuardedInvocation getGuardedInvocation(LinkRequest linkRequest, final LinkerServices linkerServices)
            throws Exception {
        for(final GuardingDynamicLinker linker: linkers) {
            final GuardedInvocation invocation = linker.getGuardedInvocation(linkRequest, linkerServices);
            if(invocation != null) {
                return invocation;
            }
        }
        return null;
    }
}