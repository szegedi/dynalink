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

import java.lang.invoke.Linkage;

/**
 * This class is used as a template for the dynamically created class that
 * uses invokedynamic in {@link GreeterDriverLoader}. We're using it with
 * ASM-ifier Eclipse plugin to generate the ASM code for it, then hand tweaking
 * it to use invokedynamic instead of the "Greeter" interface.
 * @author Attila Szegedi
 * @version $Id: $
 */
public class GreeterDriverImpl2 implements GreeterDriver
{
    static {
        Linkage.registerBootstrapMethod(GreeterDriverLoader.class, "bootstrap");
    }
    
    public String invokeGetHelloText(Object greeter) {
        return ((Greeter)greeter).getHelloText();
    }
}
