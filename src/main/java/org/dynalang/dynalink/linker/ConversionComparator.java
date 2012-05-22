package org.dynalang.dynalink.linker;


/**
 * Optional interface to be implemented by {@link GuardingTypeConverterFactory} implementers. Language-specific
 * conversions can cause increased overloaded method resolution ambiguity, as many methods can become applicable because
 * of additional conversions. The static way of selecting the "most specific" method will fail more often, because there
 * will be multiple maximally specific method with unrelated signatures. In these cases, language runtimes can be asked
 * to resolve the ambiguity by expressing preferences for one conversion over the other.
 * @author Attila Szegedi
 * @version $Id: $
 */
public interface ConversionComparator {

    enum Comparison {
        INDETERMINATE,
        TYPE_1_BETTER,
        TYPE_2_BETTER,
    }

    /**
     * Determines which of the two type conversions is preferred.
     * @param sourceType the source type.
     * @param targetType1 one potential target type
     * @param targetType2 another potential target type.
     * @return one of Comparison constants that establish which - if any - of the target types is preferrable for the
     * conversion.
     */
    public Comparison compareConversion(Class<?> sourceType, Class<?> targetType1, Class<?> targetType2);
}
