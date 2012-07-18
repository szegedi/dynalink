package org.dynalang.dynalink;

/**
 * Describes the results of various standard metaobject protocol operations. Used as the return value of those
 * operations.
 * @author Attila Szegedi
 */
public enum Results {
    /**
     * The requested property does not exist.
     */
    doesNotExist,
    /**
     * The property attempted to be read exists, but is not readable.
     */
    notReadable,
    /**
     * The property attempted to be written exists, but is not writable.
     */
    notWritable,
    /**
     * The operation succeeded.
     */
    ok,
}