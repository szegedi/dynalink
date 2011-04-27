package org.dynalang.dynalink.support;

import java.lang.invoke.MethodHandles;

/**
 * @author Attila Szegedi
 * @version $Id: $
 */
public class Backport
{
    /**
     * True if Remi's JSR-292 backport agent is active; false if we're using
     * native OpenJDK JSR-292 support.
     */
    public static final boolean inUse = 
        MethodHandles.class.getName().startsWith("jsr292");
}
