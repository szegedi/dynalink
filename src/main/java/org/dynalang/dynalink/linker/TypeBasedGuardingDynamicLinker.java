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

package org.dynalang.dynalink.linker;

/**
 * A guarding dynamic linker that can determine whether it can link the call site solely based on the type of the first
 * argument at linking invocation time. (The first argument is usually the receiver class). Most language-specific
 * linkers will fall into this category, as they recognize their native objects as Java objects of classes implementing
 * a specific language-native interface or superclass. The linker mechanism can optimize the dispatch for these linkers.
 *
 * @author Attila Szegedi
 */
public interface TypeBasedGuardingDynamicLinker extends GuardingDynamicLinker {
    /**
     * Returns true if the linker can link an invocation where the first argument (receiver) is of the specified type.
     *
     * @param type the type to link
     * @return true if the linker can link calls for the receiver type, or false otherwise.
     */
    public boolean canLinkType(Class<?> type);
}
