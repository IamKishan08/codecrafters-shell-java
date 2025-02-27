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
        input.trim();
        String ec = input.substring(0,4);
        
        if(ec.equals("echo")){
            System.out.println(input.substring(5));
        }else{
           System.out.println(input + ": command not found");
        }
       }

       
    }
}
