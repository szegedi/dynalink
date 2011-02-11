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
package org.dynalang.dynalink;

import java.dyn.MethodHandles;
import java.dyn.MethodType;

import junit.framework.TestCase;

import org.dynalang.dynalink.MonomorphicCallSite;
import org.dynalang.dynalink.RelinkableCallSite;

/**
 * Tests for the {@link RelinkableCallSite}.
 * @author Attila Szegedi
 * @version $Id: $
 */
public class TestRelinkableCallSite extends TestCase
{
    /**
     * Tests against allowing setting null as the relink method.
     */
    public void testNullRelink()
    {
        try
        {
            new MonomorphicCallSite("", MethodType.methodType(Void.TYPE)).setRelink(
                    null);
            fail();
        }
        catch(IllegalArgumentException e)
        {
            // This is expected
        }
    }

    /**
     * Tests against allowing relink to be called twice.
     */
    public void testRelinkSetTwice()
    {
        RelinkableCallSite cs = new MonomorphicCallSite("", MethodType.methodType(
                Object.class));
        cs.setRelink(MethodHandles.constant(Object.class, new Object()));
        try
        {
            cs.setRelink(MethodHandles.constant(Object.class, new Object()));
            fail();
        }
        catch(IllegalStateException e)
        {
            // This is expected
        }
    }
}