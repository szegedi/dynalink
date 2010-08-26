import java.dyn.MethodHandle;
import java.dyn.MethodHandles;
import java.dyn.MethodType;

public class TestCollectArguments
{
    public static void main(String[] args) throws Throwable
    {
        MethodHandle xs = MethodHandles.publicLookup().findVirtual(
                TestCollectArguments.class, "xs", MethodType.methodType(
                        String.class, String.class, String[].class));
        // This works
        System.out.println(xs.invokeVarargs(new TestCollectArguments(), "a", 
                new String[] { "b", "c" }));
        MethodHandle collecting = MethodHandles.collectArguments(xs, 
                MethodType.methodType(String.class, Object.class, String.class, 
                        String.class, String.class));
        // This fails
        System.out.println(collecting.invokeVarargs(new TestCollectArguments(), 
                "a", "b", "c"));
/*
        
        MethodHandle xv = MethodHandles.publicLookup().findVirtual(
                TestCollectArguments.class, "xv", MethodType.methodType(
                        int.class, int.class, int[].class));
        // This works
        System.out.println(xv.invokeGeneric(new TestCollectArguments(), 1, 
                new int[] { 2, 3 }));
        MethodHandle collecting = MethodHandles.collectArguments(xv, 
                MethodType.methodType(int.class, Object.class, int.class, 
                        int.class, int.class));
        // This fails
        System.out.println(collecting.invokeGeneric(new TestCollectArguments(), 
                1, 2, 3));
                */
    }
    
    public int xv(int y, int... z) {
        for (int zz : z) {
            y += zz;
        }
        return y;
    }

    public String xs(String y, String... z) {
        for (String zz : z) {
            y += zz;
        }
        return y;
    }
}