This is an implementation of invokedynamic-based multi-language linking and 
metaobject protocol framework. Such framework allows JVM classes emitted by one
language runtime to invoke methods, read and write properties, and so forth, on
objects from a different language runtime, within a single JVM instance.

It consists of two different parts:
* a linker composition framework that allows invokedynamic linkers for multiple
languages to be discovered in classpath, loaded, and composed into a single 
"master" linker, and
* a set of method naming and argument passing conventions, used through 
invokedynamic that make up the commonly understood metaobject protocol.

BUILDING
========
Here's how to build the JAR file:

Mandatory prerequisites:
* You will need a recent OpenJDK binary. You can grab one from Sun for Windows,
  Linux, and Solaris at <http://java.sun.com/javase/downloads/ea.jsp>. For Mac
  OS X on Intel CPUs, you can grab one from here:
  <http://www.szegedi.org/mlvm-macosx.html>
* You will need Apache Ant 1.7 or later.
* You will need Apache Ivy 2.0 or later set up with your Ant for dependency
  management. Just download Ivy from <http://ant.apache.org/ivy/> and drop 
  ivy-${versionNumber}.jar into your $ANT_HOME/lib. Note, the library actually
  has no external dependencies whatsoever for normal operation; all 
  dependencies are for testing.

Optional prerequisites:
* If you wish, you can use Rémi Forax's JSR-292 backport for Java 5 and 6. It
  is not used by the library as such, but if present, tests will be run with 
  both the OpenJDK as well as with the backport on whatever is the default JRE
  used by Ant.

Setting up:
* Set up the project in a directory. Preferrably create a "dynalang" directory
  to hold the "invoke" module, i.e. "dynalang/invoke". Ivy will create another
  directory, "dynalang/ivy" as part of the build process. That's facilitated so
  that local Ivy repo is shared across different dynalang modules (in case
  you're using others; you needn't for this module, it is self-contained). 
* You need to point out where's the OpenJDK. By default, the build script 
  expects it in 
  "${user.home}/Documents/projects/openjdk/bsd-port/build/bsd-i586/j2sdk-image"
  (which is actually a resonable default for people working on Mac OS X who
  build their own OpenJDK). If yours is elsewhere, create a file named 
  "build.properties" in the "invoke" directory, and specify
   
    openjdk.dir=path/to/my/openjdk
    
  in it.
* If you downloaded the JSR-292 backport, make sure you built it and then also
  set up
  
  backport.dir=path/to/my/backport
  
  in "build.properties" file. If you don't set it, it is by default looked up
  in "${user.home}/Documents/workspace/invokedynamic-backport" ('cause that's
  where Eclipse would put it by default when you check it out through SVN).
* Issue "ant jar". That's it. The JAR file is in the "build" subdirectory. You 
  can also try "ant test", and "ant doc" that creates JavaDoc. You might worry 
  that many of the tests fail. Unfortunately, both implementations of JSR-292 
  (in OpenJDK and in the backport) have a fair share of bugs at the moment...   

