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

package org.dynalang.dynalink.beans;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.dynalang.dynalink.support.Lookup;

/**
 * A linker for java.lang.Class objects. Provides a synthetic property "static" that allows access to static fields and
 * methods on the class (respecting property getter/setter conventions). Note that Class objects are not recognized by
 * the Dynalink as constructors for the instances of the class, {@link StaticClass} is used for this purpose.
 * @author Attila Szegedi
 */
class ClassLinker extends BeanLinker {

    ClassLinker() {
        super(Class.class);
        // Map classObject.static to StaticClass.forClass(classObject)
        addPropertyGetter("static", FOR_CLASS, false);
    }

    private static final MethodHandle FOR_CLASS = new Lookup(MethodHandles.lookup()).findStatic(StaticClass.class,
            "forClass", MethodType.methodType(StaticClass.class, Class.class));

}