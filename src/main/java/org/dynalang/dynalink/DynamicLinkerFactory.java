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

import java.lang.invoke.MutableCallSite;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.dynalang.dynalink.beans.BeansLinker;
import org.dynalang.dynalink.linker.GuardingDynamicLinker;
import org.dynalang.dynalink.linker.GuardingTypeConverterFactory;
import org.dynalang.dynalink.support.AutoDiscovery;
import org.dynalang.dynalink.support.BottomGuardingDynamicLinker;
import org.dynalang.dynalink.support.CompositeGuardingDynamicLinker;
import org.dynalang.dynalink.support.CompositeTypeBasedGuardingDynamicLinker;
import org.dynalang.dynalink.support.LinkerServicesImpl;
import org.dynalang.dynalink.support.TypeConverterFactory;

/**
 * A factory class for creating dynamic linkers. The most usual dynamic linker is a linker that is a composition of all
 * {@link GuardingDynamicLinker guarding dynamic linkers} known and precreated by the caller as well as any
 * {@link AutoDiscovery automatically discovered} guarding linkers and the standard fallback {@link BeansLinker}. See
 * {@link DynamicLinker} documentation for tips on how to use this class.
 *
 * @author Attila Szegedi
 */
public class DynamicLinkerFactory {

    private ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    private List<? extends GuardingDynamicLinker> prioritizedLinkers;
    private List<? extends GuardingDynamicLinker> fallbackLinkers;
    private int runtimeContextArgCount = 0;
    private boolean syncOnRelink = false;

    /**
     * Sets the class loader for automatic discovery of available linkers. If not set explicitly, then the thread
     * context class loader at the time of the constructor invocation will be used.
     *
     * @param classLoader the class loader used for the autodiscovery of available linkers.
     */
    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * Sets the prioritized linkers. Language runtimes using this framework will usually precreate at least the linker
     * for their own language. These linkers will be consulted first in the resulting dynamic linker, before any
     * autodiscovered linkers. If the framework also autodiscovers a linker of the same class as one of the prioritized
     * linkers, it will be ignored and the explicit prioritized instance will be used.
     *
     * @param prioritizedLinkers the list of prioritized linkers. Null can be passed to indicate no prioritized linkers
     * (this is also the default value).
     */
    public void setPrioritizedLinkers(List<? extends GuardingDynamicLinker> prioritizedLinkers) {
        this.prioritizedLinkers =
                prioritizedLinkers == null ? null : new ArrayList<GuardingDynamicLinker>(prioritizedLinkers);
    }

    /**
     * Sets the prioritized linkers. Language runtimes using this framework will usually precreate at least the linker
     * for their own language. These linkers will be consulted first in the resulting dynamic linker, before any
     * autodiscovered linkers. If the framework also autodiscovers a linker of the same class as one of the prioritized
     * linkers, it will be ignored and the explicit prioritized instance will be used.
     *
     * @param prioritizedLinkers a list of prioritized linkers.
     */
    public void setPrioritizedLinkers(GuardingDynamicLinker... prioritizedLinkers) {
        setPrioritizedLinkers(Arrays.asList(prioritizedLinkers));
    }

    /**
     * Sets a single prioritized linker. Identical to calling {@link #setPrioritizedLinkers(List)} with a single-element
     * list.
     *
     * @param prioritizedLinker the single prioritized linker. Must not be null.
     * @throws IllegalArgumentException if null is passed.
     */
    public void setPrioritizedLinker(GuardingDynamicLinker prioritizedLinker) {
        if(prioritizedLinker == null) {
            throw new IllegalArgumentException("prioritizedLinker == null");
        }
        this.prioritizedLinkers = Collections.singletonList(prioritizedLinker);
    }

    /**
     * Sets the fallback linkers. These linkers will be consulted last in the resulting composite linker, after any
     * autodiscovered linkers. If the framework also autodiscovers a linker of the same class as one of the fallback
     * linkers, it will be ignored and the explicit fallback instance will be used.
     *
     * @param fallbackLinkers the list of fallback linkers. Can be empty to indicate the caller wishes to set no
     * fallback linkers.
     */
    public void setFallbackLinkers(List<? extends GuardingDynamicLinker> fallbackLinkers) {
        this.fallbackLinkers = fallbackLinkers == null ? null : new ArrayList<GuardingDynamicLinker>(fallbackLinkers);
    }

