package org.dynalang.dynalink.support;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.util.StringTokenizer;

import org.dynalang.dynalink.linker.CallSiteDescriptor;

/**
 * Used as a factory for call site descriptor implementations. You shouldn't use this class directly
 * but rather use {@link CallSiteDescriptor#create(Lookup, String, MethodType, Object...)} instead.
 * @author Attila Szegedi
 * @version $Id: $
 */
public class CallSiteDescriptorFactory {
    private static final String TOKEN_DELIMITER = ":";

    /**
     * Creates a new call site descriptor instance. The actual underlying class of the instance is
     * dependent on the passed arguments to be space efficient; i.e. if you don't use either a
     * non-public lookup or static bootstrap arguments, you'll get back an implementation that
     * doesn't waste space on storing them. Note that you shouldn't directly call this method, but
     * you should rather use {@link CallSiteDescriptor#create(Lookup, String, MethodType, Object[])}
     * instead.
     * @param lookup the lookup that determines access rights at the call site. If your language
     * runtime doesn't have equivalents of Java access concepts, just use
     * {@link MethodHandles#publicLookup()}. Must not be null.
     * @param name the name of the method at the call site. Must not be null.
     * @param methodType the type of the method at the call site. Must not be null.
     * @param bootstrapArgs additional static bootstrap arguments. Can be null.
     * @return a new call site descriptor.
     */
    public static CallSiteDescriptor create(Lookup lookup, String name, MethodType methodType,
            Object... bootstrapArgs) {
        if(name == null) {
            throw new IllegalArgumentException("name == null");
        }
        if(methodType == null) {
            throw new IllegalArgumentException("methodType == null");
        }
        if(lookup == null) {
            throw new IllegalArgumentException("lookup == null");
        }
        final String[] tokenizedName = tokenizeName(name);
        if(bootstrapArgs == null || bootstrapArgs.length == 0) {
            if(isPublicLookup(lookup)) {
                return new DefaultCallSiteDescriptor(tokenizedName, methodType);
            } else {
                return new LookupCallSiteDescriptor(tokenizedName, methodType, lookup);
            }
        } else {
            final Object[] clonedArgs = bootstrapArgs.clone();
            if(isPublicLookup(lookup)) {
                return new ArgsCallSiteDescriptor(tokenizedName, methodType, clonedArgs);
            } else {
                return new LookupArgsCallSiteDescriptor(tokenizedName, methodType, lookup,
                        clonedArgs);
            }
        }
    }

    private static boolean isPublicLookup(Lookup lookup) {
        return lookup == MethodHandles.publicLookup();
    }

    private static String[] tokenizeName(String name) {
        final StringTokenizer tok = new StringTokenizer(name, TOKEN_DELIMITER);
        final String[] tokens = new String[tok.countTokens()];
        for(int i = 0; i < tokens.length; ++i) {
            tokens[i] = tok.nextToken().intern();
        }
        return tokens;
    }
}