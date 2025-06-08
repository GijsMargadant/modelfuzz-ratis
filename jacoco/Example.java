public class Example {
    public static void main(String[] args) {
        if (args.length > 0) {
            greet(args[0]);
        } else {
            System.out.println("No input provided.");
        }
    }

    public static void greet(String name) {
        if (name.equals("Alice")) {
            System.out.println("Hello, Alice!");
        } else if (name.equals("Bob")) {
            System.out.println("Hello, Bob!");
        } else {
            System.out.println("Hello, stranger!");
        }
    }
}
