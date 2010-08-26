import java.dyn.CallSite;
import java.dyn.InvokeDynamic;
import java.dyn.Linkage;
import java.dyn.MethodHandle;
import java.dyn.MethodHandles;
import java.dyn.MethodType;
import java.dyn.NoAccessException;


public class InvokeDynamicDemo
{
    static
    {
        Linkage.registerBootstrapMethod("bootstrap");
    }
    
    public static CallSite bootstrap(Class<?> callerClass, String name, MethodType callSiteType)
    {
        final CallSite cs = new CallSite();
        final MethodHandle mh;
        if(name.equals("foo")) {
            mh = MethodHandles.lookup().findStatic(InvokeDynamicDemo.class, 
                    "fooImpl", callSiteType.changeReturnType(Void.TYPE));
        }
        else if(name.equals("bar")) {
            mh = MethodHandles.lookup().findStatic(InvokeDynamicDemo.class, 
                    "implForBar", callSiteType.changeReturnType(Void.TYPE));
        }
        else {
            throw new NoAccessException();
        }
        cs.setTarget(MethodHandles.convertArguments(mh, callSiteType));
        return cs;
    }
    
    public static void main(String[] args) throws Throwable
    {
        InvokeDynamic.foo("x");
        InvokeDynamic.bar("y");
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