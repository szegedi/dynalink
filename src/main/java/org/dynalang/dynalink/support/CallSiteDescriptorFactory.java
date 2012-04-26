package org.dynalang.dynalink.support;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.ref.WeakReference;
import java.util.StringTokenizer;
import java.util.WeakHashMap;

import org.dynalang.dynalink.linker.CallSiteDescriptor;

/**
 * Used as a factory for call site descriptor implementations.
 * @author Attila Szegedi
 * @version $Id: $
 */
public class CallSiteDescriptorFactory {
    private static final String TOKEN_DELIMITER = ":";
    private static final WeakHashMap<CallSiteDescriptor, WeakReference<CallSiteDescriptor>> publicDescs =
            new WeakHashMap<>();

    /**
     * Creates a new call site descriptor instance. The actual underlying class of the instance is dependent on the
     * passed arguments to be space efficient; i.e. if you don't use non-public lookup, you'll get back an
     * implementation that doesn't waste space on storing them.
     * @param lookup the lookup that determines access rights at the call site. If your language runtime doesn't have
     * equivalents of Java access concepts, just use {@link MethodHandles#publicLookup()}. Must not be null.
     * @param name the name of the method at the call site. Must not be null.
     * @param methodType the type of the method at the call site. Must not be null.
     * @return a new call site descriptor.
     */
    public static CallSiteDescriptor create(Lookup lookup, String name, MethodType methodType) {
        name.getClass(); // NPE check
        methodType.getClass(); // NPE check
        lookup.getClass(); // NPE check
        final String[] tokenizedName = tokenizeName(name);
        if(isPublicLookup(lookup)) {
            return getCanonicalPublicDescriptor(createPublicCallSiteDescriptor(tokenizedName, methodType));
        } else {
            return new LookupCallSiteDescriptor(tokenizedName, methodType, lookup);
        }
    }

    static CallSiteDescriptor getCanonicalPublicDescriptor(final CallSiteDescriptor desc) {
        synchronized(publicDescs) {
            final WeakReference<CallSiteDescriptor> ref = publicDescs.get(desc);
            if(ref != null) {
                final CallSiteDescriptor canonical = ref.get();
                if(canonical != null) {
                    return canonical;
                }
            }
            publicDescs.put(desc, new WeakReference<CallSiteDescriptor>(desc));
        }
        return desc;
    }

    private static CallSiteDescriptor createPublicCallSiteDescriptor(String[] tokenizedName, MethodType methodType) {
        final int l = tokenizedName.length;
        if(l > 0 && tokenizedName[0] == "dyn") {
            if(l == 2) {
                return new UnnamedDynCallSiteDescriptor(tokenizedName[1], methodType);
            } if (l == 3) {
                return new NamedDynCallSiteDescriptor(tokenizedName[1], tokenizedName[2], methodType);
            }
        }
        return new DefaultCallSiteDescriptor(tokenizedName, methodType);
    }

    private static boolean isPublicLookup(Lookup lookup) {
        return lookup == MethodHandles.publicLookup();
    }

    /**
     * Tokenizes the compoiste name along semicolons and interns the tokens.
     * @param name the composite name
     * @return an array of tokens
     */
    public static String[] tokenizeName(String name) {
        final StringTokenizer tok = new StringTokenizer(name, TOKEN_DELIMITER);
        final String[] tokens = new String[tok.countTokens()];
        for(int i = 0; i < tokens.length; ++i) {
            tokens[i] = tok.nextToken().intern();
        }
        return tokens;
    }
}