    /**
     * Sets the fallback linkers. These linkers will be consulted last in the resulting composite linker, after any
     * autodiscovered linkers. If the framework also autodiscovers a linker of the same class as one of the fallback
     * linkers, it will be ignored and the explicit fallback instance will be used.
     *
     * @param fallbackLinkers the list of fallback linkers. Can be empty to indicate the caller wishes to set no
     * fallback linkers. If it is left as null, the standard fallback {@link BeansLinker} will be used.
     */
    public void setFallbackLinkers(GuardingDynamicLinker... fallbackLinkers) {
        setFallbackLinkers(Arrays.asList(fallbackLinkers));
    }

    /**
     * Sets the number of arguments in the call sites that represent the stack context of the language runtime creating
     * the linker. If the language runtime uses no context information passed on stack, then it should be zero
     * (the default value). If it is set to nonzero value, then every dynamic call site emitted by this runtime must
     * have the argument list of the form: {@code (this, contextArg1[, contextArg2[, ...]], normalArgs)}. It is
     * advisable to only pass one context-specific argument, though, of an easily recognizable, runtime specific type
     * encapsulating the runtime thread local state.
     *
     * @param runtimeContextArgCount the number of language runtime context arguments in call sites.
     */
    public void setRuntimeContextArgCount(int runtimeContextArgCount) {
        if(runtimeContextArgCount < 0) {
            throw new IllegalArgumentException("runtimeContextArgCount < 0");
        }
        this.runtimeContextArgCount = runtimeContextArgCount;
    }

    /**
     * Sets whether the linker created by this factory will invoke {@link MutableCallSite#syncAll(MutableCallSite[])}
     * after a call site is relinked. Defaults to false. You probably want to set it to true if your runtime supports
     * multithreaded execution of dynamically linked code.
     * @param syncOnRelink true for invoking sync on relink, false otherwise.
     */
    public void setSyncOnRelink(boolean syncOnRelink) {
        this.syncOnRelink = syncOnRelink;
    }

    /**
     * Creates a new dynamic linker consisting of all the prioritized, autodiscovered, and fallback linkers.
     *
     * @return the new dynamic Linker
     */
    public DynamicLinker createLinker() {
        // Treat nulls appropriately
        if(prioritizedLinkers == null) {
            prioritizedLinkers = Collections.emptyList();
        }
        if(fallbackLinkers == null) {
            fallbackLinkers = Collections.singletonList(new BeansLinker());
        }

        // Gather classes of all precreated (prioritized and fallback) linkers.
        // We'll filter out any discovered linkers of the same class.
        final Set<Class<? extends GuardingDynamicLinker>> knownLinkerClasses =
                new HashSet<Class<? extends GuardingDynamicLinker>>();
        addClasses(knownLinkerClasses, prioritizedLinkers);
        addClasses(knownLinkerClasses, fallbackLinkers);

        final List<GuardingDynamicLinker> discovered = AutoDiscovery.loadLinkers(classLoader);
        // Now, concatenate ...
        final List<GuardingDynamicLinker> linkers =
                new ArrayList<GuardingDynamicLinker>(prioritizedLinkers.size() + discovered.size()
                        + fallbackLinkers.size());
        // ... prioritized linkers, ...
        linkers.addAll(prioritizedLinkers);
        // ... filtered discovered linkers, ...
        for(GuardingDynamicLinker linker: discovered) {
            if(!knownLinkerClasses.contains(linker.getClass())) {
                linkers.add(linker);
            }
        }
        // ... and finally fallback linkers.
        linkers.addAll(fallbackLinkers);
        final List<GuardingDynamicLinker> optimized = CompositeTypeBasedGuardingDynamicLinker.optimize(linkers);
        final GuardingDynamicLinker composite;
        switch(linkers.size()) {
            case 0: {
                composite = BottomGuardingDynamicLinker.INSTANCE;
                break;
            }
            case 1: {
                composite = optimized.get(0);
                break;
            }
            default: {
                composite = new CompositeGuardingDynamicLinker(optimized);
                break;
            }
        }

        final List<GuardingTypeConverterFactory> typeConverters = new LinkedList<GuardingTypeConverterFactory>();
        for(GuardingDynamicLinker linker: linkers) {
            if(linker instanceof GuardingTypeConverterFactory) {
                typeConverters.add((GuardingTypeConverterFactory)linker);
            }
        }
        return new DynamicLinker(new LinkerServicesImpl(new TypeConverterFactory(typeConverters), composite),
                runtimeContextArgCount, syncOnRelink);
    }

    private static void addClasses(Set<Class<? extends GuardingDynamicLinker>> knownLinkerClasses,
            List<? extends GuardingDynamicLinker> linkers) {
        for(GuardingDynamicLinker linker: linkers) {
            knownLinkerClasses.add(linker.getClass());
        }
    }
}