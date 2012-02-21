public class InterfaceDispatchDemo {
    interface Greeter {
        public void sayHello();
    }

    static class English implements Greeter {
        @Override
        public void sayHello() {
            System.out.println("Hello!");
        }
    }

    static class Spanish implements Greeter {
        @Override
        public void sayHello() {
            System.out.println("Hola!");
        }
    }

    public static void main(String[] args) {
        final Greeter[] greeters =
                new Greeter[] { new English(), new Spanish(), new English(), new Spanish(), new Spanish(),
                        new English(), new English() };
        for(Greeter greeter: greeters) {
            greeter.sayHello();
        }
    }
}