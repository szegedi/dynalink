/*
   Copyright 2009-2013 Attila Szegedi

   Licensed under either the Apache License, Version 2.0 (the "Apache
   License") or the 3-clause BSD License (the "BSD License"), with licensee
   being free to choose either of the two at their discretion.

   You may not use this file except in compliance with either the Apache
   License or the BSD License.

   A copy of the BSD License is available in the root directory of the
   source distribution of the project under the file name
   "LICENSE-BSD.txt".

   A copy of the Apache License is available in the root directory of the
   source distribution of the project under the file name
   "LICENSE-Apache-2.0.txt". Alternatively, you may obtain a copy of the
   Apache License at <http://www.apache.org/licenses/LICENSE-2.0>

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See your chosen License for the specific language governing permissions
   and limitations under that License.
*/

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class TestCollectArguments {
    public static void main(String[] args) throws Throwable {
        MethodHandle xs =
                MethodHandles.publicLookup().findVirtual(TestCollectArguments.class, "xs",
                        MethodType.methodType(String.class, String.class, String[].class));
        // This works
        System.out.println(xs.invokeWithArguments(new TestCollectArguments(), "a", new String[] { "b", "c" }));

        // This fails
        try {
            System.out.println(xs.invokeWithArguments(new TestCollectArguments(), "a", "b", "c"));
        } catch(ClassCastException e) {
            e.printStackTrace();
        }

        // This fails too
        try {
            System.out.println(xs.asCollector(String[].class, 2).invokeWithArguments(new TestCollectArguments(), "a",
                    "b", "c"));
        } catch(ClassCastException e) {
            e.printStackTrace();
        }
    }

    public String xs(String y, String... z) {
        for(String zz: z) {
            y += zz;
        }
        return y;
    }
}