import java.dyn.CallSite;
import java.dyn.InvokeDynamic;
import java.dyn.Linkage;
import java.dyn.MethodHandle;
import java.dyn.MethodHandles;
import java.dyn.MethodType;
import java.lang.reflect.Method;

import org.dynalang.dynalink.CallSiteDescriptor;
import org.dynalang.dynalink.MonomorphicCallSite;
import org.dynalang.dynalink.RelinkableCallSite;

public class DynamicDispatchDemo
{
    public static class English
    {
        public void sayHello()
        {
            System.out.println("Hello!");
        }
    }
    
    public static class Spanish
    {
        public void sayHello()
        {
            System.out.println("Hola!");
        }
    }
    
    public static void main(String[] args) throws Throwable
    {
        final Object[] greeters = new Object[] { new English(), new Spanish(), new English(), new Spanish(), new Spanish(), new English(), new English() };
        for(Object greeter: greeters)
        {
            InvokeDynamic.sayHello(greeter);
        }
    }
    
    static
    {
        Linkage.registerBootstrapMethod("bootstrap");
    }
    
    public static CallSite bootstrap(Class<?> callerClass, String name, MethodType callSiteType)
    {
        final CallSite cs = new MonomorphicCallSite(name, callSiteType);
        MethodHandle boundInvoker = MethodHandles.insertArguments(INVOKE_DYNAMICALLY, 0, cs);
        MethodHandle collectedArgsInvoker = MethodHandles.collectArguments(boundInvoker, callSiteType.generic());
        MethodHandle convertedArgsInvoker = MethodHandles.convertArguments(collectedArgsInvoker, callSiteType);
        cs.setTarget(convertedArgsInvoker);
        return cs;
    }

    private static MethodHandle INVOKE_DYNAMICALLY = 
        MethodHandles.lookup().findStatic(DynamicDispatchDemo.class, 
                "invokeDynamically", MethodType.methodType(Object.class, RelinkableCallSite.class, Object[].class));
    
    private static Object invokeDynamically(RelinkableCallSite callSite, Object[] args) throws Throwable
    {
        final Class<?> receiverClass = args[0].getClass();
        final CallSiteDescriptor descriptor = callSite.getCallSiteDescriptor();
        final Class<?>[] signature = descriptor.getMethodType().parameterArray();
        final Class<?>[] reflectSignature = new Class<?>[signature.length - 1];
        System.arraycopy(signature, 1, reflectSignature, 0, reflectSignature.length);
        final Method m = receiverClass.getMethod(descriptor.getName(), reflectSignature);
        final MethodHandle unreflected = MethodHandles.lookup().unreflect(m);
        return unreflected.invokeVarargs(args);
    }
}