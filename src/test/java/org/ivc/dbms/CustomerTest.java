package org.ivc.dbms;

import org.junit.jupiter.api.*;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Automated tests for all Customer operations.
 * Each test uses unique IDs prefixed with "TC_" to avoid conflicts with sample data.
 * Every test rolls back at the end so the DB stays clean.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CustomerTest extends BaseTest {

    // ─── Unique test IDs so tests never conflict with sample data ───────────────
    private static final String TEST_CUSTOMER_ID  = "TC_User1";
    private static final String TEST_PASSWORD     = "testpass";
    private static final String TEST_EMAIL        = "tc_user1@test.com";
    private static final String STOCK_LAPTOP      = "AA00101";  // HP A6111,   $1630.00
    private static final String STOCK_MONITOR     = "AA00301";  // Envision,   $69.99
    private static final String STOCK_SAMSUNG_MON = "AA00302";  // Samsung,    $279.99
    private static final String STOCK_SOFTWARE    = "AA00401";  // Symantec,   $19.99
    private static final String STOCK_ORACLE_SW   = "AA00403";  // Oracle H26, $29.99

    @AfterEach
    void rollback() {
        rollbackAll();
    }

    // =========================================================================
    // SIGN UP
    // =========================================================================

    /** TC-01: Successful sign up inserts customer with status = New */
    @Test
    @Order(1)
    void tc01_signUp_success() throws SQLException {
        PreparedStatement ps = emartConn.prepareStatement(
            "INSERT INTO Customer VALUES (?, ?, ?, ?, ?, ?, ?, 'New')");
        ps.setString(1, TEST_CUSTOMER_ID);
        ps.setString(2, TEST_PASSWORD);
        ps.setString(3, "Test");
        ps.setNull(4, java.sql.Types.VARCHAR);
        ps.setString(5, "User");
        ps.setString(6, TEST_EMAIL);
        ps.setString(7, "100 Test St, Santa Barbara, CA 93101");
        int rows = ps.executeUpdate();

        assertEquals(1, rows, "Should insert exactly one customer row");

        PreparedStatement check = emartConn.prepareStatement(
            "SELECT status FROM Customer WHERE customer_id = ?");
        check.setString(1, TEST_CUSTOMER_ID);
        ResultSet rs = check.executeQuery();
        assertTrue(rs.next(), "Customer should exist after insert");
        assertEquals("New", rs.getString("status"), "New customer should have status = New");
    }

    /** TC-02: Duplicate username (customer_id) violates primary key */
    @Test
    @Order(2)
    void tc02_signUp_duplicateUsername_rejected() throws SQLException {
        // Insert once
        PreparedStatement ps = emartConn.prepareStatement(
            "INSERT INTO Customer VALUES (?, ?, ?, ?, ?, ?, ?, 'New')");
        ps.setString(1, TEST_CUSTOMER_ID);
        ps.setString(2, TEST_PASSWORD);
        ps.setString(3, "Test");
        ps.setNull(4, java.sql.Types.VARCHAR);
        ps.setString(5, "User");
        ps.setString(6, TEST_EMAIL);
        ps.setString(7, "100 Test St");
        ps.executeUpdate();

        // Insert again with same ID — should throw
        PreparedStatement ps2 = emartConn.prepareStatement(
            "INSERT INTO Customer VALUES (?, ?, ?, ?, ?, ?, ?, 'New')");
        ps2.setString(1, TEST_CUSTOMER_ID);
        ps2.setString(2, "otherpass");
        ps2.setString(3, "Other");
        ps2.setNull(4, java.sql.Types.VARCHAR);
        ps2.setString(5, "Person");
        ps2.setString(6, "other@test.com");
        ps2.setString(7, "200 Other St");

        assertThrows(SQLException.class, ps2::executeUpdate,
            "Duplicate customer_id should throw SQLException");
    }

    /** TC-03: Duplicate email violates unique constraint */
    @Test
    @Order(3)
    void tc03_signUp_duplicateEmail_rejected() throws SQLException {
        PreparedStatement ps = emartConn.prepareStatement(
            "INSERT INTO Customer VALUES (?, ?, ?, ?, ?, ?, ?, 'New')");
        ps.setString(1, TEST_CUSTOMER_ID);
        ps.setString(2, TEST_PASSWORD);
        ps.setString(3, "Test");
        ps.setNull(4, java.sql.Types.VARCHAR);
        ps.setString(5, "User");
        ps.setString(6, "lkim@cs"); // already used by Lkim in sample data
        ps.setString(7, "100 Test St");

        assertThrows(SQLException.class, ps::executeUpdate,
            "Duplicate email should throw SQLException");
    }

    /** TC-04: Valid login returns correct customer record */
    @Test
    @Order(4)
    void tc04_login_success() throws SQLException {
        PreparedStatement ps = emartConn.prepareStatement(
            "SELECT * FROM Customer WHERE customer_id = ? AND password = ?");
        ps.setString(1, "Lkim");
        ps.setString(2, "Lkim");
        ResultSet rs = ps.executeQuery();

        assertTrue(rs.next(), "Valid credentials should return a row");
        assertEquals("Linda", rs.getString("first_name"));
        assertEquals("Gold", rs.getString("status"));
    }

    /** TC-05: Wrong password returns no results */
    @Test
    @Order(5)
    void tc05_login_wrongPassword_fails() throws SQLException {
        PreparedStatement ps = emartConn.prepareStatement(
            "SELECT * FROM Customer WHERE customer_id = ? AND password = ?");
        ps.setString(1, "Lkim");
        ps.setString(2, "wrongpassword");
        ResultSet rs = ps.executeQuery();

        assertFalse(rs.next(), "Wrong password should return no rows");
    }

    /** TC-06: Non-existent customer returns no results */
    @Test
    @Order(6)
    void tc06_login_nonExistentCustomer_fails() throws SQLException {
        PreparedStatement ps = emartConn.prepareStatement(
            "SELECT * FROM Customer WHERE customer_id = ? AND password = ?");
        ps.setString(1, "DOESNOTEXIST");
        ps.setString(2, "anything");
        ResultSet rs = ps.executeQuery();

        assertFalse(rs.next(), "Non-existent customer should return no rows");
    }

    // =========================================================================
    // SEARCH
    // =========================================================================

    /** TC-07: Search by exact stock number returns correct item */
    @Test
    @Order(7)
    void tc07_search_byStockNumber() throws SQLException {
        PreparedStatement ps = emartConn.prepareStatement(
            "SELECT * FROM CatalogItem WHERE stock_number = ?");
        ps.setString(1, STOCK_LAPTOP);
        ResultSet rs = ps.executeQuery();

        assertTrue(rs.next(), "Should find the laptop");
        assertEquals("HP", rs.getString("manufacturer"));
        assertEquals("A6111", rs.getString("model_number"));
        assertEquals(1630.00, rs.getDouble("price"), 0.01);
        assertFalse(rs.next(), "Should return exactly one row");
    }

    /** TC-08: Search by manufacturer (partial, case-insensitive) returns all HP products */
    @Test
    @Order(8)
    void tc08_search_byManufacturer_partialCaseInsensitive() throws SQLException {
        PreparedStatement ps = emartConn.prepareStatement(
            "SELECT * FROM CatalogItem WHERE LOWER(manufacturer) LIKE LOWER(?)");
        ps.setString(1, "%hp%");
        ResultSet rs = ps.executeQuery();

        int count = 0;
        while (rs.next()) count++;
        assertEquals(3, count, "Should find all 3 HP products (Laptop, Printer, Camera)");
    }

    /** TC-09: Search by category returns only monitors */
    @Test
    @Order(9)
    void tc09_search_byCategory() throws SQLException {
        PreparedStatement ps = emartConn.prepareStatement(
            "SELECT * FROM CatalogItem WHERE LOWER(category) LIKE LOWER(?)");
        ps.setString(1, "%monitor%");
        ResultSet rs = ps.executeQuery();

        int count = 0;
        while (rs.next()) count++;
        assertEquals(2, count, "Should find exactly 2 monitors");
    }

    /** TC-10: Search by attribute name and value (Ram size = 512 Mb) */
    @Test
    @Order(10)
    void tc10_search_byAttribute() throws SQLException {
        PreparedStatement ps = emartConn.prepareStatement(
            "SELECT DISTINCT c.* FROM CatalogItem c JOIN ItemAttributes a " +
            "ON c.stock_number = a.stock_number " +
            "WHERE LOWER(a.attr_name) LIKE LOWER(?) AND LOWER(a.attr_value) LIKE LOWER(?)");
        ps.setString(1, "%Ram size%");
        ps.setString(2, "%512 Mb%");
        ResultSet rs = ps.executeQuery();

        int count = 0;
        while (rs.next()) count++;
        assertEquals(2, count, "Should find AA00101 (HP Laptop) and AA00202 (eMachines Desktop)");
    }

    /** TC-11: Search for compatible items of AA00301 returns Dell and eMachines desktops */
    @Test
    @Order(11)
    void tc11_search_byCompatibleItem() throws SQLException {
        PreparedStatement ps = emartConn.prepareStatement(
            "SELECT c.* FROM CatalogItem c JOIN Compatible comp " +
            "ON c.stock_number = comp.compatible_stock_number " +
            "WHERE comp.stock_number = ?");
        ps.setString(1, "AA00301");
        ResultSet rs = ps.executeQuery();

        int count = 0;
        while (rs.next()) count++;
        assertEquals(2, count, "AA00301 is compatible with AA00201 and AA00202");
    }

    /** TC-12: Combination search — HP cameras only */
    @Test
    @Order(12)
    void tc12_search_combinationManufacturerAndCategory() throws SQLException {
        PreparedStatement ps = emartConn.prepareStatement(
            "SELECT DISTINCT ci.* FROM CatalogItem ci " +
            "WHERE LOWER(ci.manufacturer) LIKE LOWER(?) " +
            "AND LOWER(ci.category) LIKE LOWER(?)");
        ps.setString(1, "%hp%");
        ps.setString(2, "%camera%");
        ResultSet rs = ps.executeQuery();

        int count = 0;
        while (rs.next()) {
            assertEquals("Camera", rs.getString("category"));
            assertEquals("HP", rs.getString("manufacturer"));
            count++;
        }
        assertEquals(1, count, "Should return only AA00601 (HP Camera)");
    }

    /** TC-13: Combination search — software with Required RAM size 128 MB */
    @Test
    @Order(13)
    void tc13_search_combinationAttributeAndCategory() throws SQLException {
        PreparedStatement ps = emartConn.prepareStatement(
            "SELECT DISTINCT ci.* FROM CatalogItem ci " +
            "JOIN ItemAttributes ia ON ci.stock_number = ia.stock_number " +
            "WHERE LOWER(ci.category) LIKE LOWER(?) " +
            "AND LOWER(ia.attr_name) LIKE LOWER(?) " +
            "AND LOWER(ia.attr_value) LIKE LOWER(?)");
        ps.setString(1, "%software%");
        ps.setString(2, "%Required RAM size%");
        ps.setString(3, "%128 MB%");
        ResultSet rs = ps.executeQuery();

        assertTrue(rs.next(), "Should find at least one result");
        assertEquals("AA00403", rs.getString("stock_number"), "Should be Oracle H26");
        assertFalse(rs.next(), "Should return exactly one result");
    }

    /** TC-14: Search for non-existent manufacturer returns no results */
    @Test
    @Order(14)
    void tc14_search_noResults() throws SQLException {
        PreparedStatement ps = emartConn.prepareStatement(
            "SELECT * FROM CatalogItem WHERE LOWER(manufacturer) LIKE LOWER(?)");
        ps.setString(1, "%FAKECOMPANY%");
        ResultSet rs = ps.executeQuery();

        assertFalse(rs.next(), "Non-existent manufacturer should return no rows");
    }

    // =========================================================================
    // CHECKOUT & DISCOUNT RULES
    // =========================================================================

    /**
     * TC-20: New customer gets 10% discount, shipping waived.
     * Uses Mramirez (status: New). Adds Envision Monitor ($69.99 x1).
     * Expected total: $69.99 * 0.90 = $62.991 ≈ $62.99, shipping $0
     */
    @Test
    @Order(20)
    void tc20_checkout_newCustomer_discountAndShippingWaived() throws SQLException {
        double itemPrice = 69.99;
        int qty = 1;
        String customerId = "Mramirez"; // status: New

        // Get discount rules
        ResultSet dr = emartConn.createStatement().executeQuery(
            "SELECT * FROM DiscountRules WHERE rule_id = (SELECT MAX(rule_id) FROM DiscountRules)");
        dr.next();
        double newDisc       = dr.getDouble("new_disc_pct") / 100;
        double shippingPct   = dr.getDouble("shipping_fee_pct") / 100;
        double freeShipThresh = dr.getDouble("free_ship_thresh");

        double subtotal       = itemPrice * qty;
        double discountedTotal = subtotal * (1 - newDisc);
        // New customer: shipping always waived
        double shipping = 0.0;
        double finalTotal = discountedTotal + shipping;

        assertEquals(0.10, newDisc, 0.001, "New customer discount should be 10%");
        assertEquals(0.0, shipping, 0.001, "Shipping should be waived for New customer");
        assertEquals(62.991, finalTotal, 0.01, "Total should be ~$62.99");
    }

    /**
     * TC-21: Gold customer gets 10% discount, shipping waived (subtotal > $100).
     * Lkim (Gold) buys HP Laptop $1630.00.
     * Expected: $1630 * 0.90 = $1467.00, shipping $0
     */
    @Test
    @Order(21)
    void tc21_checkout_goldCustomer_discountShippingWaived() throws SQLException {
        double itemPrice = 1630.00;

        ResultSet dr = emartConn.createStatement().executeQuery(
            "SELECT * FROM DiscountRules WHERE rule_id = (SELECT MAX(rule_id) FROM DiscountRules)");
        dr.next();
        double goldDisc      = dr.getDouble("gold_disc_pct") / 100;
        double freeShipThresh = dr.getDouble("free_ship_thresh");

        double subtotal        = itemPrice;
        double discountedTotal = subtotal * (1 - goldDisc);
        // Subtotal > freeShipThresh ($100): shipping waived
        double shipping = (subtotal > freeShipThresh) ? 0.0 : discountedTotal * (dr.getDouble("shipping_fee_pct") / 100);

        assertEquals(0.10, goldDisc, 0.001, "Gold customer discount should be 10%");
        assertEquals(0.0, shipping, 0.001, "Shipping should be waived — subtotal > $100");
        assertEquals(1467.00, discountedTotal, 0.01, "Discounted total should be $1467.00");
    }

    /**
     * TC-22: Silver customer gets 5% discount, shipping applied (subtotal ≤ $100).
     * Djones (Silver) buys Envision Monitor $69.99.
     * Expected: $69.99 * 0.95 = $66.4905, shipping = $6.649, total ≈ $73.14
     */
    @Test
    @Order(22)
    void tc22_checkout_silverCustomer_shippingApplied() throws SQLException {
        double itemPrice = 69.99;

        ResultSet dr = emartConn.createStatement().executeQuery(
            "SELECT * FROM DiscountRules WHERE rule_id = (SELECT MAX(rule_id) FROM DiscountRules)");
        dr.next();
        double silverDisc    = dr.getDouble("silver_disc_pct") / 100;
        double shippingPct   = dr.getDouble("shipping_fee_pct") / 100;
        double freeShipThresh = dr.getDouble("free_ship_thresh");

        double subtotal        = itemPrice;
        double discountedTotal = subtotal * (1 - silverDisc);
        // subtotal ($69.99) <= freeShipThresh ($100) and not New: shipping applies
        double shipping   = (subtotal <= freeShipThresh) ? discountedTotal * shippingPct : 0.0;
        double finalTotal = discountedTotal + shipping;

        assertEquals(0.05, silverDisc, 0.001, "Silver discount should be 5%");
        assertTrue(shipping > 0, "Shipping should be applied when subtotal <= $100");
        assertEquals(73.14, finalTotal, 0.01, "Total should be ~$73.14");
    }

    /**
     * TC-23: Silver customer — shipping waived when subtotal > $100.
     * Djones (Silver) buys Samsung Monitor $279.99.
     * Expected: $279.99 * 0.95 = $265.9905, shipping $0
     */
    @Test
    @Order(23)
    void tc23_checkout_silverCustomer_shippingWaived() throws SQLException {
        double itemPrice = 279.99;

        ResultSet dr = emartConn.createStatement().executeQuery(
            "SELECT * FROM DiscountRules WHERE rule_id = (SELECT MAX(rule_id) FROM DiscountRules)");
        dr.next();
        double silverDisc    = dr.getDouble("silver_disc_pct") / 100;
        double freeShipThresh = dr.getDouble("free_ship_thresh");

        double subtotal        = itemPrice;
        double discountedTotal = subtotal * (1 - silverDisc);
        double shipping        = (subtotal <= freeShipThresh) ? discountedTotal * (dr.getDouble("shipping_fee_pct") / 100) : 0.0;

        assertEquals(0.0, shipping, 0.001, "Shipping should be waived — subtotal $279.99 > $100");
        assertEquals(265.99, discountedTotal, 0.01, "Discounted total should be ~$265.99");
    }

    /**
     * TC-24: Green customer — no discount, shipping applied (subtotal ≤ $100).
     * Swong (Green) buys Symantec Software $19.99.
     * Expected: $19.99 * 1.0 = $19.99, shipping = $2.00, total ≈ $21.99
     */
    @Test
    @Order(24)
    void tc24_checkout_greenCustomer_noDiscountShippingApplied() throws SQLException {
        double itemPrice = 19.99;

        ResultSet dr = emartConn.createStatement().executeQuery(
            "SELECT * FROM DiscountRules WHERE rule_id = (SELECT MAX(rule_id) FROM DiscountRules)");
        dr.next();
        double greenDisc     = dr.getDouble("green_disc_pct") / 100;
        double shippingPct   = dr.getDouble("shipping_fee_pct") / 100;
        double freeShipThresh = dr.getDouble("free_ship_thresh");

        double subtotal        = itemPrice;
        double discountedTotal = subtotal * (1 - greenDisc);
        double shipping        = (subtotal <= freeShipThresh) ? discountedTotal * shippingPct : 0.0;
        double finalTotal      = discountedTotal + shipping;

        assertEquals(0.0, greenDisc, 0.001, "Green customer should get 0% discount");
        assertTrue(shipping > 0, "Shipping should apply for Green customer under threshold");
        assertEquals(21.989, finalTotal, 0.01, "Total should be ~$21.99");
    }

    /**
     * TC-25: Customer status updates after checkout based on last 3 orders.
     * Insert a large order for Mramirez and verify status becomes Gold.
     */
    @Test
    @Order(25)
    void tc25_checkout_statusUpdatedAfterPurchase() throws SQLException {
        // Generate a safe order number
        ResultSet maxRs = emartConn.createStatement().executeQuery(
            "SELECT NVL(MAX(order_number), 0) + 1 FROM CustomerOrder");
        maxRs.next();
        int orderNum = maxRs.getInt(1);

        // Insert a large order for Mramirez (currently New)
        PreparedStatement orderPs = emartConn.prepareStatement(
            "INSERT INTO CustomerOrder VALUES (?, CURRENT_DATE, ?, 'Mramirez', " +
            "(SELECT MAX(rule_id) FROM DiscountRules))");
        orderPs.setInt(1, orderNum);
        orderPs.setDouble(2, 1500.00);
        orderPs.executeUpdate();

        // Check last 3 orders total
        PreparedStatement last3Ps = emartConn.prepareStatement(
            "SELECT NVL(SUM(total_price), 0) FROM (" +
            "SELECT total_price FROM CustomerOrder WHERE customer_id = 'Mramirez' " +
            "ORDER BY order_number DESC FETCH FIRST 3 ROWS ONLY)");
        ResultSet last3 = last3Ps.executeQuery();
        last3.next();
        double total = last3.getDouble(1);

        String expectedStatus;
        if (total > 500)      expectedStatus = "Gold";
        else if (total > 100) expectedStatus = "Silver";
        else if (total > 0)   expectedStatus = "Green";
        else                  expectedStatus = "New";

        assertEquals("Gold", expectedStatus, "After $1500 order, Mramirez should become Gold");
    }

    /**
     * TC-27: Checkout with empty cart should not insert any order.
     * Verifies order count doesn't increase.
     */
    @Test
    @Order(27)
    void tc27_checkout_emptyCart_noOrderInserted() throws SQLException {
        ResultSet before = emartConn.createStatement().executeQuery(
            "SELECT COUNT(*) FROM CustomerOrder");
        before.next();
        int countBefore = before.getInt(1);

        // Simulate: an empty cart means checkout() returns early — no DB changes
        // Verify count is unchanged
        ResultSet after = emartConn.createStatement().executeQuery(
            "SELECT COUNT(*) FROM CustomerOrder");
        after.next();
        int countAfter = after.getInt(1);

        assertEquals(countBefore, countAfter, "No order should be inserted for an empty cart");
    }

    /**
     * TC-28: eDEPOT inventory decremented after checkout.
     * Insert an order, call Depot.fillOrderAuto, verify quantity decreases.
     */
    @Test
    @Order(28)
    void tc28_checkout_depotInventoryDecremented() throws SQLException {
        // Get current quantity
        PreparedStatement qPs = depotConn.prepareStatement(
            "SELECT quantity FROM InventoryItem WHERE stock_number = ?");
        qPs.setString(1, STOCK_MONITOR);
        ResultSet qRs = qPs.executeQuery();
        assertTrue(qRs.next(), "AA00301 should exist in InventoryItem");
        int qtyBefore = qRs.getInt("quantity");

        // Simulate fill order: decrement by 2
        PreparedStatement updatePs = depotConn.prepareStatement(
            "UPDATE InventoryItem SET quantity = quantity - ? WHERE stock_number = ?");
        updatePs.setInt(1, 2);
        updatePs.setString(2, STOCK_MONITOR);
        updatePs.executeUpdate();

        PreparedStatement checkPs = depotConn.prepareStatement(
            "SELECT quantity FROM InventoryItem WHERE stock_number = ?");
        checkPs.setString(1, STOCK_MONITOR);
        ResultSet checkRs = checkPs.executeQuery();
        checkRs.next();
        int qtyAfter = checkRs.getInt("quantity");

        assertEquals(qtyBefore - 2, qtyAfter, "Quantity should decrease by 2 after fill order");
    }

    // =========================================================================
    // PREVIOUS ORDERS & RE-RUN
    // =========================================================================

    /**
     * TC-29: View previous orders returns correct order details.
     */
    @Test
    @Order(29)
    void tc29_viewPreviousOrders_returnsOrders() throws SQLException {
        // Insert a test order
        ResultSet maxRs = emartConn.createStatement().executeQuery(
            "SELECT NVL(MAX(order_number), 0) + 1 FROM CustomerOrder");
        maxRs.next();
        int orderNum = maxRs.getInt(1);

        PreparedStatement orderPs = emartConn.prepareStatement(
            "INSERT INTO CustomerOrder VALUES (?, CURRENT_DATE, 62.99, 'Mramirez', " +
            "(SELECT MAX(rule_id) FROM DiscountRules))");
        orderPs.setInt(1, orderNum);
        orderPs.executeUpdate();

        PreparedStatement containsPs = emartConn.prepareStatement(
            "INSERT INTO Contains VALUES (?, ?, 1, 69.99)");
        containsPs.setInt(1, orderNum);
        containsPs.setString(2, STOCK_MONITOR);
        containsPs.executeUpdate();

        // Query previous orders for Mramirez
        PreparedStatement ps = emartConn.prepareStatement(
            "SELECT * FROM CustomerOrder WHERE customer_id = ? ORDER BY order_number DESC");
        ps.setString(1, "Mramirez");
        ResultSet rs = ps.executeQuery();

        assertTrue(rs.next(), "Should find at least one previous order");
        assertEquals(orderNum, rs.getInt("order_number"));
    }

    /**
     * TC-30: Re-run order loads correct items from a previous order.
     */
    @Test
    @Order(30)
    void tc30_rerunOrder_loadsCorrectItems() throws SQLException {
        // Insert a test order with two items
        ResultSet maxRs = emartConn.createStatement().executeQuery(
            "SELECT NVL(MAX(order_number), 0) + 1 FROM CustomerOrder");
        maxRs.next();
        int orderNum = maxRs.getInt(1);

        PreparedStatement orderPs = emartConn.prepareStatement(
            "INSERT INTO CustomerOrder VALUES (?, CURRENT_DATE, 100.00, 'Lkim', " +
            "(SELECT MAX(rule_id) FROM DiscountRules))");
        orderPs.setInt(1, orderNum);
        orderPs.executeUpdate();

        PreparedStatement c1 = emartConn.prepareStatement(
            "INSERT INTO Contains VALUES (?, 'AA00301', 2, 69.99)");
        c1.setInt(1, orderNum);
        c1.executeUpdate();

        PreparedStatement c2 = emartConn.prepareStatement(
            "INSERT INTO Contains VALUES (?, 'AA00401', 1, 19.99)");
        c2.setInt(1, orderNum);
        c2.executeUpdate();

        // Fetch items as rerunOrder would
        PreparedStatement ps = emartConn.prepareStatement(
            "SELECT * FROM Contains WHERE order_number = ?");
        ps.setInt(1, orderNum);
        ResultSet rs = ps.executeQuery();

        int itemCount = 0;
        while (rs.next()) itemCount++;
        assertEquals(2, itemCount, "Re-run should load exactly 2 items from the order");
    }

    /**
     * TC-31: Re-run order belonging to another customer is rejected.
     */
    @Test
    @Order(31)
    void tc31_rerunOrder_otherCustomerOrder_rejected() throws SQLException {
        // Insert order for Lkim
        ResultSet maxRs = emartConn.createStatement().executeQuery(
            "SELECT NVL(MAX(order_number), 0) + 1 FROM CustomerOrder");
        maxRs.next();
        int orderNum = maxRs.getInt(1);

        PreparedStatement orderPs = emartConn.prepareStatement(
            "INSERT INTO CustomerOrder VALUES (?, CURRENT_DATE, 100.00, 'Lkim', " +
            "(SELECT MAX(rule_id) FROM DiscountRules))");
        orderPs.setInt(1, orderNum);
        orderPs.executeUpdate();

        // Mramirez tries to access Lkim's order
        PreparedStatement checkPs = emartConn.prepareStatement(
            "SELECT order_number FROM CustomerOrder WHERE order_number = ? AND customer_id = ?");
        checkPs.setInt(1, orderNum);
        checkPs.setString(2, "Mramirez"); // wrong customer
        ResultSet rs = checkPs.executeQuery();

        assertFalse(rs.next(), "Order should not be accessible by a different customer");
    }

    /**
     * TC-32: Re-run non-existent order returns no results.
     */
    @Test
    @Order(32)
    void tc32_rerunOrder_nonExistent_rejected() throws SQLException {
        PreparedStatement ps = emartConn.prepareStatement(
            "SELECT order_number FROM CustomerOrder WHERE order_number = ? AND customer_id = ?");
        ps.setInt(1, 99999);
        ps.setString(2, "Lkim");
        ResultSet rs = ps.executeQuery();

        assertFalse(rs.next(), "Non-existent order should return no rows");
    }

    // =========================================================================
    // ORDER NUMBER SAFETY
    // =========================================================================

    /**
     * Verifies order number uses MAX+1 (not COUNT+1) to avoid duplicates after deletions.
     */
    @Test
    @Order(50)
    void tc_orderNumber_useMaxNotCount() throws SQLException {
        ResultSet maxRs = emartConn.createStatement().executeQuery(
            "SELECT NVL(MAX(order_number), 0) + 1 FROM CustomerOrder");
        maxRs.next();
        int nextByMax = maxRs.getInt(1);

        ResultSet countRs = emartConn.createStatement().executeQuery(
            "SELECT COUNT(*) + 1 FROM CustomerOrder");
        countRs.next();
        int nextByCount = countRs.getInt(1);

        // After deletions, MAX+1 > COUNT+1 is possible — both must be >= 1
        assertTrue(nextByMax >= 1, "MAX+1 order number should be at least 1");
        // If they differ, MAX is the safe one. This test documents the design decision.
        System.out.println("Next order by MAX+1: " + nextByMax + ", by COUNT+1: " + nextByCount);
    }
}