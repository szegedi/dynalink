import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class TestCollectArguments {
    public static void main(String[] args) throws Throwable {
        MethodHandle xs = MethodHandles.publicLookup().findVirtual(
                TestCollectArguments.class, "xs", MethodType.methodType(
                        String.class, String.class, String[].class));
        // This works
        System.out.println(xs.invokeWithArguments(new TestCollectArguments(), "a", 
                new String[] { "b", "c" }));

        // This fails
        try {
          System.out.println(xs.invokeWithArguments(new TestCollectArguments(), 
              "a", "b", "c"));
        }
        catch(ClassCastException e) {
          e.printStackTrace();
        }

        // This fails too
        try {
          System.out.println(xs.asCollector(String[].class, 2).invokeWithArguments(
              new TestCollectArguments(), "a", "b", "c"));
        }
        catch(ClassCastException e) {
          e.printStackTrace();
        }
    }
    
    public String xs(String y, String... z) {
        for (String zz : z) {
            y += zz;
        }
        return y;
    }
}