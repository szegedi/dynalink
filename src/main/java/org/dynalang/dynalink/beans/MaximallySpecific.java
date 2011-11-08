package org.dynalang.dynalink.beans;

import java.lang.invoke.MethodType;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.dynalang.dynalink.support.TypeUtilities;

/**
 * Utility class that encapsulates the algorithm for choosing the maximally
 * specific methods. It is generic and can thus work with an arbitrary
 * representation of a method.
 *
 * @author Attila Szegedi
 * @version $Id: $
 */
class MaximallySpecific {
    /**
     * Interface for a function that takes a method and returns its type.
     *
     * @author Attila Szegedi
     * @version $Id: $
     * @param <M> the method representation
     */
    interface TypeFunction<M> {
        /**
         * Takes a method, and returns its type.
         *
         * @param m the method
         * @return the method's type
         */
        MethodType type(M m);
    }

    /**
     * Given a list of methods and a function for retrieving their type, returns
     * a list of maximally specific methods.
     *
     * @param <M> the method representation class
     * @param methods the list of methods
     * @param typeFunction the type function
     * @param varArgs whether to assume the methods are varargs
     * @return the list of maximally specific methods.
     */
    static <M> List<M> getMaximallySpecificMethods(List<M> methods,
            TypeFunction<M> typeFunction, boolean varArgs) {
        if(methods.size() < 2) {
            return methods;
        }
        final LinkedList<M> maximals = new LinkedList<M>();
        for(M m: methods) {
            final MethodType methodType = typeFunction.type(m);
            boolean lessSpecific = false;
            for(Iterator<M> maximal = maximals.iterator(); maximal.hasNext();) {
                final M max = maximal.next();
                switch(isMoreSpecific(methodType, typeFunction.type(max),
                        varArgs)) {
                    case moreSpecific: {
                        maximal.remove();
                        break;
                    }
                    case lessSpecific: {
                        lessSpecific = true;
                        break;
                    }
                    case indeterminate: {
                        // do nothing
                    }
                }
            }
            if(!lessSpecific) {
                maximals.addLast(m);
            }
        }
        return maximals;
    }

    private enum Specificity {
        moreSpecific, lessSpecific, indeterminate
    }

    private static Specificity isMoreSpecific(MethodType t1, MethodType t2,
            boolean varArgs) {
        final int pc1 = t1.parameterCount();
        final int pc2 = t2.parameterCount();
        assert varArgs || pc1 == pc2;
        final int maxPc = Math.max(pc1, pc2);
        boolean t1MoreSpecific = false;
        boolean t2MoreSpecific = false;
        // NOTE: Starting from 1 as overloaded method resolution doesn't depend
        // on 0th element, which is the type of 'this'. We're only dealing with
        // instance methods here, not static methods.
        for(int i = 1; i < maxPc; ++i) {
            final Class<?> c1 = getParameterClass(t1, pc1, i, varArgs);
            final Class<?> c2 = getParameterClass(t2, pc2, i, varArgs);
            if(c1 != c2) {
                final boolean c1MoreSpecific = TypeUtilities.isSubtype(c1, c2);
                if(c1MoreSpecific && !t1MoreSpecific) {
                    t1MoreSpecific = true;
                    if(t2MoreSpecific) {
                        return Specificity.indeterminate;
                    }
                }
                final boolean c2MoreSpecific = TypeUtilities.isSubtype(c2, c1);
                if(c2MoreSpecific && !t2MoreSpecific) {
                    t2MoreSpecific = true;
                    if(t1MoreSpecific) {
                        return Specificity.indeterminate;
                    }
                }
            }
        }
        if(t1MoreSpecific) {
            return Specificity.moreSpecific;
        }
        if(t2MoreSpecific) {
            return Specificity.lessSpecific;
        }
        return Specificity.indeterminate;
    }

    private static Class<?> getParameterClass(MethodType t, int l, int i,
            boolean varArgs) {
        return varArgs && i >= l - 1 ? t.parameterType(l - 1)
                .getComponentType() : t.parameterType(i);
    }

}
