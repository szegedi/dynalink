import java.dyn.MethodHandle;
import java.dyn.MethodHandles;
import java.dyn.MethodType;

public class TestCatchException
{
    public static void main(String[] args) throws Throwable
    {
        MethodHandle throwing = findStatic("throwing");
        MethodHandle catching = findStatic("catching");
        MethodHandles.catchException(throwing, MyException.class, 
                MethodHandles.dropArguments(catching, 0, 
                        MyException.class));
    }

    private static class MyException extends RuntimeException 
    {
    }
    
    private static MethodHandle findStatic(String name)
    {
        return MethodHandles.publicLookup().findStatic(
                TestCatchException.class, name, MethodType.methodType(int.class, 
                        Object.class));
    }


    public static int throwing(Object o)
    {
        throw new IllegalArgumentException();
    }
    
    public static int catching(Object o)
    {
        return 0;
    }
}