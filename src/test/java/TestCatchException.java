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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.dynalang.dynalink.support.Lookup;

public class TestCatchException {
    public static void main(String[] args) throws Throwable {
        MethodHandle throwing = findStatic("throwing");
        MethodHandle catching = findStatic("catching");
        MethodHandles.catchException(throwing, MyException.class,
                MethodHandles.dropArguments(catching, 0, MyException.class));
    }

    private static class MyException extends RuntimeException {
    }

    private static MethodHandle findStatic(String name) {
        return Lookup.PUBLIC.findStatic(TestCatchException.class, name, MethodType.methodType(int.class, Object.class));
    }

    public static int throwing(Object o) {
        throw new IllegalArgumentException();
    }

    public static int catching(Object o) {
        return 0;
    }
}