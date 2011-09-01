This is an implementation of invokedynamic-based multi-language linking and
metaobject protocol framework. Such framework allows JVM classes emitted by
one language runtime to invoke methods, read and write properties, and so
forth, on objects from a different language runtime, within a single JVM
instance.

See the [User guide](https://github.com/szegedi/dynalink/wiki) and the
[Javadoc](http://szegedi.github.com/dynalink/javadoc/index.html) for details.
Both are fairly comprehensive and should get you started quickly.

You can also check out the [video](http://medianetwork.oracle.com/media/show/17012)
of the talk given on Dynalink on the 2011 JVM Language Summit.

If you just want the binaries, you can get them from the 
[Maven repository](http://search.maven.org/#browse%7C-362742625).

Dynalink is Open Source software, licensed under the Apache 2.0 license.