/* 

package org.ivc.dbms;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;


public class Main {
    static Connection conn;
    static Scanner scanner = new Scanner(System.in);
    static String loggedInCustomerId = null;

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

            if (choice == 1) customerMenu();
            else if (choice == 2) managerMenu();
            else break;
        }

        conn.close();
    }

    static void customerMenu() {
        while (true) {
            System.out.println("\n=== Customer Menu ===");
            System.out.println("1. Sign Up");
            System.out.println("2. Login");
            System.out.println("3. Back");
            System.out.print("Choose: ");
            int choice = Integer.parseInt(scanner.nextLine());

            if (choice == 1) signUp();
            else if (choice == 2) login();
            else break;
        }
    }

    static void managerMenu() {
        System.out.println("Manager interface coming soon.");
    }

    static void signUp() {
    try {
        System.out.print("Enter customer ID: ");
        String id = scanner.nextLine();
        System.out.print("Enter password: ");
        String password = scanner.nextLine();
        System.out.print("Enter first name: ");
        String firstName = scanner.nextLine();
        System.out.print("Enter middle name (or press Enter to skip): ");
        String middleName = scanner.nextLine();
        System.out.print("Enter last name: ");
        String lastName = scanner.nextLine();
        System.out.print("Enter email: ");
        String email = scanner.nextLine();
        System.out.print("Enter address: ");
        String address = scanner.nextLine();

        String sql = "INSERT INTO Customer VALUES (?, ?, ?, ?, ?, ?, ?, 'New')";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setString(1, id);
        ps.setString(2, password);
        ps.setString(3, firstName);
        ps.setString(4, middleName.isEmpty() ? null : middleName);
        ps.setString(5, lastName);
        ps.setString(6, email);
        ps.setString(7, address);
        ps.executeUpdate();
        conn.commit();

        System.out.println("Account created successfully!");
    } catch (SQLException e) {
        System.out.println("Error: " + e.getMessage());
    }
}

    static void login() {
    try {
        System.out.print("Enter customer ID: ");
        String id = scanner.nextLine();
        System.out.print("Enter password: ");
        String password = scanner.nextLine();

        PreparedStatement ps = conn.prepareStatement(
            "SELECT * FROM Customer WHERE customer_id = ? AND password = ?");
        ps.setString(1, id);
        ps.setString(2, password);
        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            loggedInCustomerId = id;
            System.out.println("Welcome, " + rs.getString("first_name") + "!");
            shoppingMenu();
        } else {
            System.out.println("Invalid customer ID or password.");
        }
    } catch (SQLException e) {
        System.out.println("Error: " + e.getMessage());
    }
}

static void shoppingMenu() {
    List<String[]> cart = new ArrayList<>(); // each entry: {stock_number, price}

    while (true) {
        System.out.println("\n=== Shopping Menu ===");
        System.out.println("1. Search for items");
        System.out.println("2. View cart");
        System.out.println("3. Checkout");
        System.out.println("4. View previous orders");
        System.out.println("5. Re-run previous order");
        System.out.println("6. Logout");
        System.out.print("Choose: ");
        int choice = Integer.parseInt(scanner.nextLine());

        if (choice == 1) searchItems(cart);
        else if (choice == 2) viewCart(cart);
        else if (choice == 3) checkout(cart);
        else if (choice == 4) viewPreviousOrders();
        else if (choice == 5) rerunOrder(cart);
        else break;
    }

    loggedInCustomerId = null;
}

static void searchItems(List<String[]> cart) {
    try {
        System.out.println("\n=== Search Items ===");
        System.out.println("1. By stock number");
        System.out.println("2. By manufacturer");
        System.out.println("3. By model number");
        System.out.println("4. By category");
        System.out.println("5. By description attribute");
        System.out.println("6. By compatible item");
        System.out.print("Choose: ");
        int choice = Integer.parseInt(scanner.nextLine());

        String sql = "";
        String param = "";

        if (choice == 1) {
            System.out.print("Enter stock number: ");
            param = scanner.nextLine();
            sql = "SELECT * FROM CatalogItem WHERE stock_number = ?";
        } else if (choice == 2) {
            System.out.print("Enter manufacturer: ");
            param = scanner.nextLine();
            sql = "SELECT * FROM CatalogItem WHERE LOWER(manufacturer) LIKE LOWER(?)";
            param = "%" + param + "%";
        } else if (choice == 3) {
            System.out.print("Enter model number: ");
            param = scanner.nextLine();
            sql = "SELECT * FROM CatalogItem WHERE LOWER(model_number) LIKE LOWER(?)";
            param = "%" + param + "%";
        } else if (choice == 4) {
            System.out.print("Enter category: ");
            param = scanner.nextLine();
            sql = "SELECT * FROM CatalogItem WHERE LOWER(category) LIKE LOWER(?)";
            param = "%" + param + "%";
        } else if (choice == 5) {
            System.out.print("Enter attribute name: ");
            String attrName = scanner.nextLine();
            System.out.print("Enter attribute value: ");
            String attrValue = scanner.nextLine();
            sql = "SELECT c.* FROM CatalogItem c JOIN ItemAttributes a ON c.stock_number = a.stock_number " +
                  "WHERE LOWER(a.attr_name) LIKE LOWER(?) AND LOWER(a.attr_value) LIKE LOWER(?)";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, "%" + attrName + "%");
            ps.setString(2, "%" + attrValue + "%");
            ResultSet rs = ps.executeQuery();
            printAndAddToCart(rs, cart);
            return;
        } else if (choice == 6) {
            System.out.print("Enter stock number to find compatible items for: ");
            param = scanner.nextLine();
            sql = "SELECT c.* FROM CatalogItem c JOIN Compatible comp ON c.stock_number = comp.compatible_stock_number " +
                  "WHERE comp.stock_number = ?";
        } else {
            System.out.println("Invalid choice.");
            return;
        }

        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setString(1, param);
        ResultSet rs = ps.executeQuery();
        printAndAddToCart(rs, cart);

    } catch (SQLException e) {
        System.out.println("Error: " + e.getMessage());
    }
}

static void printAndAddToCart(ResultSet rs, List<String[]> cart) throws SQLException {
    List<String[]> results = new ArrayList<>();
    System.out.println("\n--- Results ---");
    int i = 1;
    while (rs.next()) {
        String stockNum = rs.getString("stock_number");
        String mfr = rs.getString("manufacturer");
        String model = rs.getString("model_number");
        String category = rs.getString("category");
        String price = rs.getString("price");
        System.out.println(i + ". [" + stockNum + "] " + mfr + " " + model +
                           " | Category: " + category + " | $" + price);
        results.add(new String[]{stockNum, price});
        i++;
    }

    if (results.isEmpty()) {
        System.out.println("No items found.");
        return;
    }

    System.out.print("\nEnter item number to add to cart (or 0 to skip): ");
    int choice = Integer.parseInt(scanner.nextLine());
    if (choice > 0 && choice <= results.size()) {
        System.out.print("Enter quantity: ");
        String qty = scanner.nextLine();
        String[] item = results.get(choice - 1);
        cart.add(new String[]{item[0], item[1], qty});
        System.out.println("Added to cart!");
    }
}

static void viewCart(List<String[]> cart) {
    System.out.println("\n=== Your Cart ===");
    if (cart.isEmpty()) {
        System.out.println("Your cart is empty.");
        return;
    }

    double total = 0;
    int i = 1;
    for (String[] item : cart) {
        double price = Double.parseDouble(item[1]);
        int qty = Integer.parseInt(item[2]);
        double subtotal = price * qty;
        total += subtotal;
        System.out.println(i + ". Stock#: " + item[0] + " | $" + item[1] +
                           " x " + qty + " = $" + String.format("%.2f", subtotal));
        i++;
    }
    System.out.println("Subtotal: $" + String.format("%.2f", total));

    System.out.print("\nEnter item number to remove (or 0 to keep): ");
    int choice = Integer.parseInt(scanner.nextLine());
    if (choice > 0 && choice <= cart.size()) {
        cart.remove(choice - 1);
        System.out.println("Item removed.");
    }
}

static void checkout(List<String[]> cart) {
    if (cart.isEmpty()) {
        System.out.println("Your cart is empty.");
        return;
    }

    try {
        // Get customer status
        PreparedStatement ps = conn.prepareStatement(
            "SELECT status FROM Customer WHERE customer_id = ?");
        ps.setString(1, loggedInCustomerId);
        ResultSet rs = ps.executeQuery();
        rs.next();
        String status = rs.getString("status");

        // Get discount rules
        ResultSet dr = conn.createStatement().executeQuery(
            "SELECT * FROM DiscountRules WHERE rule_id = 1");
        dr.next();
        double goldDisc = dr.getDouble("gold_disc_pct") / 100;
        double silverDisc = dr.getDouble("silver_disc_pct") / 100;
        double greenDisc = dr.getDouble("green_disc_pct") / 100;
        double newDisc = dr.getDouble("new_disc_pct") / 100;
        double shippingPct = dr.getDouble("shipping_fee_pct") / 100;
        double freeShipThresh = dr.getDouble("free_ship_thresh");

        // Calculate subtotal
        double subtotal = 0;
        for (String[] item : cart) {
            subtotal += Double.parseDouble(item[1]) * Integer.parseInt(item[2]);
        }

        // Apply discount
        double discountPct = 0;
        if (status.equals("New") || status.equals("Gold")) discountPct = newDisc;
        else if (status.equals("Silver")) discountPct = silverDisc;
        else if (status.equals("Green")) discountPct = greenDisc;

        double discountedTotal = subtotal * (1 - discountPct);

        // Apply shipping
        double shipping = 0;
        if (discountedTotal <= freeShipThresh && !status.equals("New")) {
            shipping = discountedTotal * shippingPct;
        }

        double finalTotal = discountedTotal + shipping;

        System.out.println("\n=== Order Summary ===");
        System.out.println("Subtotal:  $" + String.format("%.2f", subtotal));
        System.out.println("Discount:  " + (int)(discountPct * 100) + "%");
        System.out.println("Shipping:  $" + String.format("%.2f", shipping));
        System.out.println("Total:     $" + String.format("%.2f", finalTotal));
        System.out.print("Confirm order? (yes/no): ");
        if (!scanner.nextLine().equalsIgnoreCase("yes")) return;

        // Generate order number
        ResultSet countRs = conn.createStatement().executeQuery(
            "SELECT COUNT(*) FROM CustomerOrder");
        countRs.next();
        int orderNum = countRs.getInt(1) + 1;

        // Insert order
        PreparedStatement orderPs = conn.prepareStatement(
            "INSERT INTO CustomerOrder VALUES (?, CURRENT_DATE, ?, ?, 1)");
        orderPs.setInt(1, orderNum);
        orderPs.setDouble(2, finalTotal);
        orderPs.setString(3, loggedInCustomerId);
        orderPs.executeUpdate();

        // Insert contains rows
        for (String[] item : cart) {
            PreparedStatement containsPs = conn.prepareStatement(
                "INSERT INTO Contains VALUES (?, ?, ?, ?)");
            containsPs.setInt(1, orderNum);
            containsPs.setString(2, item[0]);
            containsPs.setInt(3, Integer.parseInt(item[2]));
            containsPs.setDouble(4, Double.parseDouble(item[1]));
            containsPs.executeUpdate();
        }

        // Update customer status based on last 3 orders
        ResultSet last3 = conn.prepareStatement(
            "SELECT SUM(total_price) FROM (" +
            "SELECT total_price FROM CustomerOrder WHERE customer_id = '" + loggedInCustomerId +
            "' ORDER BY order_number DESC FETCH FIRST 3 ROWS ONLY)").executeQuery();
        last3.next();
        double last3Total = last3.getDouble(1);

        String newStatus;
        if (last3Total > 500) newStatus = "Gold";
        else if (last3Total > 100) newStatus = "Silver";
        else if (last3Total > 0) newStatus = "Green";
        else newStatus = "New";

        PreparedStatement updateStatus = conn.prepareStatement(
            "UPDATE Customer SET status = ? WHERE customer_id = ?");
        updateStatus.setString(1, newStatus);
        updateStatus.setString(2, loggedInCustomerId);
        updateStatus.executeUpdate();

        conn.commit();
        System.out.println("Order placed! Your order number is: " + orderNum);
        System.out.println("Your new status: " + newStatus);
        cart.clear();

    } catch (SQLException e) {
        System.out.println("Error: " + e.getMessage());
    }
}

static boolean viewPreviousOrders() {
    try {
        PreparedStatement ps = conn.prepareStatement(
            "SELECT * FROM CustomerOrder WHERE customer_id = ? ORDER BY order_number DESC");
        ps.setString(1, loggedInCustomerId);
        ResultSet rs = ps.executeQuery();

        System.out.println("\n=== Previous Orders ===");
        boolean found = false;
        while (rs.next()) {
            found = true;
            int orderNum = rs.getInt("order_number");
            System.out.println("\nOrder #" + orderNum +
                               " | Date: " + rs.getString("order_date") +
                               " | Total: $" + rs.getString("total_price"));

            PreparedStatement itemPs = conn.prepareStatement(
                "SELECT c.*, ci.manufacturer, ci.model_number FROM Contains c " +
                "JOIN CatalogItem ci ON c.stock_number = ci.stock_number " +
                "WHERE c.order_number = ?");
            itemPs.setInt(1, orderNum);
            ResultSet items = itemPs.executeQuery();
            while (items.next()) {
                System.out.println("  - [" + items.getString("stock_number") + "] " +
                                   items.getString("manufacturer") + " " +
                                   items.getString("model_number") +
                                   " | Qty: " + items.getInt("quantity") +
                                   " | Unit Price: $" + items.getString("unit_price"));
            }
        }

        if (!found) { 
          System.out.println("No previous orders found.");
          return false; 
        }

    } catch (SQLException e) {
        System.out.println("Error: " + e.getMessage());
    }
    return true;
}

static void rerunOrder(List<String[]> cart) {
    try {
        if (!viewPreviousOrders()) {
            return;
        }
        System.out.print("\nEnter order number to re-run: ");
        int orderNum = Integer.parseInt(scanner.nextLine());

        PreparedStatement ps = conn.prepareStatement(
            "SELECT * FROM Contains WHERE order_number = ?");
        ps.setInt(1, orderNum);
        ResultSet rs = ps.executeQuery();

        cart.clear();
        while (rs.next()) {
            cart.add(new String[]{
                rs.getString("stock_number"),
                rs.getString("unit_price"),
                String.valueOf(rs.getInt("quantity"))
            });
        }

        if (cart.isEmpty()) {
            System.out.println("Order not found.");
            return;
        }

        System.out.println("Items loaded into cart. Proceeding to checkout...");
        checkout(cart);

    } catch (SQLException e) {
        System.out.println("Error: " + e.getMessage());
    }
}
}

*/