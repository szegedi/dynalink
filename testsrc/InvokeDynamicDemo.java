import java.dyn.CallSite;
import java.dyn.ConstantCallSite;
import java.dyn.MethodHandle;
import java.dyn.MethodHandles;
import java.dyn.MethodType;
import java.dyn.InvokeDynamicBootstrapError;


public class InvokeDynamicDemo
{
    public static CallSite bootstrap(Class<?> callerClass, String name, MethodType callSiteType)
    {
        final MethodHandle mh;
        try {
            if(name.equals("foo")) {
                mh = MethodHandles.lookup().findStatic(InvokeDynamicDemo.class, 
                        "fooImpl", callSiteType.changeReturnType(Void.TYPE));
            }
            else if(name.equals("bar")) {
                mh = MethodHandles.lookup().findStatic(InvokeDynamicDemo.class, 
                        "implForBar", callSiteType.changeReturnType(Void.TYPE));
            }
            else {
                throw new InvokeDynamicBootstrapError();
            }
        }
        catch(IllegalAccessException|NoSuchMethodException e) {
            throw new InvokeDynamicBootstrapError(e);
        }
        return new ConstantCallSite(MethodHandles.convertArguments(mh, callSiteType));
    }
    
    public static void main(String[] args) throws Throwable
    {
        invokeDynamically("foo", "x");
        invokeDynamically("bar", "y");
    }
    
    private static void invokeDynamically(String name, String arg) throws Throwable {
        new DynamicIndy().invokeDynamic(name, MethodType.methodType(Void.TYPE, 
            String.class), InvokeDynamicDemo.class, "bootstrap", 
            MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, 
                String.class, MethodType.class)).invokeExact(arg);
    }

    public static void fooImpl(String s)
    {
        System.out.println("fooImpl invoked with " + s);
    }

    public static void implForBar(String s)
    {
        System.out.println("implForBar invoked with " + s);
    }
}