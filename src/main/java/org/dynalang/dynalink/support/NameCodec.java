package org.dynalang.dynalink.support;

/**
 * Implements the name mangling and demangling as specified by John Rose's
 * <a href="https://blogs.oracle.com/jrose/entry/symbolic_freedom_in_the_vm">"Symbolic Freedom in the VM" article</a>.
 * It is recommended that implementers of languages on the JVM uniformly adopt this for symbolic interoperability
 * between languages.
 *
 * @author Attila Szegedi
 */
public class NameCodec {
    private static final char ESCAPE_CHAR = '\\';
    private static final char EMPTY_ESCAPE = '=';
    private static final String EMPTY_NAME = new String(new char[] { ESCAPE_CHAR, EMPTY_ESCAPE });
    private static final char EMPTY_CHAR = 0xFEFF;

    private static final int MIN_ENCODING = '$';
    private static final int MAX_ENCODING = ']';
    private static final char[] ENCODING = new char[MAX_ENCODING - MIN_ENCODING + 1];
    private static final int MIN_DECODING = '!';
    private static final int MAX_DECODING = '}';
    private static final char[] DECODING = new char[MAX_DECODING - MIN_DECODING + 1];

    static {
        addEncoding('/', '|');
        addEncoding('.', ',');
        addEncoding(';', '?');
        addEncoding('$', '%');
        addEncoding('<', '^');
        addEncoding('>', '_');
        addEncoding('[', '{');
        addEncoding(']', '}');
        addEncoding(':', '!');
        addEncoding('\\', '-');
        DECODING[EMPTY_ESCAPE - MIN_DECODING] = EMPTY_CHAR;
    }

    private NameCodec() {
    }

    /**
     * Encodes ("mangles") an unencoded symbolic name.
     * @param name the symbolic name to mangle
     * @return the mangled form of the symbolic name.
     */
    public static String encode(String name) {
        final int l = name.length();
        if(l == 0) {
            return EMPTY_NAME;
        }
        StringBuilder b = null;
        int lastEscape = -1;
        for(int i = 0; i < l; ++i) {
            final int encodeIndex = name.charAt(i) - MIN_ENCODING;
            if(encodeIndex >= 0 && encodeIndex < ENCODING.length) {
                final char e = ENCODING[encodeIndex];
                if(e != 0) {
                    if(b == null) {
                        b = new StringBuilder(name.length() + 3);
                        if(name.charAt(0) != ESCAPE_CHAR && i > 0) {
                            b.append(EMPTY_NAME);
                        }
                        b.append(name, 0, i);
                    } else {
                        b.append(name, lastEscape + 1, i);
                    }
                    b.append(ESCAPE_CHAR).append(e);
                    lastEscape = i;
                }
            }
        }
        if(b == null) {
            return name.toString();
        }
        assert lastEscape != -1;
        b.append(name, lastEscape + 1, l);
        return b.toString();
    }

    /**
     * Decodes ("demangles") an encoded symbolic name.
     * @param name the symbolic name to demangle
     * @return the demangled form of the symbolic name.
     */
    public static String decode(String name) {
        if(name.charAt(0) != ESCAPE_CHAR) {
            return name;
        }
        final int l = name.length();
        if(l == 2 && name.charAt(1) == EMPTY_CHAR) {
            return "";
        }
        StringBuilder b = new StringBuilder(name.length());
        int lastEscape = -2;
        int lastBackslash = -1;
        for(;;) {
            int nextBackslash = name.indexOf(ESCAPE_CHAR, lastBackslash + 1);
            if(nextBackslash == -1 || nextBackslash == l - 1) {
                break;
            }
            final int decodeIndex = name.charAt(nextBackslash + 1) - MIN_DECODING;
            if(decodeIndex >= 0 && decodeIndex < DECODING.length) {
                final char d = DECODING[decodeIndex];
                if(d == EMPTY_CHAR) {
                    // "\=" is only valid at the beginning of a mangled string
                    if(nextBackslash == 0) {
                        lastEscape = 0;
                    }
                } else if(d != 0) {
                    b.append(name, lastEscape + 2, nextBackslash).append(d);
                    lastEscape = nextBackslash;
                }
            }
            lastBackslash = nextBackslash;
        }
        b.append(name, lastEscape + 2, l);
        return b.toString();
    }

    private static void addEncoding(char from, char to) {
        ENCODING[from - MIN_ENCODING] = to;
        DECODING[to - MIN_DECODING] = from;
    }
}
