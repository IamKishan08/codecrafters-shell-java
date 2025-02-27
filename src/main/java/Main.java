import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.nio.file.Path;
import java.nio.file.Files;

public class Main {
    public static void main(String[] args) throws Exception {

        Scanner scanner = new Scanner(System.in);

        List<String> builtIn = new ArrayList<>();
        Collections.addAll(builtIn, "type", "echo", "exit");

        while (true) {

            System.out.print("$ ");
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) {
                continue;
            }

            String[] parts = input.split("\\s+", 2);
            String command = parts[0];
            String parameter = parts.length > 1 ? parts[1] : "";

            switch (command) {
                case "exit":
                    if (parameter.equals("0")) {
                        scanner.close();
                        System.exit(0);
                    } else {
                        System.out.println(input + ": command not found");
                    }
                    break;

                case "echo":
                    System.out.println(parameter);
                    break;

                case "type":
                    if (parameter.isEmpty()) {
                        System.out.println("type: missing argument");
                        break;
                    }

                    // Check if it is builtIn
                    if (builtIn.contains(parameter)) {
                        System.out.println(parameter + " is a shell builtin");
                        break;
                    }

                    // Search Path
                    String path = getPath(parameter);
                    if (path != null) {
                        System.out.println(parameter + " is " + path);
                    } else {
                        System.out.println(parameter + ": not found");
                    }
                    break;

                default:

                    String spath = getPath(command);
                    if (spath == null) {
                        System.out.println(input + ": command not found");
                        break;
                    } else {
                        String fullpath = spath + parameter;
                        Process p = Runtime.getRuntime().exec(fullpath.split(" "));
                        p.getInputStream().transferTo(System.out);
                    }
            }

        }
    }

    private static String getPath(String parameter) {
        String pathEnv = System.getenv("PATH");

        if (pathEnv == null || pathEnv.isEmpty()) {
            return null;
        }

        for (String dir : pathEnv.split(":")) {
            Path fullpath = Path.of(dir, parameter);
            if (Files.isRegularFile(fullpath)) {
                return fullpath.toString();
            }
        }

        return null;
    }
}
