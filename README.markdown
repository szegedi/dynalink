This is an implementation of invokedynamic-based multi-language linking and
metaobject protocol framework. Such framework allows JVM classes emitted by
one language runtime to invoke methods, read and write properties, and so
forth, on objects from a different language runtime, within a single JVM
instance.

It consists of two different parts:

* a linker composition framework that allows invokedynamic linkers for
multiple languages to be discovered in classpath, loaded, and composed into a
single "master" linker, and
* a set of method naming and argument passing conventions, used through
invokedynamic that make up the commonly understood metaobject protocol.

As an added bonus, it also contains a POJO linker that allows your language to
link with plain Java objects. It manages conformance to JavaBeans
specification, and provides full support for vararg methods and optimized
overloaded method resolution.

Building
========
Here's how to build the JAR file:

    ant jar

Note that this will download a private copy of OpenJDK into the build
directory (about 60MB). The build process has been tested to work on Mac OS X.
There is experimental build support for Linux. There is currently no build
support on Windows.

Here's how to test the JAR file:

    ant test

Known issues
============

* It doesn't currently work with Rémi Forax's JSR-292 backport, as the backport
was not updated to reflect newest JSR-292

Using the library
=================
Suppose you have a language runtime and want to both use the linker and MOP of
other languages (be able to access objects of other runtimes) and want other
language runtimes to be serviced by your linker and MOP (be able to expose your
runtime's objects to other runtimes).

Here's what you need to do in detail:

Using the linker facility
-------------------------
Have one class that creates a DynamicLinker and has a bootstrap method:
```java
import java.lang.invoke.*;
import org.dynalang.dynalink.*;

class MyLanguageRuntime {
    private static final DynamicLinker linker =
        new DynamicLinkerFactory().createLinker();

    public static CallSite bootstrap(MethodHandles.Lookup lookup, String name,
        MethodType type)
    {
        final MonomorphicCallSite callSite = new MonomorphicCallSite(name, type);
        linker.link(callSite);
        return callSite;
    }
}
```
Now, from every class you emit that uses invokedynamic, you would need to emit
an invokedynamic instruction that specifies this method as its bootstrap
method. I.e. if you used the ASM 4 library and wanted to emit an invokedynamic
call to a property getter "color" that you'd expect to return a string, you'd
do:
```java
mv.visitIndyMethodInsn("dyn:getProp:color", "(Ljava/lang/Object;)Ljava/lang/String;",
    new MHandle(MHandle.REF_invokeStatic, "org/mycompany/mylanguage/MyLanguageRuntime",
    "bootstrap", MethodType.methodType(CallSite.class,
        MethodHandles.Lookup.class, String.class, MethodType.class).toMethodDescriptorString()),
    new Object[0]);
```
Note how you used `MonomorphicCallSite`, a special subclass of CallSite provided
by the library that implements a monomorphic inline cache. It is actually a
subclass of another library class, `RelinkableCallSite` that allows you to
implement any inline caching strategy you wish.

Even easier use of the linker facility
--------------------------------------
The above code for creating the bootstrap method and the default dynamic linker
is so generic, that the library actually provides the class named
`org.dynalang.dynalink.DefaultBootstrapper` as a convenience that
actually implements the above functionality, so you can simply replace the name
of your bootstrapper class in the above ASM 4 example for dynamically getting
the property "color" of an object with the name of the default bootstrapper:
```java
mv.visitIndyMethodInsn("dyn:getProp:color", "(Ljava/lang/Object;)Ljava/lang/String;",
    new MHandle(MHandle.REF_invokeStatic, "org/dynalang/dynalink/support/DefaultBootstrapper",
    "bootstrap", MethodType.methodType(CallSite.class,
        MethodHandles.Lookup.class, String.class, MethodType.class).toMethodDescriptorString()),
    new Object[0]);
```
And you need not write your own bootstrap method.

Having your own language linker
-------------------------------
That's it, now every `invokedynamic` call will go through the linker. The
linker created by the `DynamicLinkerFactory` actually manages a collection of
instances of classes that all implement
`org.dynalang.dynalink.linker.GuardingDynamicLinker` interface. If your runtime has
its own object model, you need to create an implementation of this interface
yourself to provide MOP functionality for your own language. The
`DynamicLinkerFactory` uses the JAR service mechanism, and will look into a
file named `META-INF/services/org.dynalang.dynalink.linker.GuardingDynamicLinker` in
every JAR file of the actual class loader. (By default, this is the thread
context class loader. The factory has a method for setting a different class
loader.) Therefore, if you wish for your language linker to be discovered by
other language runtimes, you should have this file in the JAR file of your
language runtime distribution, and declare its class name in it.

However, when you are creating a linker for your own use, you might want to
explicitly create an instance of your guarding linker and make sure that the
master linker gives it priority. You can do this by changing the code for
creating the linker in `MyLanguageRuntime` to:
```java
private static final DynamicLinker linker;
static {
    final DynamicLinkerFactory factory = new DynamicLinkerFactory();
    final GuardingDynamicLinker myLanguageLinker = new MyLanguageLinker();
    factory.setPrioritizedLinker(myLanguageLinker);
    linker = factory.createLinker();
}
```
The factory is smart enough that even if it discovers the `MyLanguageLinker`
class through the JAR service mechanism, it will ignore it if you supplied a
pre-created prioritized instance.

Guarding linker?
----------------
Yes, the interface is named `GuardingDynamicLinker`. It has a sole method with
this signature:
```java
public GuardedInvocation getGuardedInvocation(LinkerRequest linkerRequest,
    LinkerServices linkerServices);
```
It is invoked for a particular invocation at particular call site. It needs to
inspect both the call site (mostly for its method name and types) and the
actual arguments and figure out whether it can produce a MethodHandle as the
target for the call site. The call site descriptor and the arguments are passed
in the `LinkerRequest` object. In ordinary circumstances, you'll check something
along the lines of:
```java
if(arguments.length > 0 && arguments[0] instanceof IMyLanguageObject)
```
If not, return null -- the master linker will then ask the next (if any)
guarding linker. This is the base requirement for cross-language
interoperability; you only deal with what you know, and pass on what you don't.
On the other hand, if you know what to do with the receiver object, then you'll
produce a method handle for handling the call *and* a guard method handle.
Actually, the `GuardedInvocation` class above is nothing more than a value
class, a tuple of two method handles -- one for the invocation, one for the
guard condition. Since your method handle is only valid under certain
conditions (i.e. `arguments[0] instanceof IMyLanguageObject`), you will want
to create a guard expressing this condition. The master linker will pass the
guard and the invocation to the call site, which will compose them into a new
method handle according to its inline caching strategy. I.e. the
`MonomorphicCallSite` will create a `guardWithTest()` of the guard and the
invocation, with fallback to the master linker's `relink()` method when the
guard fails. The main takeaway is that you needn't deal with any of that; just
need to provide the invocation and the guard.

In reality, `GuardedInvocation` can also contain a
`java.lang.invoke.SwitchPoint`. You can use the switch point in your linker
implementation if you want the ability to invalidate the guarded invocations
asynchronously when some external condition changes. You just need to pass the
switch point in your guarded invocation, and in the chosen event, invalidate it.
You don't need to worry about invoking `SwitchPoint.guardWithTest()`; again, it
is the job of the call site implementation to compose your invocation, the
guard, and the switch point into a composite method handle that behaves
according to the call site semantics (i.e. the `MonomorphicCallSite` class will
relink itself on next invocation after you invalidate the currently linked
method's switch point).

What's LinkerServices?
----------------------
It's an interface provided to your linker with some extra methods your linker
might need. Currently it provides you with a `convertArguments()` method that
looks much like `MethodHandles.convertArguments()`, except it will also inject
language-specific type conversions when they are available in addition to the
JVM specific ones provided by `MethodHandles.convertArguments()`.

Cool, I want to define my own language type conversions!
--------------------------------------------------------
Sure thing. Just have your `GuardingDynamicLinker` also implement the optional
`GuardingTypeConverterFactory` interface. The linker framework will pick it up
and do the rest of its magic to make sure it ends up in the call path when
needed, as optimally as possible.

Finally, the Metaobject Protocol
--------------------------------
Finally, what kind of invocations to provide? What method names and signatures
to expect and react to? Also, what method names and signatures to emit in your
own `invokedynamic` instructions? For purposes of interoperability, we'll
reserve the method namespace `dyn` for the commonly-understood MOP, meaning
every method name will start with `dyn:`. Also note that when we use the
`INVOKEDYNAMIC` instruction, for sake of brevity we omit the business of
specifying a bootstrap method that we already explained how to do previously.

The operations are:

1. Get property of an object with a constant name

    Template: `"dyn:getProp:${name}"(any-object-type)any-type`

    Example:
    * Source code:

            obj.temperature

    * Bytecode:

            ALOAD 2 # assume obj is in 2nd local variable
            INVOKEDYNAMIC "dyn:getProp:temperature"(Ljava/lang/Object;)Ljava/lang/Number;

    Your `GuardingDynamicLinker` should recognize `dyn:getprop:name` as a
    property getter for a fixed name. `MethodHandles.convertArguments()` or
    even `MethodHandles.filterArguments()` for custom value conversions might
    of course be necessary both for receiver and return value.

2. Set property of an object with a constant name

    Template: `"dyn:setProp:${name}"(any-object-type,any-type)V`

    Example:
    * Source code:

            obj.temperature = 1;

    * Bytecode:

            ALOAD 2
            ICONST_1
            INVOKEDYNAMIC "dyn:setProp:temperature"(Ljava/lang/Object;I)V;

    Your `GuardingDynamicLinker` should recognize `dyn:setprop:name` as a
    property setter for a fixed name. `MethodHandles.convertArguments()` or
    even `MethodHandles.filterArguments()` for custom value conversions might
    of course be necessary both for receiver and return value.

3. Get property of an object with a non-constant identifier

    Template: `"dyn:getProp"(any-object-type,any-type)any-type`

    Example:
    * Source code:

            var a = "temperature"; obj[a]

    * Bytecode:

            ALOAD 2 # assume 'obj' is in 2nd slot
            ALOAD 3 # assume 'a' is in 3rd slot
            INVOKEDYNAMIC "dyn:getProp"(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Number;

    Your `GuardingDynamicLinker` should recognize `dyn:getprop` as a property
    getter for a name that can change between invocations, and which is passed
    in the arguments to the method handle. You probably shouldn't return a
    method handle that is fixed for the current value of the identifier
    (albeit you could if you also build the assumption into the guard). The
    expectation is that this will result in too frequent relinking, so you'd
    rather return a method handle that uses the value of the name.
    `MethodHandles.convertArguments()` or even
    `MethodHandles.filterArguments()` for custom value conversions might of
    course be necessary. Note how the identifier argument can be of any type
    and is not restricted to a `java.lang.String`. The reasoning behind this
    is that not every language can prove the value will be a string at
    invocation time, and the language semantics can actually allow for, say,
    numeric IDs. Consider this in JavaScript:

        function x(d) {
            var arrayAndDict = ["arrayElement"];
            arrayAndDict.customProperty = "namedProperty";
            return arrayAndDict[d ? 0 : "customProperty"];
        }

    `x(true)` returns `"arrayElement"`, `x(false)` returns `"namedProperty"`.
    At the point of invocation, the type of the property identifier is not
    known in advance.

4. Set property of an object with a non-constant identifier

    Template: `"dyn:setProp"(any-object-type,any-type,any-type)V`

    Example:
    * Source code:

            var a = "temperature"; obj[a] = 1

    * Bytecode:

            ALOAD 2 # assume 'obj' is in 2nd slot
            ALOAD 3 # assume 'a' is in 3rd slot
            ICONST_1
            INVOKEDYNAMIC "dyn:setProp"(Ljava/lang/Object;Ljava/lang/Object;I)V

    Your `GuardingDynamicLinker` should recognize `dyn:setprop` as a property
    setter for a name that can change between invocations.
    `MethodHandles.convertArguments()` or even
    `MethodHandles.filterArguments()` for custom value conversions might of
    course be necessary. Concerns about binding the method handle to the
    identifier expressed in point 3 fully apply, as well as the reasoning
    behind allowing any type for the identifier.

5. Get element of a container object

    Template: `"dyn:getElem"(any-object-type,any-type)any-type`

    Example:
    * Source code:

            var a = "temperature"; obj[a]

    * Bytecode:

             ALOAD 2 # assume 'obj' is in 2nd slot
             ALOAD 3 # assume 'a' is in 3rd slot
             INVOKEDYNAMIC "dyn:getElem"(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Number;

    Very similar to 3, except it can be used by languages that distinguish
    between namespaces of properties and keyspaces of container objects
    (arrays, lists, maps). All considerations in 3 apply. Additionally, if
    your language makes no distinction between the two, your
    `GuardingDynamicLinker` should respond to `dyn:getElem` identically as it
    would to `dyn:getProp`.

6. Set element of a container object

    Template: `"dyn:setElem"(any-object-type,any-type,any-type)V`

    Example:
    * Source code:

            var a = "temperature"; obj[a] = 1

    * Bytecode:

            ALOAD 2 # assume 'obj' is in 2nd slot
            ALOAD 3 # assume 'a' is in 3rd slot
            ICONST_1
            INVOKEDYNAMIC "dyn:setElem"(Ljava/lang/Object;Ljava/lang/Object;I)V

    Very similar to 4, except it can be used by languages that distinguish
    between namespaces of properties and keyspaces of container objects
    (arrays, lists, maps). All considerations in 3 and 4 apply. Additionally,
    if your language makes no distinction between the two namespaces, your
    `GuardingDynamicLinker` should respond to `dyn:setElem` identically as it
    would to `dyn:setProp`.

6. Get length of a container object

    Template: `"dyn:getLength"(any-object-type)I`

    Example:
    * Source code:

            a.length

    * Bytecode:

            ALOAD 2 # assume 'a' is in 2nd slot
            INVOKEDYNAMIC "dyn:getLength"(Ljava/lang/Object)I

    Returns the length of a container object. Expected to work on Java arrays,
    collections, and maps, as well as any other languages' container types.

Advanced topic: Language runtime contexts
-----------------------------------------
Some language runtimes pass "context" on stack. That is, each call site they
emit will have one or more additional arguments that represent language runtime
specific state at the point of invocation. This is normally thread-specific
state that is accessible through a thread local too, but is more optimal when
passed on stack. If you have such a language runtime, you should add the context
arguments at the end of the argument list, and you should also make sure to
invoke the `setNativeContextArgCount` method on the `DynamicLinkerFactory` to
make it aware that the last few arguments in your call sites are runtime
context.

In your `GuardingDynamicLinker` implementations, you should prepare for
encountering both expected and unexpected context arguments in the link
requests. If your runtime has a runtime context in the call sites, check for it,
and link accordingly when you see it. If your linker is asked to link against a
call site that does not expose your expected context (or your linker does not
expect any runtime contexts at all), invoke
`LinkRequest.withoutRuntimeContext()` to obtain a request with all runtime
context arguments stripped and link against that. The `DynamicLinker`
implementation is smart enough to notice that your linker returned a guarded
invocation for a context-stripped link request, and will successfully link it
into the call site by dropping the context arguments.

Also prepare for a situation when your linker is invoked for linking a call site
that is not emitted by your own language runtime, and does not have the context
arguments in the link request. You will have to make sure that your objects'
methods are correctly invokable even in absence of the context -- they should be
able to reacquire the context from a thread local when needed.