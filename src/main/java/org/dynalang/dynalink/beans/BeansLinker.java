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

import java.beans.BeanInfo;
import java.lang.invoke.MethodHandles;

import org.dynalang.dynalink.CallSiteDescriptor;
import org.dynalang.dynalink.DynamicLinkerFactory;
import org.dynalang.dynalink.linker.GuardedInvocation;
import org.dynalang.dynalink.linker.GuardingDynamicLinker;
import org.dynalang.dynalink.linker.LinkRequest;
import org.dynalang.dynalink.linker.LinkerServices;
import org.dynalang.dynalink.linker.TypeBasedGuardingDynamicLinker;

/**
 * A linker for POJOs. Normally used as the ultimate fallback linker by the {@link DynamicLinkerFactory} so it is given
 * the chance to link calls to all objects that no other language runtime recognizes. Specifically, this linker will:
 * <ul>
 * <li>expose all public methods of form {@code setXxx()}, {@code getXxx()}, and {@code isXxx()} as property setters and
 * getters for {@code dyn:setProp} and {@code dyn:getProp} operations;</li>
 * <li>expose all property getters and setters declared by the class' {@link BeanInfo};</li>
 * <li>expose all public methods and methods declared by the class' {@link BeanInfo} for invocation through
 * {@code dyn:callMethod} operation;</li>
 * <li>expose all public methods and methods declared by the class' {@link BeanInfo} for retrieval for
 * {@code dyn:getMethod} operation; the methods thus retrieved can then be invoked using {@code dyn:call};</li>
 * <li>expose all public fields as properties, unless there are getters or setters for the properties of the same name;</li>
 * <li>expose {@code dyn:getLength}, {@code dyn:getElem} and {@code dyn:setElem} on native Java arrays, as well as
 * {@link java.util.List} and {@link java.util.Map} objects; ({@code dyn:getLength} works on any
 * {@link java.util.Collection});</li>
 * <li>expose a virtual property named {@code length} on Java arrays;</li>
 * <li>expose {@code dyn:new} on instances of {@link StaticClass} as calls to constructors, including those static class
 * objects that represent Java arrays (their constructors take a single {@code int} parameter representing the length of
 * the array to create);</li>
 * <li>expose static methods, fields, and properties of classes in a similar manner to how instance method, fields, and
 * properties are exposed, on {@link StaticClass} objects.</li>
 * <li>expose a virtual property named {@code static} on instances of {@link java.lang.Class} to access their
 * {@link StaticClass}.</li>
 * </ul>
 * <p><strong>Overloaded method resolution</strong> is performed automatically for property setters, methods, and
 * constructors. Additionally, manual overloaded method selection is supported by having a call site specify a name for
 * a method that contains an explicit signature, i.e. {@code dyn:getMethod:parseInt(String,int)}. You can use
 * non-qualified class names in such signatures regardless of those classes' packages, they will match any class with
 * the same non-qualified name. You only have to use a fully qualified class name in case non-qualified class names
 * would cause selection ambiguity (that is extremely rare).</p>
 * <p><strong>Variable argument invocation</strong> is handled for both methods and constructors.</p>
 * <p>Currently, only public fields and methods are supported. Any Lookup objects passed in the
 * {@link LinkRequest}s are ignored and {@link MethodHandles#publicLookup()} is used instead.</p>
 *
 * @author Attila Szegedi
 */
public class BeansLinker implements GuardingDynamicLinker {
    private static final ClassValue<TypeBasedGuardingDynamicLinker> linkers = new ClassValue<TypeBasedGuardingDynamicLinker>() {
        @Override
        protected TypeBasedGuardingDynamicLinker computeValue(Class<?> clazz) {
            // If ClassValue.put() were public, we could just pre-populate with these known mappings...
            return
                clazz == Class.class ? new ClassLinker() :
                clazz == StaticClass.class ? new StaticClassLinker() :
                DynamicMethod.class.isAssignableFrom(clazz) ? new DynamicMethodLinker() :
                new BeanLinker(clazz);
        }
    };

    /**
     * Creates a new POJO linker.
     */
    public BeansLinker() {
    }

    /**
     * Returns a bean linker for a particular single class. Useful when you need to override or extend the behavior of
     * linking for some classes in your language runtime's linker, but still want to delegate to the default behavior in
     * some cases.
     * @param clazz the class
     * @return a bean linker for that class
     */
    public static TypeBasedGuardingDynamicLinker getLinkerForClass(Class<?> clazz) {
        return linkers.get(clazz);
    }

    @Override
    public GuardedInvocation getGuardedInvocation(LinkRequest request, final LinkerServices linkerServices)
            throws Exception {
        final CallSiteDescriptor callSiteDescriptor = request.getCallSiteDescriptor();
        final int l = callSiteDescriptor.getNameTokenCount();
        // All names conforming to the dynalang MOP should have at least two tokens, the first one being "dyn"
        if(l < 2 || "dyn" != callSiteDescriptor.getNameToken(CallSiteDescriptor.SCHEME)) {
            return null;
        }

        final Object receiver = request.getReceiver();
        if(receiver == null) {
            // Can't operate on null
            return null;
        }
        return getLinkerForClass(receiver.getClass()).getGuardedInvocation(request, linkerServices);
    }
}