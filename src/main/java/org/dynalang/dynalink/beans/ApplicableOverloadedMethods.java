package org.dynalang.dynalink.beans;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.LinkedList;
import java.util.List;

import org.dynalang.dynalink.support.TypeUtilities;

/**
 * Represents overloaded methods applicable to a specific call site signature.
 *
 * @author Attila Szegedi
 * @version $Id: $
 */
class ApplicableOverloadedMethods {
    private final List<MethodHandle> methods;
    private final boolean varArgs;

    /**
     * Creates a new ApplicableOverloadedMethods instance
     *
     * @param methods a list of all overloaded methods with the same name for a class.
     * @param callSiteType the type of the call site
     * @param test applicability test. One of {@link #APPLICABLE_BY_SUBTYPING},
     * {@link #APPLICABLE_BY_METHOD_INVOCATION_CONVERSION}, or {@link #APPLICABLE_BY_VARIABLE_ARITY}.
     */
    ApplicableOverloadedMethods(final List<MethodHandle> methods, final MethodType callSiteType,
            final ApplicabilityTest test) {
        this.methods = new LinkedList<MethodHandle>();
        for(MethodHandle m: methods) {
            if(test.isApplicable(callSiteType, m)) {
                this.methods.add(m);
            }
        }
        varArgs = test == APPLICABLE_BY_VARIABLE_ARITY;
    }

    /**
     * Retrieves all the methods this object holds.
     *
     * @return list of all methods.
     */
    List<MethodHandle> getMethods() {
        return methods;
    }

    /**
     * Returns a list of all methods in this objects that are maximally specific.
     *
     * @return a list of maximally specific methods.
     */
    List<MethodHandle> findMaximallySpecificMethods() {
        return MaximallySpecific.getMaximallySpecificMethods(methods, varArgs);
    }

    abstract static class ApplicabilityTest {
        abstract boolean isApplicable(MethodType callSiteType, MethodHandle method);
    }

    /**
     * Implements the applicability-by-subtyping test from JLS 15.12.2.2.
     */
    static final ApplicabilityTest APPLICABLE_BY_SUBTYPING = new ApplicabilityTest() {
        @Override
        boolean isApplicable(MethodType callSiteType, MethodHandle method) {
            final MethodType methodType = method.type();
            final int methodArity = methodType.parameterCount();
            if(methodArity != callSiteType.parameterCount()) {
                return false;
            }
            // 0th arg is receiver; it doesn't matter for overload
            // resolution.
            for(int i = 1; i < methodArity; ++i) {
                if(!TypeUtilities.isSubtype(callSiteType.parameterType(i), methodType.parameterType(i))) {
                    return false;
                }
            }
            return true;
        }
    };

    /**
     * Implements the applicability-by-method-invocation-conversion test from JLS 15.12.2.3.
     */
    static final ApplicabilityTest APPLICABLE_BY_METHOD_INVOCATION_CONVERSION = new ApplicabilityTest() {
        @Override
        boolean isApplicable(MethodType callSiteType, MethodHandle method) {
            final MethodType methodType = method.type();
            final int methodArity = methodType.parameterCount();
            if(methodArity != callSiteType.parameterCount()) {
                return false;
            }
            // 0th arg is receiver; it doesn't matter for overload
            // resolution.
            for(int i = 1; i < methodArity; ++i) {
                if(!TypeUtilities.isMethodInvocationConvertible(callSiteType.parameterType(i),
                        methodType.parameterType(i))) {
                    return false;
                }
            }
            return true;
        }
    };

    /**
     * Implements the applicability-by-variable-arity test from JLS 15.12.2.4.
     */
    static final ApplicabilityTest APPLICABLE_BY_VARIABLE_ARITY = new ApplicabilityTest() {
        @Override
        boolean isApplicable(MethodType callSiteType, MethodHandle method) {
            if(!method.isVarargsCollector()) {
                return false;
            }
            final MethodType methodType = method.type();
            final int methodArity = methodType.parameterCount();
            final int fixArity = methodArity - 1;
            final int callSiteArity = callSiteType.parameterCount();
            if(fixArity > callSiteArity) {
                return false;
            }
            // 0th arg is receiver; it doesn't matter for overload
            // resolution.
            for(int i = 1; i < fixArity; ++i) {
                if(!TypeUtilities.isMethodInvocationConvertible(callSiteType.parameterType(i),
                        methodType.parameterType(i))) {
                    return false;
                }
            }
            final Class<?> varArgType = methodType.parameterType(fixArity).getComponentType();
            for(int i = fixArity; i < callSiteArity; ++i) {
                if(!TypeUtilities.isMethodInvocationConvertible(callSiteType.parameterType(i), varArgType)) {
                    return false;
                }
            }
            return true;
        }
    };
}
