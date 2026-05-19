package org.ivc.dbms;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.Scanner;
import java.io.FileInputStream;

public class Main {
    public static Connection emartConn;
    public static Connection depotConn;
    public static Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) throws Exception {
        Properties config = new Properties();
        config.load(new FileInputStream("config.properties"));

        // eMART connection
        String emartWallet = config.getProperty("emart.wallet");
        Properties emartProps = new Properties();
        emartProps.setProperty("user", "ADMIN");
        emartProps.setProperty("password", config.getProperty("emart.password"));
        emartProps.setProperty("oracle.net.wallet_location",
            "(SOURCE=(METHOD=FILE)(METHOD_DATA=(DIRECTORY=" + emartWallet + ")))");
        emartProps.setProperty("oracle.net.tns_admin", emartWallet);
        emartConn = DriverManager.getConnection(
            "jdbc:oracle:thin:@" + config.getProperty("emart.tns") + "?TNS_ADMIN=" + emartWallet,
            emartProps);
        emartConn.setAutoCommit(false);
        System.out.println("Connected to eMART database.");

        // eDEPOT connection
        String depotWallet = config.getProperty("depot.wallet");
        Properties depotProps = new Properties();
        depotProps.setProperty("user", "ADMIN");
        depotProps.setProperty("password", config.getProperty("depot.password"));
        depotProps.setProperty("oracle.net.wallet_location",
            "(SOURCE=(METHOD=FILE)(METHOD_DATA=(DIRECTORY=" + depotWallet + ")))");
        depotProps.setProperty("oracle.net.tns_admin", depotWallet);
        depotConn = DriverManager.getConnection(
            "jdbc:oracle:thin:@" + config.getProperty("depot.tns") + "?TNS_ADMIN=" + depotWallet,
            depotProps);
        depotConn.setAutoCommit(false);
        System.out.println("Connected to eDEPOT database.");

        while (true) {
            System.out.println("\n=== Welcome to eMART ===");
            System.out.println("1. Customer");
            System.out.println("2. Manager");
            System.out.println("3. eDEPOT");
            System.out.println("4. Exit");
            System.out.print("Choose: ");
            int choice = Integer.parseInt(scanner.nextLine());

            if (choice == 1)      Customer.customerMenu();
            else if (choice == 2) Manager.managerMenu();
            else if (choice == 3) Depot.depotMenu();
            else break;
        }

        emartConn.close();
        depotConn.close();
        System.out.println("Goodbye!");
    }
}