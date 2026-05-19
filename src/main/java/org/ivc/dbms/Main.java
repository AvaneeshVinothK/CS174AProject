package org.ivc.dbms;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.Scanner;

public class Main {
    public static Connection conn;
    public static Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) throws Exception {
        String walletPath = "C:/Users/Avaneesh/OneDrive/UCSB_Classes/Spring2026/CS174A/Project/Wallet_CS174AProject";
        
        Properties props = new Properties();
        props.setProperty("user", "ADMIN");
        props.setProperty("password", "3e6vF#5qw#9DP_$");
        props.setProperty("oracle.net.wallet_location",
            "(SOURCE=(METHOD=FILE)(METHOD_DATA=(DIRECTORY=" + walletPath + ")))");
        props.setProperty("oracle.net.tns_admin", walletPath);

        String url = "jdbc:oracle:thin:@cs174aproject_tp?TNS_ADMIN=" + walletPath;
        conn = DriverManager.getConnection(url, props);
        conn.setAutoCommit(false);
        System.out.println("Connected to database.");

        while (true) {
            System.out.println("\n=== Welcome to eMART ===");
            System.out.println("1. Customer");
            System.out.println("2. Manager");
            System.out.println("3. Exit");
            System.out.print("Choose: ");
            int choice = Integer.parseInt(scanner.nextLine());

            if (choice == 1) Customer.customerMenu();
            else if (choice == 2) Manager.managerMenu();
            else break;
        }

        conn.close();
        System.out.println("Goodbye!");
    }

    public static String generateCustomerId() throws SQLException {
        ResultSet rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM Customer");
        rs.next();
        int count = rs.getInt(1);
        return "CUST" + String.format("%04d", count + 1);
    }
}