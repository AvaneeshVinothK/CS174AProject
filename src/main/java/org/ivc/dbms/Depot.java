package org.ivc.dbms;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class Depot {

    static void depotMenu() {
        while (true) {
            System.out.println("\n=== eDEPOT Menu ===");
            System.out.println("1. Receive shipping notice");
            System.out.println("2. Receive shipment");
            System.out.println("3. Check item quantity");
            System.out.println("4. Fill an order");
            System.out.println("5. Back");
            System.out.print("Choose: ");
            int choice = Integer.parseInt(Main.scanner.nextLine());

            if (choice == 1)      receiveShippingNotice();
            else if (choice == 2) receiveShipment();
            else if (choice == 3) checkItemQuantity();
            else if (choice == 4) fillOrder();
            else break;
        }
    }

    static void receiveShippingNotice() {
        try {
            System.out.print("Enter shipping notice ID: ");
            int noticeId = Integer.parseInt(Main.scanner.nextLine());
            System.out.print("Enter shipping company: ");
            String shippingCompany = Main.scanner.nextLine();

            PreparedStatement noticePs = Main.depotConn.prepareStatement(
                "INSERT INTO ShippingNotice VALUES (?, ?)");
            noticePs.setInt(1, noticeId);
            noticePs.setString(2, shippingCompany);
            noticePs.executeUpdate();

            System.out.println("Enter items in this shipment (enter blank stock number to stop):");
            while (true) {
                System.out.print("Stock number: ");
                String stockNum = Main.scanner.nextLine().trim();
                if (stockNum.isEmpty()) break;

                System.out.print("Quantity: ");
                int qty = Integer.parseInt(Main.scanner.nextLine());

                // Try to update existing inventory item first
                PreparedStatement updatePs = Main.depotConn.prepareStatement(
                    "UPDATE InventoryItem SET replenishment_qty = replenishment_qty + ? " +
                    "WHERE stock_number = ?");
                updatePs.setInt(1, qty);
                updatePs.setString(2, stockNum);
                int rows = updatePs.executeUpdate();

                // If item doesn't exist yet, create it BEFORE inserting into Lists
                if (rows == 0) {
                    System.out.print("New item - enter manufacturer: ");
                    String manufacturer = Main.scanner.nextLine();
                    System.out.print("Enter model number: ");
                    String modelNumber = Main.scanner.nextLine();
                    System.out.print("Enter min stock level: ");
                    int minStock = Integer.parseInt(Main.scanner.nextLine());
                    System.out.print("Enter max stock level: ");
                    int maxStock = Integer.parseInt(Main.scanner.nextLine());
                    System.out.print("Enter location: ");
                    String location = Main.scanner.nextLine();

                    PreparedStatement insertPs = Main.depotConn.prepareStatement(
                        "INSERT INTO InventoryItem VALUES (?, ?, ?, 0, ?, ?, ?, ?)");
                    insertPs.setString(1, stockNum);
                    insertPs.setString(2, manufacturer);
                    insertPs.setString(3, modelNumber);
                    insertPs.setInt(4, minStock);
                    insertPs.setInt(5, maxStock);
                    insertPs.setString(6, location);
                    insertPs.setInt(7, qty);
                    insertPs.executeUpdate();
                }

                // InventoryItem is guaranteed to exist now — safe to insert into Lists
                PreparedStatement listsPs = Main.depotConn.prepareStatement(
                    "INSERT INTO Lists VALUES (?, ?, ?)");
                listsPs.setInt(1, noticeId);
                listsPs.setString(2, stockNum);
                listsPs.setInt(3, qty);
                listsPs.executeUpdate();

                System.out.println("Item added to notice.");
            }

            Main.depotConn.commit();
            System.out.println("Shipping notice #" + noticeId + " recorded.");

        } catch (SQLException e) {
            try { Main.depotConn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            System.out.println("Error: " + e.getMessage());
        }
    }

    static void receiveShipment() {
        try {
            System.out.print("Enter shipping notice ID to receive: ");
            int noticeId = Integer.parseInt(Main.scanner.nextLine());

            // Verify notice exists
            PreparedStatement checkPs = Main.depotConn.prepareStatement(
                "SELECT notice_id FROM ShippingNotice WHERE notice_id = ?");
            checkPs.setInt(1, noticeId);
            ResultSet checkRs = checkPs.executeQuery();
            if (!checkRs.next()) {
                System.out.println("Shipping notice not found.");
                return;
            }

            // Get all items in this notice
            PreparedStatement itemsPs = Main.depotConn.prepareStatement(
                "SELECT stock_number, quantity FROM Lists WHERE notice_id = ?");
            itemsPs.setInt(1, noticeId);
            ResultSet items = itemsPs.executeQuery();

            System.out.println("\nReceiving shipment for notice #" + noticeId + ":");
            boolean found = false;
            while (items.next()) {
                found = true;
                String stockNum = items.getString("stock_number");
                int qty = items.getInt("quantity");

                // Increase actual quantity, decrease replenishment_qty
                PreparedStatement updatePs = Main.depotConn.prepareStatement(
                    "UPDATE InventoryItem SET quantity = quantity + ?, " +
                    "replenishment_qty = replenishment_qty - ? " +
                    "WHERE stock_number = ?");
                updatePs.setInt(1, qty);
                updatePs.setInt(2, qty);
                updatePs.setString(3, stockNum);
                updatePs.executeUpdate();

                System.out.println("  [" + stockNum + "] +" + qty + " units received.");
            }

            if (!found) {
                System.out.println("No items found for this notice.");
                return;
            }

            Main.depotConn.commit();
            System.out.println("Shipment received and inventory updated.");

        } catch (SQLException e) {
            try { Main.depotConn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            System.out.println("Error: " + e.getMessage());
        }
    }

    static void checkItemQuantity() {
        try {
            System.out.print("Enter stock number: ");
            String stockNum = Main.scanner.nextLine().trim();

            PreparedStatement ps = Main.depotConn.prepareStatement(
                "SELECT stock_number, manufacturer, model_number, quantity, " +
                "min_stock_level, max_stock_level, replenishment_qty, location " +
                "FROM InventoryItem WHERE stock_number = ?");
            ps.setString(1, stockNum);
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) {
                System.out.println("Item not found in inventory.");
                return;
            }

            int qty = rs.getInt("quantity");
            int minStock = rs.getInt("min_stock_level");

            System.out.println("\n=== Inventory Details ===");
            System.out.println("Stock#:            " + rs.getString("stock_number"));
            System.out.println("Manufacturer:      " + rs.getString("manufacturer"));
            System.out.println("Model:             " + rs.getString("model_number"));
            System.out.println("Quantity:          " + qty);
            System.out.println("Min Stock Level:   " + minStock);
            System.out.println("Max Stock Level:   " + rs.getInt("max_stock_level"));
            System.out.println("On Order:          " + rs.getInt("replenishment_qty"));
            System.out.println("Location:          " + rs.getString("location"));

            if (qty < minStock) {
                System.out.println("WARNING: Quantity is below minimum stock level!");
            }

        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    static void fillOrder() {
        try {
            System.out.print("Enter eMART order number to fill: ");
            int orderNum = Integer.parseInt(Main.scanner.nextLine());

            // Verify order exists in eMART
            PreparedStatement checkPs = Main.emartConn.prepareStatement(
                "SELECT order_number FROM CustomerOrder WHERE order_number = ?");
            checkPs.setInt(1, orderNum);
            ResultSet checkRs = checkPs.executeQuery();
            if (!checkRs.next()) {
                System.out.println("Order not found in eMART.");
                return;
            }

            // Fetch items from eMART
            PreparedStatement itemsPs = Main.emartConn.prepareStatement(
                "SELECT c.stock_number, c.quantity, ci.manufacturer " +
                "FROM Contains c " +
                "JOIN CatalogItem ci ON c.stock_number = ci.stock_number " +
                "WHERE c.order_number = ?");
            itemsPs.setInt(1, orderNum);
            ResultSet items = itemsPs.executeQuery();

            System.out.println("\nFilling order #" + orderNum + ":");
            boolean found = false;
            while (items.next()) {
                found = true;
                String stockNum = items.getString("stock_number");
                int qty = items.getInt("quantity");

                // Check sufficient stock before decrementing
                PreparedStatement stockCheckPs = Main.depotConn.prepareStatement(
                    "SELECT quantity FROM InventoryItem WHERE stock_number = ?");
                stockCheckPs.setString(1, stockNum);
                ResultSet stockRs = stockCheckPs.executeQuery();
                if (!stockRs.next()) {
                    System.out.println("  [" + stockNum + "] NOT FOUND in inventory — skipping.");
                    continue;
                }
                int available = stockRs.getInt("quantity");
                if (available < qty) {
                    System.out.println("  [" + stockNum + "] INSUFFICIENT STOCK: need " +
                        qty + ", have " + available + " — rolling back.");
                    Main.depotConn.rollback();
                    return;
                }

                PreparedStatement updatePs = Main.depotConn.prepareStatement(
                    "UPDATE InventoryItem SET quantity = quantity - ? " +
                    "WHERE stock_number = ?");
                updatePs.setInt(1, qty);
                updatePs.setString(2, stockNum);
                updatePs.executeUpdate();

                System.out.println("  [" + stockNum + "] -" + qty + " units.");
            }

            if (!found) {
                System.out.println("No items found for this order.");
                return;
            }

            Main.depotConn.commit();
            checkReplenishment();
            System.out.println("Order #" + orderNum + " filled successfully.");

        } catch (SQLException e) {
            try { Main.depotConn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            System.out.println("Error: " + e.getMessage());
        }
    }

    // Called from Customer.checkout() automatically after every purchase
    public static void fillOrderAuto(List<String[]> cart) {
        try {
            for (String[] item : cart) {
                String stockNum = item[0];
                int qty = Integer.parseInt(item[2]);

                // Check sufficient stock before decrementing
                PreparedStatement stockCheckPs = Main.depotConn.prepareStatement(
                    "SELECT quantity FROM InventoryItem WHERE stock_number = ?");
                stockCheckPs.setString(1, stockNum);
                ResultSet stockRs = stockCheckPs.executeQuery();
                if (!stockRs.next()) {
                    throw new SQLException("Stock number " + stockNum + " not found in eDEPOT inventory.");
                }
                int available = stockRs.getInt("quantity");
                if (available < qty) {
                    throw new SQLException("Insufficient stock for " + stockNum +
                        ": need " + qty + ", have " + available + ".");
                }

                PreparedStatement updatePs = Main.depotConn.prepareStatement(
                    "UPDATE InventoryItem SET quantity = quantity - ? " +
                    "WHERE stock_number = ?");
                updatePs.setInt(1, qty);
                updatePs.setString(2, stockNum);
                updatePs.executeUpdate();
        }
            Main.depotConn.commit();
            checkReplenishment();
        } catch (SQLException e) {
            try { Main.depotConn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            try { Main.depotConn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            System.out.println("eDEPOT fill order failed: " + e.getMessage());
        }
    }

    // Checks if 3+ items from the same manufacturer are below min_stock_level
    // and auto-generates a replenishment order if so
    private static void checkReplenishment() throws SQLException {
        PreparedStatement lowPs = Main.depotConn.prepareStatement(
            "SELECT manufacturer, COUNT(*) as low_count " +
            "FROM InventoryItem " +
            "WHERE quantity < min_stock_level " +
            "GROUP BY manufacturer " +
            "HAVING COUNT(*) >= 3");
        ResultSet lowRs = lowPs.executeQuery();

        while (lowRs.next()) {
            String manufacturer = lowRs.getString("manufacturer");
            System.out.println("\nWARNING: 3+ items from " + manufacturer +
                               " are below min stock level.");
            System.out.println("Auto-generating replenishment order...");

            // Generate replenishment order ID
            ResultSet maxRs = Main.depotConn.createStatement().executeQuery(
                "SELECT NVL(MAX(order_id), 0) + 1 FROM ReplenishmentOrder");
            maxRs.next();
            int repOrderId = maxRs.getInt(1);

            // Insert replenishment order
            PreparedStatement repPs = Main.depotConn.prepareStatement(
                "INSERT INTO ReplenishmentOrder VALUES (?, ?)");
            repPs.setInt(1, repOrderId);
            repPs.setString(2, manufacturer);
            repPs.executeUpdate();

            // Get all low items from this manufacturer
            PreparedStatement lowItemsPs = Main.depotConn.prepareStatement(
                "SELECT stock_number, max_stock_level, quantity " +
                "FROM InventoryItem " +
                "WHERE manufacturer = ? AND quantity < max_stock_level");
            lowItemsPs.setString(1, manufacturer);
            ResultSet lowItems = lowItemsPs.executeQuery();

            while (lowItems.next()) {
                String stockNum = lowItems.getString("stock_number");
                int reorderQty = lowItems.getInt("max_stock_level") -
                                 lowItems.getInt("quantity");

                // Insert into Includes
                PreparedStatement includesPs = Main.depotConn.prepareStatement(
                    "INSERT INTO Includes VALUES (?, ?, ?)");
                includesPs.setInt(1, repOrderId);
                includesPs.setString(2, stockNum);
                includesPs.setInt(3, reorderQty);
                includesPs.executeUpdate();

                // Update replenishment_qty
                PreparedStatement repQtyPs = Main.depotConn.prepareStatement(
                    "UPDATE InventoryItem SET replenishment_qty = replenishment_qty + ? " +
                    "WHERE stock_number = ?");
                repQtyPs.setInt(1, reorderQty);
                repQtyPs.setString(2, stockNum);
                repQtyPs.executeUpdate();

                System.out.println("  [" + stockNum + "] Reorder qty: " + reorderQty);
            }

            Main.depotConn.commit();
            System.out.println("Replenishment order #" + repOrderId +
                               " created for " + manufacturer + ".");
        }
    }
}