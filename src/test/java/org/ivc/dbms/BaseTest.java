package org.ivc.dbms;
 
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
 
import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
 
/**
 * Base class for all eMART/eDEPOT tests.
 * Sets up emartConn and depotConn once for the entire test suite,
 * mirroring the connection logic in Main.java.
 */
public abstract class BaseTest {
 
    protected static Connection emartConn;
    protected static Connection depotConn;
 
    @BeforeAll
    static void setupConnections() throws Exception {
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
 
        // Wire into Main so existing methods can use them if needed
        Main.emartConn = emartConn;
        Main.depotConn = depotConn;
 
        System.out.println("Test DB connections established.");
    }
 
    @AfterAll
    static void closeConnections() throws SQLException {
        if (emartConn != null && !emartConn.isClosed()) emartConn.close();
        if (depotConn != null && !depotConn.isClosed()) depotConn.close();
        System.out.println("Test DB connections closed.");
    }
 
    /** Helper: rolls back both connections safely — call in @AfterEach */
    protected void rollbackAll() {
        try { emartConn.rollback(); } catch (SQLException e) { e.printStackTrace(); }
        try { depotConn.rollback(); } catch (SQLException e) { e.printStackTrace(); }
    }
}
 