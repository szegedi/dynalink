import java.dyn.MethodHandle;
import java.dyn.MethodHandles;
import java.dyn.MethodType;


public class TestInsertArguments
{
    public static void main(String[] args) throws Throwable
    {
        MethodHandle xv = MethodHandles.publicLookup().findVirtual(
                TestInsertArguments.class, "xv", MethodType.methodType(int.class, 
                        int.class, int[].class));
        MethodHandle singleArg = MethodHandles.insertArguments(xv, 2, new int[0]);
        System.out.println(singleArg.type());
        System.out.println(singleArg.invokeGeneric(new TestInsertArguments(), 1));
        
        
    }

    public int xv(int y, int... z)
    {
        for (int zz : z)
        {
            y += zz;
        }
        return y;
    }
}
