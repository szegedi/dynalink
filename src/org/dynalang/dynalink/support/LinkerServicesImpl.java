package org.dynalang.dynalink.support;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

import org.dynalang.dynalink.GuardedInvocation;
import org.dynalang.dynalink.GuardingDynamicLinker;
import org.dynalang.dynalink.LinkRequest;
import org.dynalang.dynalink.LinkerServices;

/**
 * Default implementation of the {@link LinkerServices} interface.
 * @author Attila Szegedi
 * @version $Id: $
 */
public class LinkerServicesImpl implements LinkerServices {

    private final TypeConverterFactory typeConverterFactory;
    private final GuardingDynamicLinker topLevelLinker;

    /**
     * Creates a new linker services object.
     * @param typeConverterFactory the type converter factory exposed by the
     * services.
     * @param topLevelLinker the top level linker used by the services.
     */
    public LinkerServicesImpl(final TypeConverterFactory typeConverterFactory,
        final GuardingDynamicLinker topLevelLinker) {
        this.typeConverterFactory = typeConverterFactory;
        this.topLevelLinker = topLevelLinker;
    }

    public boolean canConvert(Class<?> from, Class<?> to) {
      return typeConverterFactory.canConvert(from, to);
    }

    @Override
    public MethodHandle convertArguments(MethodHandle handle, MethodType fromType) {
      return typeConverterFactory.convertArguments(handle, fromType);
    }

    @Override
    public GuardedInvocation getGuardedInvocation(LinkRequest linkRequest) throws Exception {
        return topLevelLinker.getGuardedInvocation(linkRequest, this);
    }
}