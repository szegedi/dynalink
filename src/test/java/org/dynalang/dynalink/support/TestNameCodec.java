package org.dynalang.dynalink.support;

import junit.framework.TestCase;

public class TestNameCodec extends TestCase {

    public void testAll() {
        testEncodeSame("foo");
        testEncode("foo:bar", "\\=foo\\!bar");
        testEncode("foo:bar", "\\=foo\\!bar");
        testEncode(":foo", "\\!foo");
        testEncode("foo\\", "\\=foo\\-");
        testEncode("foo\\", "\\=foo\\-");
        testEncode("", "\\=");
        testEncode("<", "\\^");
        testEncode("a/.;$<>[]:\\", "\\=a\\|\\,\\?\\%\\^\\_\\{\\}\\!\\-");

        testDecode("foo\\!", "foo\\!", "\\=foo\\-!"); // No unmangling when doesn't start with a backslash
        testDecode("\\foo\\!", "\\foo:", "\\-foo\\!"); // Unrecognized escape sequences are pass-through
        testDecode("\\!\\=woo", ":\\=woo", "\\!\\-=woo"); // \= Only recognized at start of the string
        testDecode("\\=foo\\", "foo\\", "\\=foo\\-"); // trailing backslash is allowed

    }

    private static void testEncodeSame(String s) {
        assertSame(s, NameCodec.encode(s));
    }

    private static void testEncode(String unmangled, String mangled) {
        assertEquals(mangled, NameCodec.encode(unmangled));
        // Mangling is reversible
        assertEquals(unmangled, NameCodec.decode(mangled));
    }

    private static void testDecode(String mangled, String unmangled, String remangled) {
        assertEquals(unmangled, NameCodec.decode(mangled));
        assertEquals(remangled, NameCodec.encode(unmangled));
    }
}
