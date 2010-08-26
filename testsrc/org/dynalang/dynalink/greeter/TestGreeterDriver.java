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
package org.dynalang.dynalink.greeter;

import junit.framework.TestCase;

public class TestGreeterDriver extends TestCase
{
    public void testAll() throws Exception {
        ClassLoader cl = new GreeterDriverLoader(Thread.currentThread().getContextClassLoader());
        GreeterDriver gd = (GreeterDriver)Class.forName("org.dynalang.dynalink.greeter.GreeterDriverImpl", true, cl).newInstance();
        assertEquals("hello1", gd.invokeGetHelloText(new Hello1()));
        assertEquals("hello2", gd.invokeGetHelloText(new Hello2()));
        assertEquals("hello1", gd.invokeGetHelloText(new Hello1()));
        // 3 links
        assertEquals(3, GreeterDriverLoader.linkCount);
        GreeterDriverLoader.linkCount = 0;
        assertEquals("hello2", gd.invokeGetHelloText(new Hello2()));
        assertEquals("hello1", gd.invokeGetHelloText(new Hello1()));
        assertEquals("hello1", gd.invokeGetHelloText(new Hello1()));
        // 2 links
        assertEquals(2, GreeterDriverLoader.linkCount);
        GreeterDriverLoader.linkCount = 0;
    }
}
