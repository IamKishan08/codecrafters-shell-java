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
        Collections.addAll(builtIn, "type","echo","exit","ls","valid_command");
        
        if(input.startsWith("echo")){
            System.out.println(input.substring(5));
        }else if(input.startsWith("type")){
             String inbuilt = input.substring(5);
             if(builtIn.contains(inbuilt)){
                System.out.println(inbuilt + " is /usr/bin/"+inbuilt);
             }else{
                System.out.println(inbuilt+ ": not found");
             }
        }
        else{
           System.out.println(input + ": command not found");
        }
       }

       
    }
}
