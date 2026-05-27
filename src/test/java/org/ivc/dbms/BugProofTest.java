package org.ivc.dbms;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.ArrayList;
import java.io.InputStream;
import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BugProofTest extends BaseTest {

    @AfterEach
    void rollback() { rollbackAll(); }


//Avaneesh is working on this
    // =========================================================
    // BUG 2 — Inventory goes negative; no pre-commit stock check
    // =========================================================

    /**
     * Attempts to fill an order for MORE units than are in stock.
     * The buggy code runs this UPDATE unconditionally and relies on
     * the CHECK constraint to catch it — but by then the eMART order
     * is already committed. This test proves the constraint fires.
     */
    @Test
    @Order(2)
    void bug2_fillOrder_quantityGoesNegative_constraintViolation() throws SQLException {
        // Get current quantity
        PreparedStatement qPs = depotConn.prepareStatement(
            "SELECT quantity FROM InventoryItem WHERE stock_number = 'AA00301'");
        ResultSet qRs = qPs.executeQuery();
        assertTrue(qRs.next());
        int currentQty = qRs.getInt("quantity"); // e.g. 25

        // Try to deduct MORE than available — exactly what fillOrderAuto does
        int overOrder = currentQty + 10;
        PreparedStatement updatePs = depotConn.prepareStatement(
            "UPDATE InventoryItem SET quantity = quantity - ? WHERE stock_number = 'AA00301'");
        updatePs.setInt(1, overOrder);

        // Must throw — quantity would go negative, violating CHECK (quantity >= 0)
        assertThrows(SQLException.class, updatePs::executeUpdate,
            "Filling an order for more units than available must throw — " +
            "proving the depot has no pre-check and the constraint is the only guard.");
    }


    // =========================================================
    // BUG 4 — Revenue overstated: pre-discount vs actual total
    // =========================================================

    /**
     * Places an order with a 10% discount applied.
     * The monthly report query uses SUM(quantity * unit_price) from Contains —
     * which is the pre-discount price. We compare it against the actual
     * total_price stored on the order and prove they differ.
     */
    @Test
    @Order(5)
    void bug4_monthlyRevenue_usesPreDiscountPrice() throws SQLException {
        ResultSet m = emartConn.createStatement().executeQuery(
            "SELECT NVL(MAX(order_number),0)+1 FROM CustomerOrder");
        m.next();
        int orderNum = m.getInt(1);

        double unitPrice   = 279.99;
        double actualTotal = unitPrice * 0.90;

        PreparedStatement orderPs = emartConn.prepareStatement(
            "INSERT INTO CustomerOrder VALUES (?, CURRENT_DATE, ?, 'Lkim', " +
            "(SELECT MAX(rule_id) FROM DiscountRules))");
        orderPs.setInt(1, orderNum);
        orderPs.setDouble(2, actualTotal);
        orderPs.executeUpdate();

        PreparedStatement containsPs = emartConn.prepareStatement(
            "INSERT INTO Contains VALUES (?, 'AA00302', 1, ?)");
        containsPs.setInt(1, orderNum);
        containsPs.setDouble(2, unitPrice);
        containsPs.executeUpdate();

        // What the buggy report shows — pre-discount sum from Contains
        PreparedStatement reportedPs = emartConn.prepareStatement(
            "SELECT SUM(c.quantity * c.unit_price) as reported_revenue " +
            "FROM Contains c WHERE c.order_number = ?");
        reportedPs.setInt(1, orderNum);
        ResultSet reportedRs = reportedPs.executeQuery();
        assertTrue(reportedRs.next(), "Contains row should exist for this order");
        double reportedRevenue = reportedRs.getDouble("reported_revenue");

        // What was actually charged
        PreparedStatement actualPs = emartConn.prepareStatement(
            "SELECT total_price FROM CustomerOrder WHERE order_number = ?");
        actualPs.setInt(1, orderNum);
        ResultSet actualRs = actualPs.executeQuery();
        assertTrue(actualRs.next(), "Order should exist");
        double actualRevenue = actualRs.getDouble("total_price");

        assertTrue(reportedRevenue > actualRevenue,
            "Bug confirmed: report shows $" + reportedRevenue +
            " but actual charged amount was $" + actualRevenue);

        assertEquals(279.99, reportedRevenue, 0.01, "Report shows full pre-discount price");
        assertEquals(251.99, actualRevenue,   0.01, "Actual charged amount is discounted");
    }

    @Test
    @Order(8)
    void rerunOrder_usesCurrentCatalogPrice() throws SQLException {
        // Step 1: Insert a new order at old price
        ResultSet rs = emartConn.createStatement().executeQuery(
            "SELECT NVL(MAX(order_number),0)+1 FROM CustomerOrder");
        rs.next();
        int orderNum = rs.getInt(1);

        // Original order at 69.99
        PreparedStatement orderPs = emartConn.prepareStatement(
            "INSERT INTO CustomerOrder VALUES (?, CURRENT_DATE, 62.99, 'Lkim', " +
            "(SELECT MAX(rule_id) FROM DiscountRules))");
        orderPs.setInt(1, orderNum);
        orderPs.executeUpdate();

        PreparedStatement containsPs = emartConn.prepareStatement(
            "INSERT INTO Contains VALUES (?, 'AA00301', 1, 69.99)");
        containsPs.setInt(1, orderNum);
        containsPs.executeUpdate();

        // Step 2: Update catalog to new price
        PreparedStatement priceUpdate = emartConn.prepareStatement(
            "UPDATE CatalogItem SET price = 149.99 WHERE stock_number = 'AA00301'");
        priceUpdate.executeUpdate();

        // Step 3: Simulate rerunOrder
        List<String[]> cart = new ArrayList<>();
        Main.loggedInCustomerId = "Lkim"; // Make sure logged-in user is set
        // Mock input to select our order number
        InputStream sysInBackup = System.in; // backup System.in
        ByteArrayInputStream in = new ByteArrayInputStream((orderNum + "\n").getBytes());
        System.setIn(in);

        Main.rerunOrder(cart);

        System.setIn(sysInBackup); // restore System.in

        // Step 4: Check that cart contains current catalog price
        assertFalse(cart.isEmpty(), "Cart should be loaded");

        double loadedPrice = Double.parseDouble(cart.get(0)[1]);
        assertEquals(149.99, loadedPrice, 0.001,
            "Re-run should load current catalog price, not old price");

        int quantity = Integer.parseInt(cart.get(0)[2]);
        assertEquals(1, quantity, "Quantity should match original order");
    }

    // =========================================================
    // BUG 6 — Re-run uses stale price from old Contains row
    // =========================================================

    /**
     * Creates an order at price $69.99, then changes the price to $149.99.
     * Re-run loads items from Contains — which still has $69.99.
     * Proves the cart is loaded with the old price, not the current catalog price.
     */
    @Test
    @Order(7)
    void bug6_rerunOrder_usesStalePrice() throws SQLException {
        // Insert original order at $69.99
        ResultSet m = emartConn.createStatement().executeQuery(
            "SELECT NVL(MAX(order_number),0)+1 FROM CustomerOrder");
        m.next();
        int orderNum = m.getInt(1);

        PreparedStatement orderPs = emartConn.prepareStatement(
            "INSERT INTO CustomerOrder VALUES (?, CURRENT_DATE, 62.99, 'Lkim', " +
            "(SELECT MAX(rule_id) FROM DiscountRules))");
        orderPs.setInt(1, orderNum);
        orderPs.executeUpdate();

        PreparedStatement containsPs = emartConn.prepareStatement(
            "INSERT INTO Contains VALUES (?, 'AA00301', 1, 69.99)");
        containsPs.setInt(1, orderNum);
        containsPs.executeUpdate();

        // Manager raises the price
        PreparedStatement priceUpdate = emartConn.prepareStatement(
            "UPDATE CatalogItem SET price = 149.99 WHERE stock_number = 'AA00301'");
        priceUpdate.executeUpdate();

        // Simulate rerunOrder: load from Contains (stale)
        PreparedStatement rerunPs = emartConn.prepareStatement(
            "SELECT unit_price FROM Contains WHERE order_number = ?");
        rerunPs.setInt(1, orderNum);
        ResultSet rerunRs = rerunPs.executeQuery();
        assertTrue(rerunRs.next());
        double stalePrice = rerunRs.getDouble("unit_price");

        // Fetch current catalog price
        PreparedStatement currentPs = emartConn.prepareStatement(
            "SELECT price FROM CatalogItem WHERE stock_number = 'AA00301'");
        ResultSet currentRs = currentPs.executeQuery();
        assertTrue(currentRs.next());
        double currentPrice = currentRs.getDouble("price");

        // Prove the re-run would charge the wrong price
        assertNotEquals(currentPrice, stalePrice, 0.001,
            "Re-run loads stale price ($" + stalePrice + ") from Contains " +
            "instead of current catalog price ($" + currentPrice + ").");
        assertEquals(69.99,  stalePrice,   0.001, "Re-run sees old price");
        assertEquals(149.99, currentPrice, 0.001, "Catalog has updated price");
    }

    // =========================================================
    // BUG 10 — Manufacturer case mismatch breaks replenishment grouping
    // =========================================================

    /**
     * Inserts inventory items with the same manufacturer stored under
     * different cases ('HP', 'hp', 'Hp'). The buggy GROUP BY uses the
     * raw manufacturer column — so these are counted as 3 separate groups,
     * each with count=1, never triggering the HAVING COUNT(*) >= 3 threshold.
     */
    @Test
    @Order(10)
    void bug10_manufacturerCaseMismatch_replenishmentNeverTriggers() throws SQLException {
        // Insert 3 items with the same manufacturer in different cases
        // Using stock numbers that won't conflict
        // DELETE whichever version of this you currently have and replace with:
        String[][] testItems = {
            {"TC00001", "HP", "ModelA", "A1"},
            {"TC00002", "hp", "ModelB", "B2"},
            {"TC00003", "Hp", "ModelC", "C3"}
        };

        for (String[] item : testItems) {
            depotConn.prepareStatement(
                "DELETE FROM InventoryItem WHERE stock_number = '" + item[0] + "'")
                .executeUpdate();

            PreparedStatement ins = depotConn.prepareStatement(
                "INSERT INTO InventoryItem VALUES (?, ?, ?, 0, 5, 20, ?, 0)");
            ins.setString(1, item[0]);
            ins.setString(2, item[1]);
            ins.setString(3, item[2]);
            ins.setString(4, item[3]);  // location now part of the array
            ins.executeUpdate();
        }

        // Buggy query: GROUP BY raw manufacturer — treats 'HP','hp','Hp' as 3 groups
        ResultSet buggyRs = depotConn.createStatement().executeQuery(
            "SELECT manufacturer, COUNT(*) as low_count " +
            "FROM InventoryItem " +
            "WHERE quantity < min_stock_level " +
            "GROUP BY manufacturer " +
            "HAVING COUNT(*) >= 3");

        int buggyGroups = 0;
        while (buggyRs.next()) buggyGroups++;

        // Correct query: GROUP BY LOWER(manufacturer) — merges all 3 into one group
        ResultSet correctRs = depotConn.createStatement().executeQuery(
            "SELECT LOWER(manufacturer) as manufacturer, COUNT(*) as low_count " +
            "FROM InventoryItem " +
            "WHERE quantity < min_stock_level " +
            "GROUP BY LOWER(manufacturer) " +
            "HAVING COUNT(*) >= 3");

        int correctGroups = 0;
        while (correctRs.next()) correctGroups++;

        assertEquals(0, buggyGroups,
            "Buggy query sees 0 groups with count >= 3 because 'HP','hp','Hp' are split.");
        assertEquals(1, correctGroups,
            "Correct query sees 1 group — all three case variants merge under LOWER().");
    }
}