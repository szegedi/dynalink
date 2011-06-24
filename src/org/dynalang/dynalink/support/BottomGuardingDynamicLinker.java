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

import org.dynalang.dynalink.GuardedInvocation;
import org.dynalang.dynalink.LinkRequest;
import org.dynalang.dynalink.LinkerServices;
import org.dynalang.dynalink.TypeBasedGuardingDynamicLinker;

/**
 * A linker that can't link any call site. Only used internally by
 * {@link CompositeTypeBasedGuardingDynamicLinker}. Can be used by other
 * language runtimes if they need it though.
 * @author Attila Szegedi
 * @version $Id: $
 */
public class BottomGuardingDynamicLinker
implements TypeBasedGuardingDynamicLinker {

    /**
     * The sole instance of this stateless linker.
     */
    public static final BottomGuardingDynamicLinker INSTANCE =
        new BottomGuardingDynamicLinker();

    private BottomGuardingDynamicLinker() {
    }

    public boolean canLinkType(Class<?> type) {
        return false;
    }

    public GuardedInvocation getGuardedInvocation(LinkRequest linkRequest,
            LinkerServices linkerServices)
    {
        return null;
    }
}