USING THE LIBRARY:
==================
Suppose you have a language runtime and want to both use the linker and MOP of
other languages (be able to access objects of other runtimes) and want other 
language runtimes to be serviced by your linker and MOP (be able to expose your
runtime's objects to other runtimes).

Here's what you need to do in detail:

USING THE LINKER FACILITY:
==========================
Have one class that creates a DynamicLinker and has a bootstrap method:

---
import java.dyn.*;
import org.dynalang.dynalink.*;

class MyLanguageRuntime {
    private static final DynamicLinker linker = 
        new DynamicLinkerFactory().createLinker();

    public static CallSite bootstrap(Class<?> caller, String name, 
        MethodType type)
    {
        final RelinkableCallSite callSite = new MonomorphicCallSite(caller, 
            name, type);
        linker.link(callSite);
        return callSite;
    }
}
---

Now, from every class you emit that uses invokedynamic, you need to do this:

---
static {
    java.dyn.Linkage.registerBootstrapMethod(MyLanguageRuntime.class, 
        "bootstrap");
} 
---

Note how you'll need to use a special subclass of CallSite named 
"RelinkableCallSite". It's actually an abstract class that allows its 
implementations to use any inline caching strategy they choose. A concrete 
subclass named "MonomorphicCallSite" is provided with the library that 
implements a monomorphic inline cache.
 
HAVING YOUR OWN LANGUAGE LINKER:
================================
That's it, now every invokedynamic call will go through the linker. The linker
created by the DynamicLinkerFactory actually manages a collection of instances 
of classes that all implement org.dynalang.dynalink.GuardingDynamicLinker 
interface. If your runtime has its own object model, you need to create an
implementation of this interface yourself to provide MOP functionality for your
own language. The DynamicLinkerFactory uses the JAR service mechanism, and will
look into a file named
"META-INF/services/org.dynalang.dynalink.GuardingDynamicLinker" in every JAR
file of the actual class loader[1]. Therefore, if you wish for your language
linker to be discovered by other language runtimes, you should have this file
in the JAR file of your language runtime distribution, and declare its class 
name in it.

However, when you are creating a linker for your own use, you might want to
explicitly create an instance of your guarding linker and make sure that the
master linker gives it priority. You can do this by changing the code for
creating the linker in "MyLanguageRuntime" to:

    private static final DynamicLinker linker;
    static {
        final DynamicLinkerFactory factory = new DynamicLinkerFactory();
+       final GuardingDynamicLinker myLanguageLinker = new MyLanguageLinker();
+       factory.setPrioritizedLinker(myLanguageLinker);
        linker = factory.createLinker();
    }

(Significant lines emphasized.) The factory is smart enough that even if it
discovers the MyLanguageLinker class through the JAR service mechanism, it will
ignore it if you supplied a precreated prioritized instance.

GUARDING LINKER?
================
Yes, the interface is named "GuardingDynamicLinker". It has a sole method with
this signature:

  public GuardedInvocation getGuardedInvocation(
      CallSiteDescriptor callSiteDescriptor, LinkerServices linkerServices,
      Object... arguments);
      
It is invoked for a particular invocation at particular call site. It needs to
inspect both the call site (mostly for its method name and types) and the 
actual arguments and figure out whether it can produce a MethodHandle as the
target for the call site. In ordinary circumstances, you'll check something
along the lines of: 

    if(arguments.length > 0 && arguments[0] instanceof IMyLanguageObject)
    
If not, return null - the master linker will then ask the next (if any) 
guarding linker. This is the base requirement for cross-language 
interoperability; you only deal with what you know, and pass on what you don't.
On the other hand, if you know what to do with the receiver object, then you'll
produce a method handle for handling the call *and* a guard method handle. 
Actually, the "GuardedInvocation" class above is nothing more than a value 
class, a tuple of two method handles - one for the invocation, one for the 
guard condition. Since your method handle is only valid under certain 
conditions (i.e. arguments[0] instanceof IMyLanguageObject), you will want to 
create a guard expressing this condition. The master linker will pass the guard
and the invocation to the call site, which will compose them into a new method 
handle according to its inline caching strategy. I.e. the MonomorphicCallSite 
will create a guardWithTest of the guard and the invocation, with fallback to 
the master linker's relink method when the guard fails. The main takeaway is 
that you needn't deal with any of that; just just need to provide the 
invocation and the guard.

WHAT'S LinkerServices?
======================
It's an interface provided to your linker with some extra methods your linker
might need. Currently it provides you with a convertArguments() method that 
looks much like MethodHandles.convertArguments(), except it will also inject 
language-specific type conversions when they are available in addition to the 
JVM specific ones provided by MethodHandles.convertArguments().

COOL, I WANT TO DEFINE MY OWN LANGUAGE TYPE CONVERSIONS
=======================================================
Sure thing. Just have your GuardingDynamicLinker also implement the optional
GuardingTypeConverterFactory interface. The linker framework will pick it up
and do the rest of its magic to make sure it ends up in the call path when
needed, as optimally as possible.

FINALLY, THE METAOBJECT PROTOCOL
================================
Finally, what kind of invocations to provide? What method names and signatures
to expect and react to? Also, what method names and signatures to emit in your
own invokedynamic instructions? For purposes of interoperability, we'll reserve
the method namespace "dyn" for the commonly-understood MOP, meaning every 
method name will start with "dyn:". The operations are:

1. Get property of an object with a constant name

Template: "dyn:getProp:${name}"(any-object-type)any-type

Example: 
  Source code: obj.temperature
  Bytecode: 
    ALOAD 2 # assume obj is in 2nd local variable
    INVOKEDYNAMIC "dyn:getProp:temperature"(Ljava/lang/Object;)Ljava/lang/Number;

Your GuardingDynamicLinker should recognize "dyn:getprop:name" as a property
getter for a fixed name. MethodHandles.convertArguments() or even 
MethodHandles.filterArguments() for custom value conversions might of course be
necessary both for receiver and return value.

2. Set property of an object with a constant name

Template: "dyn:setProp:${name}"(any-object-type,any-type)V

Example: 
  Source code: obj.temperature = 1;
  Bytecode: 
    ALOAD 2
    ICONST_1
    INVOKEDYNAMIC "dyn:setProp:temperature"(Ljava/lang/Object;I)V;

Your GuardingDynamicLinker should recognize "dyn:setprop:name" as a property
setter for a fixed name. MethodHandles.convertArguments() or even 
MethodHandles.filterArguments() for custom value conversions might of course be
necessary both for receiver and return value.

3. Get property of an object with a non-constant identifier

Template: "dyn:getProp"(any-object-type,any-type)any-type
Example:
  Source code: var a = "temperature"; obj[a]
  Bytecode:
    ALOAD 2 # assume 'obj' is in 2nd slot
    ALOAD 3 # assume 'a' is in 3rd slot
    INVOKEDYNAMIC "dyn:getProp"(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Number;
        
Your GuardingDynamicLinker should recognize "dyn:getprop" as a property
getter for a name that can change between invocations, and which is passed in
the arguments to the method handle. You probably shouldn't return a method 
handle that is fixed for the current value of the identifier (albeit you could 
if you also build the assumption into the guard). The expectation is that this 
will result in too frequent relinking, so you'd rather return a method handle 
that uses the value of the name. MethodHandles.convertArguments() or even 
MethodHandles.filterArguments() for custom value conversions might of course be
necessary. Note how the identifier argument can be of any type and is not
restricted to a java.lang.String. The reasoning behind this is that not every
language can prove the value will be a string at invocation time, and the 
language semantics can actually allow for, say, numeric IDs. Consider this in
JavaScript:

function x(d) {
    var arrayAndDict = ["arrayElement"];
    arrayAndDict.customProperty = "namedProperty";
    return arrayAndDict[d ? 0 : "customProperty"];
}

x(true) returns "arrayElement", x(false) returns "namedProperty". At the point
of invocation, the type of the property identifier is not known in advance.  

4. Set property of an object with a non-constant identifier

Template: "dyn:setProp"(any-object-type,any-type,any-type)V
Example:
  Source code: var a = "temperature"; obj[a] = 1;
  Bytecode:
    ALOAD 2 # assume 'obj' is in 2nd slot
    ALOAD 3 # assume 'a' is in 3rd slot
    ICONST_1
    INVOKEDYNAMIC "dyn:setProp"(Ljava/lang/Object;Ljava/lang/Object;I)V
        
Your GuardingDynamicLinker should recognize "dyn:setprop" as a property setter
for a name that can change between invocations. 
MethodHandles.convertArguments() or even MethodHandles.filterArguments() for 
custom value conversions might of course be necessary. Concerns about binding 
the method handle to the identifier expressed in (3) fully apply, as well as 
the reasoning behind allowing any type for the identifier.

5. Get element of a container object

Template: "dyn:getElem"(any-object-type,any-type)any-type
Example:
  Source code: var a = "temperature"; obj[a]
  Bytecode:
    ALOAD 2 # assume 'obj' is in 2nd slot
    ALOAD 3 # assume 'a' is in 3rd slot
    INVOKEDYNAMIC "dyn:getElem"(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Number;

Very similar to (3) getting a property of an object with a non-constant 
identifier, except it can be used by languages that distinguish between 
namespaces of properties and keyspaces of container objects (arrays, lists, 
maps). All considerations in (3) apply. Additionally, if your language makes no
distinction between the two, your GuardingDynamicLinker should respond to 
dyn:getElem identically as it would to dyn:getProp.

6. Set element of a container object

Template: "dyn:setElem"(any-object-type,any-type,any-type)V
Example:
  Source code: var a = "temperature"; obj[a] = 1;
  Bytecode:
    ALOAD 2 # assume 'obj' is in 2nd slot
    ALOAD 3 # assume 'a' is in 3rd slot
    ICONST_1
    INVOKEDYNAMIC "dyn:setElem"(Ljava/lang/Object;Ljava/lang/Object;I)V
        
Very similar to (4) setting a property of an object with a non-constant 
identifier, except it can be used by languages that distinguish between 
namespaces of properties and keyspaces of container objects (arrays, lists, 
maps). All considerations in (3) and (4) apply. Additionally, if your language 
makes no distinction between the two namespaces, your GuardingDynamicLinker 
should respond to dyn:setelem identically as it would to dyn:setprop.

6. Get length of a container object

Template: "dyn:getLength"(any-object-type)I
Example:
  Source code: a.length
  Bytecode:
    ALOAD 2 # assume 'a' is in 2nd slot
    INVOKEDYNAMIC "dyn:getLength"(Ljava/lang/Object)I

Returns the length of a container object. Expected to work on Java arrays, 
collections, and maps, as well as any other languages' container types.

===
Footnotes:
[1] By default, thread context class loader. The factory has a method for 
    setting a different class loader.
    
-- This file version is $Id: $