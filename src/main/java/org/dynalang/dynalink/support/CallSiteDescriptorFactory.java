package org.dynalang.dynalink.support;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;
import java.util.WeakHashMap;

import org.dynalang.dynalink.CallSiteDescriptor;

/**
 * Usable as a default factory for call site descriptor implementations. It is weakly canonicalizing, meaning it will
 * return the same immutable call site descriptor for identical inputs, i.e. repeated requests for a descriptor
 * signifying public lookup for "dyn:getProp:color" of type "Object(Object)" will return the same object as long as
 * a previously created, at least softly reachable one exists. It also uses several different implementations of the
 * {@link CallSiteDescriptor} internally, and chooses the most space-efficient one based on the input.
 * @author Attila Szegedi
 */
public class CallSiteDescriptorFactory {
    private static final String TOKEN_DELIMITER = ":";
    private static final WeakHashMap<CallSiteDescriptor, WeakReference<CallSiteDescriptor>> publicDescs =
            new WeakHashMap<>();


    private CallSiteDescriptorFactory() {
    }

    /**
     * Creates a new call site descriptor instance. The actual underlying class of the instance is dependent on the
     * passed arguments to be space efficient; i.e. if you  only use the public lookup, you'll get back an
     * implementation that doesn't waste space on storing the lookup object.
     * @param lookup the lookup that determines access rights at the call site. If your language runtime doesn't have
     * equivalents of Java access concepts, just use {@link MethodHandles#publicLookup()}. Must not be null.
     * @param name the name of the method at the call site. Must not be null.
     * @param methodType the type of the method at the call site. Must not be null.
     * @return a call site descriptor representing the input. Note that although the method name is "create", it will
     * in fact return a weakly-referenced canonical instance.
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
     * Tokenizes the composite name along colons, as well as {@link NameCodec#decode(String) demangles} and interns
     * the tokens. The first two tokens are not demangled as they are supposed to be the naming scheme and the name of
     * the operation which can be expected to consist of just alphabetical characters.
     * @param name the composite name consisting of colon-separated, possibly mangled tokens.
     * @return an array of tokens
     */
    public static String[] tokenizeName(String name) {
        final StringTokenizer tok = new StringTokenizer(name, TOKEN_DELIMITER);
        final String[] tokens = new String[tok.countTokens()];
        for(int i = 0; i < tokens.length; ++i) {
            String token = tok.nextToken();
            if(i > 1) {
                token = NameCodec.decode(token);
            }
            tokens[i] = token.intern();
        }
        return tokens;
    }

    /**
     * Tokenizes a composite operation name along pipe characters. I.e. if you have a "dyn:getElem|getProp|getMethod"
     * operation, returns a list of ["getElem", "getProp", "getMethod"]. The tokens are not interned.
     * @param desc the call site descriptor with the operation
     * @return a list of tokens
     */
    public static List<String> tokenizeOperators(CallSiteDescriptor desc) {
        final String ops = desc.getNameToken(CallSiteDescriptor.OPERATOR);
        final StringTokenizer tok = new StringTokenizer(ops, "|");
        final int count = tok.countTokens();
        if(count == 1) {
            return Collections.singletonList(ops);
        }
        final String[] tokens = new String[count];
        for(int i = 0; i < count; ++i) {
            tokens[i] = tok.nextToken();
        }
        return Arrays.asList(tokens);
    }
}