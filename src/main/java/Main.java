import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {

       Scanner scanner = new Scanner(System.in);
       while(true){
        System.out.print("$ ");
        String input = scanner.nextLine();
        if(input.equals("exit 0")){
            scanner.close();
            break;
        }
        
        List<String> builtIn = new ArrayList<>();
        Collections.addAll(builtIn, "type","echo","exit");
        
        if(input.startsWith("echo")){
            System.out.println(input.substring(5));
        }else if(input.startsWith("type")){
             if(builtIn.contains(input.substring(5))){
                System.out.println(input.substring(5) + " is a shell builtin");
             }else{
                System.out.println(input.substring(5) + ": not found");
             }
        }
        else{
           System.out.println(input + ": command not found");
        }
       }

       
    }
}
