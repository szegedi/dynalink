package org.dynalang.dynalink;

/**
 *
 * @author Attila Szegedi
 * @version $Id: $
 */
public enum Results {
    /**
     * The requested property does not exist.
     */
    doesNotExist,
    /**
     * The metaobject protocol can't authoritatively perform the requested
     * operation on the object (the object is foreign to it).
     */
    noAuthority,
    /**
     * The target object that was attempted to be called does not support
     * calling (is not a callable) in the context of the attempted call
     * operation (either with positional or named arguments).
     */
    notCallable,
    /**
     * The property attempted to be deleted can not be deleted.
     */
    notDeleteable,
    /**
     * The property attempted to be read exists, but is not readable.
     */
    notReadable,
    /**
     * A suitable type representation for a value could not be obtained.
     */
    noRepresentation,
    /**
     * The property attempted to be written exists, but is not writable.
     */
    notWritable,
    /**
     * The operation succeeded.
     */
    ok,
}