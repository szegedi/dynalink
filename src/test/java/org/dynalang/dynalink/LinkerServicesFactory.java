package org.dynalang.dynalink;

import java.util.LinkedList;

import org.dynalang.dynalink.linker.GuardingDynamicLinker;
import org.dynalang.dynalink.linker.GuardingTypeConverterFactory;
import org.dynalang.dynalink.linker.LinkerServices;
import org.dynalang.dynalink.support.LinkerServicesImpl;
import org.dynalang.dynalink.support.TypeConverterFactory;

public class LinkerServicesFactory {
    public static LinkerServices getLinkerServices(GuardingDynamicLinker linker) {
        return new LinkerServicesImpl(new TypeConverterFactory(new LinkedList<GuardingTypeConverterFactory>()), linker);
    }
}
