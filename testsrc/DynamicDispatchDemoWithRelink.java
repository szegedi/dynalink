import java.dyn.CallSite;
import java.dyn.InvokeDynamic;
import java.dyn.Linkage;
import java.dyn.MethodHandle;
import java.dyn.MethodHandles;
import java.dyn.MethodType;
import java.lang.reflect.Method;

import org.dynalang.dynalink.CallSiteDescriptor;

public class DynamicDispatchDemoWithRelink
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
        //final Object[] greeters = new Object[] { new English(), new Spanish() };
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
        final RelinkableCallSite cs = new RelinkableCallSite();
        MethodHandle boundRelink = MethodHandles.insertArguments(RELINK_AND_INVOKE, 0, cs);
        MethodHandle collectedArgsRelink = MethodHandles.collectArguments(boundRelink, callSiteType.generic());
        MethodHandle convertedArgsRelink = MethodHandles.convertArguments(collectedArgsRelink, callSiteType);
        cs.setTarget(convertedArgsRelink);
        cs.relink = convertedArgsRelink;
        return cs;
    }

    private static MethodHandle RELINK_AND_INVOKE = 
        MethodHandles.lookup().findStatic(DynamicDispatchDemoWithRelink.class, 
                "relinkAndInvoke", MethodType.methodType(Object.class, RelinkableCallSite.class, Object[].class));
    
    private static Object relinkAndInvoke(RelinkableCallSite callSite, Object[] args) throws Throwable
    {
        final Class<?> receiverClass = args[0].getClass();
        final Class<?>[] signature = callSite.type().parameterArray();
        final Class<?>[] reflectSignature = new Class<?>[signature.length - 1];
        System.arraycopy(signature, 1, reflectSignature, 0, reflectSignature.length);
        final Method m = receiverClass.getMethod(callSite.name(), reflectSignature);
        final MethodHandle unreflected = MethodHandles.lookup().unreflect(m);
        final MethodHandle convertedTarget = MethodHandles.convertArguments(unreflected, callSite.type());
        
        final MethodHandle test = MethodHandles.insertArguments(IS_OF_CLASS, 0, receiverClass);
        final MethodHandle projected = MethodHandles.permuteArguments(test, callSite.type().generic(), new int[] { 0 });
        final MethodHandle convertedTest = MethodHandles.convertArguments(projected, callSite.type().changeReturnType(boolean.class));
        callSite.setTarget(MethodHandles.guardWithTest(convertedTest, convertedTarget, callSite.relink));
        System.out.println("Relinked call site for " + receiverClass.getName());
        return convertedTarget.invokeVarargs(args);
    }

    private static MethodHandle IS_OF_CLASS = 
        MethodHandles.lookup().findStatic(DynamicDispatchDemoWithRelink.class, 
                "isOfClass", MethodType.methodType(boolean.class, Class.class, Object.class));

    private static boolean isOfClass(Class<?> c, Object o)
    {
        return o != null && o.getClass() == c;
    }
    
    static class RelinkableCallSite extends CallSite
    {
        private MethodHandle relink;

        protected RelinkableCallSite() {
            super();
        }
    }
}