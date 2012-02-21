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
 * @version $Id: $
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