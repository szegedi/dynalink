package org.dynalang.dynalink.beans;

import java.lang.invoke.MethodHandle;
import java.util.List;

/**
 * Constructor extender is an optional component that can be passed to a {@link BeansLinker} constructor. When a beans
 * linker is constructed with a constructor extender, then the extender will be invoked once for every class on which
 * the "dyn:new" operation is attempted. The extender can provide additional constructors for the class. This allows
 * language runtimes to provide the common idiom of anonymous implementation of Java interfaces and abstract classes
 * by passing a native object representing the implementation as a constructor to the Class object representing the
 * interface or the abstract class.
 * @author Attila Szegedi
 * @version $Id: $
 */
public interface ConstructorExtender {
    /**
     * Given a class, returns a list containing method handles that can be used as additional constructors for the
     * class.
     * @param clazz the class whose constructor set is being extended
     * @return a list of additional constructor method handles. Must not be null, but can be an empty list if a
     * particular class can not be extended.
     */
    public List<MethodHandle> getAdditionalConstructors(Class<?> clazz);
}
