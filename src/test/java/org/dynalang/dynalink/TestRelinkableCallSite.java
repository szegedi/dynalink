/*
   Copyright 2009-2012 Attila Szegedi

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

package org.dynalang.dynalink;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.dynalang.dynalink.linker.CallSiteDescriptor;

import junit.framework.TestCase;

/**
 * Tests for the {@link RelinkableCallSite}.
 *
 * @author Attila Szegedi
 * @version $Id: $
 */
public class TestRelinkableCallSite extends TestCase {
    /**
     * Tests against allowing setting null as the relink method.
     */
    public static void testNullRelink() {
        try {
            createCallSite(MethodType.methodType(Void.TYPE)).setTarget(null);
            fail();
        } catch(NullPointerException e) {
            // This is expected
        }
    }

    private static MonomorphicCallSite createCallSite(MethodType type) {
        return new MonomorphicCallSite(CallSiteDescriptor.create(MethodHandles.publicLookup(), "", type));
    }
}