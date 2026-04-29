import java.io.*;
import java.util.Scanner;

public class ATM {
    static final String BAL_FILE = "balance.txt";

    public static void main(String[] args) throws IOException {
        Scanner sc = new Scanner(System.in);
        System.out.println("1. Deposit\n2. Withdraw\n3. Check Balance");
        int choice = sc.nextInt();

        double balance = readBalance();

        if (choice == 1) {
            System.out.print("Enter amount to deposit: ");
            double amt = sc.nextDouble();
            balance += amt;
            writeBalance(balance);
            System.out.println("Deposited. New Balance: " + balance);
        } else if (choice == 2) {
            System.out.print("Enter amount to withdraw: ");
            double amt = sc.nextDouble();
            if (amt <= balance) {
                balance -= amt;
                writeBalance(balance);
                System.out.println("Withdrawn. New Balance: " + balance);
            } else {
                System.out.println("Insufficient funds!");
            }
        } else if (choice == 3) {
            System.out.println("Current Balance: " + balance);
        } else {
            System.out.println("Invalid option");
        }
        sc.close();
    }

    static double readBalance() {
        try (BufferedReader br = new BufferedReader(new FileReader(BAL_FILE))) {
            return Double.parseDouble(br.readLine());
        } catch (IOException | NumberFormatException e) {
            return 0.0;
        }
    }

    static void writeBalance(double bal) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(BAL_FILE))) {
            bw.write(String.valueOf(bal));
        } catch (IOException e) {
            System.out.println("Error writing balance.");
        }
    }
}
