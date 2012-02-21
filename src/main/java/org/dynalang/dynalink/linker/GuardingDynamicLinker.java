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

package org.dynalang.dynalink.linker;

/**
 * The base interface for language-specific dynamic linkers. Such linkers always have to produce method handles with
 * guards, as the validity of the method handle for calls at a call site inevitably depends on some condition (at the
 * very least, it depends on the receiver belonging to the language runtime of the linker). Language runtime
 * implementors will normally implement one for their own language, and declare it in the
 * <tt>META-INF/services/org.dynalang.dynalink.linker.GuardingDynamicLinker</tt> file within their JAR file.
 *
 * @author Attila Szegedi
 * @version $Id: $
 */
public interface GuardingDynamicLinker {
    /**
     * Creates a guarded invocation appropriate for a particular invocation with the specified arguments at a call site.
     *
     * @param linkRequest the object describing the request for linking a particular invocation
     * @param linkerServices linker services
     * @return a guarded invocation with a method handle suitable for the arguments, as well as a guard condition that
     * if fails should trigger relinking. Must return null if it can't resolve the invocation. If the returned
     * invocation is unconditional (which is actually quite rare), the guard in the return value can be null. The
     * invocation can also have a switch point for asynchronous invalidation of the linkage. If the linker does not
     * recognize any native language runtime contexts in arguments, or does recognize its own, but receives a call site
     * descriptor without its recognized context in the arguments, it should invoke
     * {@link LinkRequest#withoutRuntimeContext()} and link for that.
     * @throws Exception if the operation fails for whatever reason
     */
    public GuardedInvocation getGuardedInvocation(LinkRequest linkRequest, LinkerServices linkerServices)
            throws Exception;
}