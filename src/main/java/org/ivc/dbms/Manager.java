package org.ivc.dbms;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Manager {

    static void managerMenu() {
        while (true) {
            System.out.println("\n=== Manager Menu ===");
            System.out.println("1. Print monthly sales summary");
            System.out.println("2. Adjust customer status");
            System.out.println("3. Send order to manufacturer");
            System.out.println("4. Change price of item");
            System.out.println("5. Delete old sales transactions");
            System.out.println("6. Back");
            System.out.print("Choose: ");
            int choice = Integer.parseInt(Main.scanner.nextLine());

            if (choice == 1)      monthlySalesSummary();
            else if (choice == 2) adjustCustomerStatus();
            else if (choice == 3) sendOrderToManufacturer();
            else if (choice == 4) changeItemPrice();
            else if (choice == 5) deleteOldTransactions();
            else break;
        }
    }

    static void monthlySalesSummary() {
        try {
            System.out.print("Enter month (1-12): ");
            int month = Integer.parseInt(Main.scanner.nextLine());
            System.out.print("Enter year (e.g. 2026): ");
            int year = Integer.parseInt(Main.scanner.nextLine());

            if (month < 1 || month > 12) {
                System.out.println("Invalid month. Please enter a value between 1 and 12.");
                return;
            }
            if (year < 2000 || year > 2100) {
                System.out.println("Invalid year.");
                return;
            }

            System.out.println("\n=== Monthly Sales Summary: " + month + "/" + year + " ===");

            // Sales per product
            System.out.println("\n-- Sales per Product --");
            PreparedStatement ps = Main.emartConn.prepareStatement(
                "SELECT c.stock_number, ci.manufacturer, ci.model_number, " +
                "SUM(c.quantity) as total_qty, SUM(c.quantity * c.unit_price) as total_sales " +
                "FROM Contains c " +
                "JOIN CustomerOrder o ON c.order_number = o.order_number " +
                "JOIN CatalogItem ci ON c.stock_number = ci.stock_number " +
                "WHERE EXTRACT(MONTH FROM o.order_date) = ? " +
                "AND EXTRACT(YEAR FROM o.order_date) = ? " +
                "GROUP BY c.stock_number, ci.manufacturer, ci.model_number " +
                "ORDER BY total_sales DESC");
            ps.setInt(1, month);
            ps.setInt(2, year);
            ResultSet rs = ps.executeQuery();
            boolean found = false;
            while (rs.next()) {
                found = true;
                System.out.println("[" + rs.getString("stock_number") + "] " +
                                   rs.getString("manufacturer") + " " +
                                   rs.getString("model_number") +
                                   " | Qty Sold: " + rs.getInt("total_qty") +
                                   " | Revenue: $" + String.format("%.2f", rs.getDouble("total_sales")));
            }
            if (!found) System.out.println("No sales found for this period.");

            // Sales per category
            System.out.println("\n-- Sales per Category --");
            PreparedStatement catPs = Main.emartConn.prepareStatement(
                "SELECT ci.category, SUM(c.quantity) as total_qty, " +
                "SUM(c.quantity * c.unit_price) as total_sales " +
                "FROM Contains c " +
                "JOIN CustomerOrder o ON c.order_number = o.order_number " +
                "JOIN CatalogItem ci ON c.stock_number = ci.stock_number " +
                "WHERE EXTRACT(MONTH FROM o.order_date) = ? " +
                "AND EXTRACT(YEAR FROM o.order_date) = ? " +
                "GROUP BY ci.category ORDER BY total_sales DESC");
            catPs.setInt(1, month);
            catPs.setInt(2, year);
            ResultSet catRs = catPs.executeQuery();
            boolean catFound = false;
            while (catRs.next()) {
                catFound = true;
                System.out.println("Category: " + catRs.getString("category") +
                                   " | Qty Sold: " + catRs.getInt("total_qty") +
                                   " | Revenue: $" + String.format("%.2f", catRs.getDouble("total_sales")));
            }
            if (!catFound) System.out.println("No category data found for this period.");

            // Top customer
            System.out.println("\n-- Top Customer --");
            PreparedStatement topPs = Main.emartConn.prepareStatement(
                "SELECT o.customer_id, cu.first_name, cu.last_name, " +
                "SUM(o.total_price) as total_spent " +
                "FROM CustomerOrder o " +
                "JOIN Customer cu ON o.customer_id = cu.customer_id " +
                "WHERE EXTRACT(MONTH FROM o.order_date) = ? " +
                "AND EXTRACT(YEAR FROM o.order_date) = ? " +
                "GROUP BY o.customer_id, cu.first_name, cu.last_name " +
                "ORDER BY total_spent DESC FETCH FIRST 1 ROWS ONLY");
            topPs.setInt(1, month);
            topPs.setInt(2, year);
            ResultSet topRs = topPs.executeQuery();
            if (topRs.next()) {
                System.out.println(topRs.getString("first_name") + " " +
                                   topRs.getString("last_name") +
                                   " (ID: " + topRs.getString("customer_id") + ")" +
                                   " | Total Spent: $" + String.format("%.2f", topRs.getDouble("total_spent")));
            } else {
                System.out.println("No orders this month.");
            }

        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    static void adjustCustomerStatus() {
        try {
            System.out.println("\n=== Adjust Customer Status ===");
            System.out.println("1. Auto-adjust all customers based on last 3 orders");
            System.out.println("2. Manually set a customer's status");
            System.out.print("Choose: ");
            int choice = Integer.parseInt(Main.scanner.nextLine());

            if (choice == 1) {
                  ResultSet customers = Main.emartConn.createStatement().executeQuery(
                      "SELECT customer_id FROM Customer");
                  List<String> customerIds = new ArrayList<>();
                  while (customers.next()) {
                      customerIds.add(customers.getString("customer_id"));
                  }
                  customers.close();

                  int updated = 0;
                  for (String cid : customerIds) {
                      PreparedStatement ps = Main.emartConn.prepareStatement(
                        "SELECT SUM(total_price) FROM (" +
                        "SELECT total_price FROM CustomerOrder WHERE customer_id = ? " +
                        "ORDER BY order_number DESC FETCH FIRST 3 ROWS ONLY)");
                    ps.setString(1, cid);
                    ResultSet rs = ps.executeQuery();
                    rs.next();
                    double total = rs.getDouble(1);

                    String newStatus;
                    if (total > 500)      newStatus = "Gold";
                    else if (total > 100) newStatus = "Silver";
                    else if (total > 0)   newStatus = "Green";
                    else                  newStatus = "New";

                    PreparedStatement updatePs = Main.emartConn.prepareStatement(
                        "UPDATE Customer SET status = ? WHERE customer_id = ?");
                    updatePs.setString(1, newStatus);
                    updatePs.setString(2, cid);
                    updatePs.executeUpdate();
                    updated++;
                }
                Main.emartConn.commit();
                System.out.println("Updated status for " + updated + " customers.");

            } else if (choice == 2) {
                System.out.print("Enter customer ID: ");
                String cid = Main.scanner.nextLine();
                System.out.println("New status options: New, Green, Silver, Gold");
                System.out.print("Enter new status: ");
                String newStatus = Main.scanner.nextLine();

                if (!Arrays.asList("New", "Green", "Silver", "Gold").contains(newStatus)) {
                    System.out.println("Invalid status.");
                    return;
                }

                PreparedStatement ps = Main.emartConn.prepareStatement(
                    "UPDATE Customer SET status = ? WHERE customer_id = ?");
                ps.setString(1, newStatus);
                ps.setString(2, cid);
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    System.out.println("Customer not found.");
                    Main.emartConn.rollback();
                } else {
                    Main.emartConn.commit();
                    System.out.println("Status updated to " + newStatus + ".");
                }
            }

        } catch (SQLException e) {
            try { Main.emartConn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            System.out.println("Error: " + e.getMessage());
        }
    }

    static void sendOrderToManufacturer() {
        try {
            System.out.print("Enter manufacturer name: ");
            String manufacturer = Main.scanner.nextLine();

            // Use LIKE so partial names work (e.g. "hp" matches "HP")
            PreparedStatement ps = Main.emartConn.prepareStatement(
                "SELECT stock_number, model_number, price FROM CatalogItem " +
                "WHERE LOWER(manufacturer) LIKE LOWER(?)");
            ps.setString(1, "%" + manufacturer + "%");
            ResultSet rs = ps.executeQuery();

            System.out.println("\n=== Order to Manufacturer: " + manufacturer + " ===");
            boolean found = false;
            while (rs.next()) {
                found = true;
                System.out.println("[" + rs.getString("stock_number") + "] " +
                                   rs.getString("model_number") +
                                   " | Price: $" + rs.getString("price"));
            }

            if (!found) {
                System.out.println("No products found for manufacturer: " + manufacturer);
            } else {
                System.out.println("\n(Order printed above - send to manufacturer.)");
            }

        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    static void changeItemPrice() {
        try {
            System.out.print("Enter stock number: ");
            String stockNum = Main.scanner.nextLine();

            PreparedStatement checkPs = Main.emartConn.prepareStatement(
                "SELECT stock_number, manufacturer, model_number, price " +
                "FROM CatalogItem WHERE stock_number = ?");
            checkPs.setString(1, stockNum);
            ResultSet rs = checkPs.executeQuery();

            if (!rs.next()) {
                System.out.println("Item not found.");
                return;
            }

            System.out.println("Current item: " + rs.getString("manufacturer") + " " +
                               rs.getString("model_number") +
                               " | Current price: $" + rs.getString("price"));
            System.out.print("Enter new price: ");
            double newPrice = Double.parseDouble(Main.scanner.nextLine());

            if (newPrice < 0) {
                System.out.println("Price cannot be negative.");
                return;
            }

            PreparedStatement ps = Main.emartConn.prepareStatement(
                "UPDATE CatalogItem SET price = ? WHERE stock_number = ?");
            ps.setDouble(1, newPrice);
            ps.setString(2, stockNum);
            ps.executeUpdate();
            Main.emartConn.commit();
            System.out.println("Price updated to $" + String.format("%.2f", newPrice));

        } catch (SQLException e) {
            try { Main.emartConn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            System.out.println("Error: " + e.getMessage());
        }
    }

    static void deleteOldTransactions() {
        try {
            System.out.println("\n=== Delete Old Transactions ===");
            System.out.println("This will delete all orders except the 3 most recent per customer.");
            System.out.print("Are you sure? (yes/no): ");
            if (!Main.scanner.nextLine().equalsIgnoreCase("yes")) return;

            // Delete Contains rows first due to foreign key constraint
            Main.emartConn.createStatement().executeUpdate(
                "DELETE FROM Contains WHERE order_number IN (" +
                "SELECT order_number FROM CustomerOrder WHERE order_number NOT IN (" +
                "SELECT order_number FROM (" +
                "SELECT order_number, ROW_NUMBER() OVER " +
                "(PARTITION BY customer_id ORDER BY order_number DESC) as rn " +
                "FROM CustomerOrder) WHERE rn <= 3))");

            // Delete old orders
            int deleted = Main.emartConn.createStatement().executeUpdate(
                "DELETE FROM CustomerOrder WHERE order_number NOT IN (" +
                "SELECT order_number FROM (" +
                "SELECT order_number, ROW_NUMBER() OVER " +
                "(PARTITION BY customer_id ORDER BY order_number DESC) as rn " +
                "FROM CustomerOrder) WHERE rn <= 3)");

            Main.emartConn.commit();
            System.out.println("Deleted " + deleted + " old transaction(s).");

        } catch (SQLException e) {
            try { Main.emartConn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            System.out.println("Error: " + e.getMessage());
        }
    }
}