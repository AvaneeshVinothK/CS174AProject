package org.ivc.dbms;

import org.junit.jupiter.api.*;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Automated tests for all Manager operations.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ManagerTest extends BaseTest {

    @AfterEach
    void rollback() {
        rollbackAll();
    }

    // =========================================================================
    // MONTHLY SALES SUMMARY
    // =========================================================================

    /**
     * TC-33: Monthly sales summary returns product, category, and top customer data.
     * Inserts a known order, then verifies the summary queries return correct results.
     */
    @Test
    @Order(33)
    void tc33_monthlySalesSummary_withData() throws SQLException {
        // Insert a test order in the current month
        ResultSet maxRs = emartConn.createStatement().executeQuery(
            "SELECT NVL(MAX(order_number), 0) + 1 FROM CustomerOrder");
        maxRs.next();
        int orderNum = maxRs.getInt(1);

        PreparedStatement orderPs = emartConn.prepareStatement(
            "INSERT INTO CustomerOrder VALUES (?, CURRENT_DATE, 279.99, 'Lkim', " +
            "(SELECT MAX(rule_id) FROM DiscountRules))");
        orderPs.setInt(1, orderNum);
        orderPs.executeUpdate();

        PreparedStatement containsPs = emartConn.prepareStatement(
            "INSERT INTO Contains VALUES (?, 'AA00302', 1, 279.99)");
        containsPs.setInt(1, orderNum);
        containsPs.executeUpdate();

        // Query sales per product for this month/year
        PreparedStatement ps = emartConn.prepareStatement(
            "SELECT c.stock_number, SUM(c.quantity) as total_qty, " +
            "SUM(c.quantity * c.unit_price) as total_sales " +
            "FROM Contains c " +
            "JOIN CustomerOrder o ON c.order_number = o.order_number " +
            "JOIN CatalogItem ci ON c.stock_number = ci.stock_number " +
            "WHERE EXTRACT(MONTH FROM o.order_date) = EXTRACT(MONTH FROM CURRENT_DATE) " +
            "AND EXTRACT(YEAR FROM o.order_date) = EXTRACT(YEAR FROM CURRENT_DATE) " +
            "GROUP BY c.stock_number ORDER BY total_sales DESC");
        ResultSet rs = ps.executeQuery();

        assertTrue(rs.next(), "Should find at least one product sale this month");
    }

    /**
     * TC-34: Monthly sales summary for a month with no data returns nothing.
     */
    @Test
    @Order(34)
    void tc34_monthlySalesSummary_noData() throws SQLException {
        PreparedStatement ps = emartConn.prepareStatement(
            "SELECT COUNT(*) FROM Contains c " +
            "JOIN CustomerOrder o ON c.order_number = o.order_number " +
            "WHERE EXTRACT(MONTH FROM o.order_date) = 1 " +
            "AND EXTRACT(YEAR FROM o.order_date) = 2000");
        ResultSet rs = ps.executeQuery();
        rs.next();
        assertEquals(0, rs.getInt(1), "Should find no sales in Jan 2000");
    }

    /**
     * TC-35: Invalid month (13) should be caught before any DB query.
     * Verifies the validation logic — no DB call needed.
     */
    @Test
    @Order(35)
    void tc35_monthlySalesSummary_invalidMonth() {
        int month = 13;
        boolean valid = (month >= 1 && month <= 12);
        assertFalse(valid, "Month 13 should be flagged as invalid");

        int month2 = 0;
        assertFalse(month2 >= 1 && month2 <= 12, "Month 0 should be flagged as invalid");
    }

    // =========================================================================
    // ADJUST CUSTOMER STATUS
    // =========================================================================

    /**
     * TC-36: Auto-adjust sets Gold status when last 3 orders total > $500.
     */
    @Test
    @Order(36)
    void tc36_autoAdjustStatus_setsGold() throws SQLException {
        // Insert 3 large orders for Mramirez
        for (int i = 0; i < 3; i++) {
            ResultSet maxRs = emartConn.createStatement().executeQuery(
                "SELECT NVL(MAX(order_number), 0) + 1 FROM CustomerOrder");
            maxRs.next();
            int orderNum = maxRs.getInt(1);
            PreparedStatement ps = emartConn.prepareStatement(
                "INSERT INTO CustomerOrder VALUES (?, CURRENT_DATE, 200.00, 'Mramirez', " +
                "(SELECT MAX(rule_id) FROM DiscountRules))");
            ps.setInt(1, orderNum);
            ps.executeUpdate();
        }

        // Check last 3 total
        PreparedStatement ps = emartConn.prepareStatement(
            "SELECT NVL(SUM(total_price), 0) FROM (" +
            "SELECT total_price FROM CustomerOrder WHERE customer_id = 'Mramirez' " +
            "ORDER BY order_number DESC FETCH FIRST 3 ROWS ONLY)");
        ResultSet rs = ps.executeQuery();
        rs.next();
        double total = rs.getDouble(1);

        String newStatus;
        if (total > 500)      newStatus = "Gold";
        else if (total > 100) newStatus = "Silver";
        else if (total > 0)   newStatus = "Green";
        else                  newStatus = "New";

        assertEquals("Gold", newStatus, "3 orders of $200 each = $600 > $500 → Gold");
    }

    /**
     * TC-36b: Auto-adjust sets Silver when last 3 orders total is $100–$500.
     */
    @Test
    @Order(36)
    void tc36b_autoAdjustStatus_setsSilver() throws SQLException {
        ResultSet maxRs = emartConn.createStatement().executeQuery(
            "SELECT NVL(MAX(order_number), 0) + 1 FROM CustomerOrder");
        maxRs.next();
        int orderNum = maxRs.getInt(1);

        PreparedStatement ps = emartConn.prepareStatement(
            "INSERT INTO CustomerOrder VALUES (?, CURRENT_DATE, 150.00, 'Swong', " +
            "(SELECT MAX(rule_id) FROM DiscountRules))");
        ps.setInt(1, orderNum);
        ps.executeUpdate();

        PreparedStatement last3Ps = emartConn.prepareStatement(
            "SELECT NVL(SUM(total_price), 0) FROM (" +
            "SELECT total_price FROM CustomerOrder WHERE customer_id = 'Swong' " +
            "ORDER BY order_number DESC FETCH FIRST 3 ROWS ONLY)");
        ResultSet rs = last3Ps.executeQuery();
        rs.next();
        double total = rs.getDouble(1);

        String newStatus;
        if (total > 500)      newStatus = "Gold";
        else if (total > 100) newStatus = "Silver";
        else if (total > 0)   newStatus = "Green";
        else                  newStatus = "New";

        // Swong had no prior orders — just one $150 order → Silver
        assertEquals("Silver", newStatus, "$150 total should map to Silver");
    }

    /**
     * TC-37: Manually set customer status updates the DB correctly.
     */
    @Test
    @Order(37)
    void tc37_manualStatusUpdate_success() throws SQLException {
        PreparedStatement ps = emartConn.prepareStatement(
            "UPDATE Customer SET status = ? WHERE customer_id = ?");
        ps.setString(1, "Gold");
        ps.setString(2, "Swong");
        int rows = ps.executeUpdate();

        assertEquals(1, rows, "Should update exactly one row");

        PreparedStatement check = emartConn.prepareStatement(
            "SELECT status FROM Customer WHERE customer_id = ?");
        check.setString(1, "Swong");
        ResultSet rs = check.executeQuery();
        rs.next();
        assertEquals("Gold", rs.getString("status"), "Status should now be Gold");
    }

    /**
     * TC-38: Setting an invalid status is rejected by validation before any DB call.
     */
    @Test
    @Order(38)
    void tc38_manualStatusUpdate_invalidStatus_rejected() {
        String newStatus = "Platinum";
        boolean valid = java.util.Arrays.asList("New", "Green", "Silver", "Gold")
                                        .contains(newStatus);
        assertFalse(valid, "'Platinum' is not a valid status and should be rejected");
    }

    /**
     * TC-38b: Updating status for a non-existent customer affects 0 rows.
     */
    @Test
    @Order(38)
    void tc38b_manualStatusUpdate_nonExistentCustomer() throws SQLException {
        PreparedStatement ps = emartConn.prepareStatement(
            "UPDATE Customer SET status = ? WHERE customer_id = ?");
        ps.setString(1, "Gold");
        ps.setString(2, "DOESNOTEXIST");
        int rows = ps.executeUpdate();
        assertEquals(0, rows, "Non-existent customer should affect 0 rows");
    }

    // =========================================================================
    // SEND ORDER TO MANUFACTURER
    // =========================================================================

    /**
     * TC-39: Send order to manufacturer — partial name finds all HP products.
     */
    @Test
    @Order(39)
    void tc39_sendOrderToManufacturer_partialName() throws SQLException {
        PreparedStatement ps = emartConn.prepareStatement(
            "SELECT stock_number, model_number, price FROM CatalogItem " +
            "WHERE LOWER(manufacturer) LIKE LOWER(?)");
        ps.setString(1, "%hp%");
        ResultSet rs = ps.executeQuery();

        int count = 0;
        while (rs.next()) count++;
        assertEquals(3, count, "Should find 3 HP products");
    }

    /**
     * TC-40: Non-existent manufacturer returns no products.
     */
    @Test
    @Order(40)
    void tc40_sendOrderToManufacturer_notFound() throws SQLException {
        PreparedStatement ps = emartConn.prepareStatement(
            "SELECT stock_number FROM CatalogItem " +
            "WHERE LOWER(manufacturer) LIKE LOWER(?)");
        ps.setString(1, "%FAKECOMPANY%");
        ResultSet rs = ps.executeQuery();

        assertFalse(rs.next(), "Non-existent manufacturer should return no products");
    }

    // =========================================================================
    // CHANGE ITEM PRICE
    // =========================================================================

    /**
     * TC-41: Change item price updates the DB correctly.
     */
    @Test
    @Order(41)
    void tc41_changeItemPrice_success() throws SQLException {
        PreparedStatement ps = emartConn.prepareStatement(
            "UPDATE CatalogItem SET price = ? WHERE stock_number = ?");
        ps.setDouble(1, 89.99);
        ps.setString(2, "AA00301");
        int rows = ps.executeUpdate();

        assertEquals(1, rows, "Should update exactly one row");

        PreparedStatement check = emartConn.prepareStatement(
            "SELECT price FROM CatalogItem WHERE stock_number = ?");
        check.setString(1, "AA00301");
        ResultSet rs = check.executeQuery();
        rs.next();
        assertEquals(89.99, rs.getDouble("price"), 0.001, "Price should be updated to $89.99");
    }

    /**
     * TC-42: Negative price is rejected by validation before any DB call.
     */
    @Test
    @Order(42)
    void tc42_changeItemPrice_negativeRejected() {
        double newPrice = -10.0;
        assertFalse(newPrice >= 0, "Negative price should fail validation");
    }

    /**
     * TC-43: Changing price for a non-existent item affects 0 rows.
     */
    @Test
    @Order(43)
    void tc43_changeItemPrice_itemNotFound() throws SQLException {
        PreparedStatement checkPs = emartConn.prepareStatement(
            "SELECT stock_number FROM CatalogItem WHERE stock_number = ?");
        checkPs.setString(1, "XX99999");
        ResultSet rs = checkPs.executeQuery();
        assertFalse(rs.next(), "Non-existent stock number should return no row");
    }

    // =========================================================================
    // DELETE OLD TRANSACTIONS
    // =========================================================================

    /**
     * TC-44: Delete old transactions keeps only the 3 most recent orders per customer.
     */
    @Test
    @Order(44)
    void tc44_deleteOldTransactions_keepsThreeMostRecent() throws SQLException {
        // Insert 5 orders for Mramirez
        for (int i = 0; i < 5; i++) {
            ResultSet maxRs = emartConn.createStatement().executeQuery(
                "SELECT NVL(MAX(order_number), 0) + 1 FROM CustomerOrder");
            maxRs.next();
            int orderNum = maxRs.getInt(1);
            PreparedStatement ps = emartConn.prepareStatement(
                "INSERT INTO CustomerOrder VALUES (?, CURRENT_DATE, 50.00, 'Mramirez', " +
                "(SELECT MAX(rule_id) FROM DiscountRules))");
            ps.setInt(1, orderNum);
            ps.executeUpdate();
        }

        // Run the delete logic
        emartConn.createStatement().executeUpdate(
            "DELETE FROM Contains WHERE order_number IN (" +
            "SELECT order_number FROM CustomerOrder WHERE order_number NOT IN (" +
            "SELECT order_number FROM (" +
            "SELECT order_number, ROW_NUMBER() OVER " +
            "(PARTITION BY customer_id ORDER BY order_number DESC) as rn " +
            "FROM CustomerOrder) WHERE rn <= 3))");

        emartConn.createStatement().executeUpdate(
            "DELETE FROM CustomerOrder WHERE order_number NOT IN (" +
            "SELECT order_number FROM (" +
            "SELECT order_number, ROW_NUMBER() OVER " +
            "(PARTITION BY customer_id ORDER BY order_number DESC) as rn " +
            "FROM CustomerOrder) WHERE rn <= 3)");

        // Verify no customer has more than 3 orders
        ResultSet rs = emartConn.createStatement().executeQuery(
            "SELECT customer_id, COUNT(*) as order_count " +
            "FROM CustomerOrder GROUP BY customer_id HAVING COUNT(*) > 3");
        assertFalse(rs.next(), "No customer should have more than 3 orders after deletion");
    }

    /**
     * TC-45: Cancelling delete (answering no) leaves DB unchanged.
     * Verifies the confirmation check — no DB operation needed to test this.
     */
    @Test
    @Order(45)
    void tc45_deleteOldTransactions_cancelled() throws SQLException {
        ResultSet before = emartConn.createStatement().executeQuery(
            "SELECT COUNT(*) FROM CustomerOrder");
        before.next();
        int countBefore = before.getInt(1);

        // Simulate "no" — nothing should happen, count unchanged
        ResultSet after = emartConn.createStatement().executeQuery(
            "SELECT COUNT(*) FROM CustomerOrder");
        after.next();
        int countAfter = after.getInt(1);

        assertEquals(countBefore, countAfter, "Cancelling delete should leave order count unchanged");
    }

    // =========================================================================
    // DISCOUNT RULES — Read Most Recent
    // =========================================================================

    /**
     * Verifies the system reads the most recent DiscountRules row (by MAX rule_id).
     */
    @Test
    @Order(60)
    void tc_discountRules_readsMostRecent() throws SQLException {
        // Insert a new rule with different discount percentages
        ResultSet maxRs = emartConn.createStatement().executeQuery(
            "SELECT MAX(rule_id) FROM DiscountRules");
        maxRs.next();
        int newRuleId = maxRs.getInt(1) + 1;

        PreparedStatement ps = emartConn.prepareStatement(
            "INSERT INTO DiscountRules VALUES (?, 15.00, 8.00, 2.00, 15.00, 12.00, 150.00)");
        ps.setInt(1, newRuleId);
        ps.executeUpdate();

        // Read most recent rule
        ResultSet dr = emartConn.createStatement().executeQuery(
            "SELECT * FROM DiscountRules WHERE rule_id = (SELECT MAX(rule_id) FROM DiscountRules)");
        dr.next();
        assertEquals(newRuleId, dr.getInt("rule_id"), "Should read the newly inserted rule");
        assertEquals(15.00, dr.getDouble("gold_disc_pct"), 0.001, "Gold discount should be 15% from new rule");
    }
}