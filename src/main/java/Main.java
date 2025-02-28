import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.nio.file.Path;
import java.nio.file.Paths;
//import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class Main {
    public static void main(String[] args) throws Exception {

        Scanner scanner = new Scanner(System.in);
        List<String> builtIn = new ArrayList<>();
        Collections.addAll(builtIn, "type", "echo", "exit", "pwd", "cd", "cat");
        String dir = Path.of("").toAbsolutePath().toString();

        while (true) {
            System.out.print("$ ");
            String input = scanner.nextLine().trim();

            if (input.isEmpty())
                continue;

            List<String> parts = parseArgument(input);

            if (parts.isEmpty())
                continue;

            // String[] parts = input.split("\s+", 2);
            String command = parts.get(0);
            List<String> parameter = parts.subList(1, parts.size());

            switch (command) {
                case "exit":
                    if (parameter.size() == 1 && parameter.get(0).equals("0")) {
                        scanner.close();
                        System.exit(0);
                    } else {
                        System.out.println(input + ": command not found");
                    }
                    break;

                case "echo":

                    System.out.println(String.join(" ", parameter));
                    break;

                case "pwd":

                    System.out.println(dir);
                    break;
                case "cd":

                    if (parameter.isEmpty()) {
                        System.out.println("cd: missing arguments");
                        break;
                    }
                    String targetDir = parameter.get(0).equals("~") ? System.getenv("HOME") : parameter.get(0);
                    Path newPath = Paths.get(dir).resolve(targetDir).normalize();

                    if (Files.isDirectory(newPath)) {
                        dir = newPath.toAbsolutePath().toString();
                    } else {
                        System.out.println("cd: " + parameter.get(0) + ": No such file or directory");
                    }
                    break;

                case "type":
                    if (parameter.isEmpty()) {
                        System.out.println("type: missing argument");
                        break;
                    }

                    for (String param : parameter) {
                        // Check if it is builtIn
                        if (builtIn.contains(param)) {
                            System.out.println(param + " is a shell builtin");
                            break;
                        }

                        // Search Path
                        String path = getPath(param);
                        if (path != null) {
                            System.out.println(param + " is " + path);
                        } else {
                            System.out.println(param + ": not found");
                        }
                    }
                    break;

                case "cat":

                    if (parameter.isEmpty()) {
                        System.out.println("cat : missing file operand");
                    }
                     
                    for(String fileName : parameter){
                          Path filePath = Paths.get(dir).resolve(fileName);
                          if(Files.exists(filePath) && Files.isReadable(filePath)){
                            try{
                                   Files.lines(filePath).forEach(System.out::print);
                            }catch(IOException e){
                                System.out.println("cat: "+fileName+ ": read error");
                            }
                          }else{
                            System.out.println("cat: "+fileName+ ": No such file or cannot read");
                          }
                    } System.out.println();

                    break;

                default:
                    String execPath = getPath(command);
                    if (execPath != null) {
                        try {
                            String[] fullcommand = new String[] { command, parameter.get(0) };

                            Process process = Runtime.getRuntime().exec(fullcommand);
                            process.getInputStream().transferTo(System.out);
                            process.waitFor();

                        } catch (IOException | InterruptedException e) {
                            System.out.println(command + ": execution failed: " + e.getMessage());
                        }
                    } else {
                        System.out.println(input + ": command not found");
                    }
                    break;

            }

        }
    }

    private static List<String> parseArgument(String input) {

        List<String> args = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder currentArg = new StringBuilder();

        for (char c : input.toCharArray()) {
            if (c == '\'') {
                inQuotes = !inQuotes;
            } else if (c == ' ' && !inQuotes) {
                if (currentArg.length() > 0) {
                    args.add(currentArg.toString());
                    currentArg.setLength(0);
                }
            } else {
                currentArg.append(c);
            }
        }

        if (currentArg.length() > 0) {
            args.add(currentArg.toString());
        }

        return args;
    }

    private static String getPath(String parameter) {
        String pathEnv = System.getenv("PATH");

        if (pathEnv == null || pathEnv.isEmpty()) {
            return null;
        }

        for (String dir : pathEnv.split(":")) {
            Path fullpath = Path.of(dir, parameter);
            if (Files.isRegularFile(fullpath) && Files.isExecutable(fullpath)) {
                return fullpath.toString();
            }
        }

        return null;
    }
}
