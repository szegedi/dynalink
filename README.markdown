Dynalink is an invokedynamic-based high-level linking and metaobject 
protocol library. It enables creation of languages on the JVM that can 
easily interoperate with plain Java objects and each other.

It lets you write your language runtime in a way where you can think in
higher-level abstractions than the invokedynamic bytecode instruction.

See the [Online documentation](https://github.com/szegedi/dynalink/wiki) to 
get started.

You can also check out the [video of the talk on Dynalink](http://medianetwork.oracle.com/video/player/1113272541001)
given during the 2011 JVM Language Summit, as well as 
[slides and video of the talk about integration of Dynalink and Nashorn](https://oracleus.activeevents.com/connect/sessionDetail.ww?SESSION_ID=5251)
given during JavaOne 2012.

If you just want the binaries, you can get the releases from the 
[Maven central repository](http://search.maven.org/#browse%7C-362742625). 
Frequent snapshots of the version under development are available in the [
Maven snapshot repository](https://oss.sonatype.org/content/repositories/snapshots/org/dynalang/dynalink).

Dynalink is Open Source software, dual-licensed under both the Apache 2.0
license and 3-clause BSD license, allowing you to choose either one when you
use Dynalink. When you choose one license, it will be the only one that
applies to you, the other does not. See the [Licensing FAQ](https://github.com/szegedi/dynalink/wiki/Licensing-FAQ)
for advice on which one to choose.

[![Build Status](https://secure.travis-ci.org/szegedi/dynalink.png)](http://travis-ci.org/szegedi/dynalink)
